package com.zufe.procurement.m9.query.service;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m7.assistant.port.AiDataQueryPort;
import com.zufe.procurement.m7.assistant.support.RouteLexicon;
import com.zufe.procurement.m7.assistant.vo.AiContext;
import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m7.assistant.vo.DataAnswer;
import com.zufe.procurement.m9.query.entity.AiIntent;
import com.zufe.procurement.m9.query.entity.AiIntentParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 受控查询流水线（M9 全流程）：意图 → 实体归一 → 参数提取 → 白名单校验 → 执行 → 转述。
 *
 * 每一步都可能中断并返回非 Success 结果码，且中断原因都以澄清选项或说明文字返回，
 * 不抛异常黑箱：用户看到的是「系统为什么不能查」，而不是一句参数错误。
 */
@Service
public class ControlledQueryPipeline implements AiDataQueryPort {

    private final AiIntentCatalog catalog;
    private final IntentClassifier classifier;
    private final ParamExtractor extractor;
    private final ParamValidator validator;
    private final EntityNormalizer normalizer;
    private final DataQueryExecutor executor;
    private final LlmIntentAdvisor intentAdvisor;

    public ControlledQueryPipeline(AiIntentCatalog catalog, IntentClassifier classifier,
                                   ParamExtractor extractor, ParamValidator validator,
                                   EntityNormalizer normalizer, DataQueryExecutor executor,
                                   LlmIntentAdvisor intentAdvisor) {
        this.catalog = catalog;
        this.classifier = classifier;
        this.extractor = extractor;
        this.validator = validator;
        this.normalizer = normalizer;
        this.executor = executor;
        this.intentAdvisor = intentAdvisor;
    }

    @Override
    public DataAnswer query(String question, String overrideIntentCode, Map<String, Object> overrideParams,
                            Long confirmedEntityId, AiContext context) {
        DataAnswer answer = new DataAnswer();
        List<EntityNormalizer.Group> groups = normalizer.detect(question);

        // ---------- 1 意图 M9-1-1 / M9-1-2 ----------
        AiIntent intent;
        if (overrideIntentCode != null && !overrideIntentCode.isBlank()) {
            intent = catalog.byCode(overrideIntentCode);
            if (intent == null) {
                answer.setResultCode(ResultCode.INVALID_PARAMETER.getCode());
                answer.setSummary("指定的意图编码 " + overrideIntentCode + " 不在启用白名单内。");
                return answer;
            }
            answer.setConfidence(BigDecimal.ONE);
        } else {
            double threshold = catalog.confidenceThreshold();
            // 优先问模型：它只给建议，编码合法性、权限、参数仍由后续四道闸决定
            LlmIntentAdvisor.Suggestion suggestion = intentAdvisor.enabled()
                    ? intentAdvisor.suggest(question, catalog.enabledIntents(), context) : null;
            if (suggestion != null && suggestion.isNone()) {
                // 模型说「不属于任何一类」只作参考，不作决定。
                // 直接据此判 Unsupported 会把规则本来答得上的问题砍掉
                // （实测「台州的供应商」「订单金额大于 999999999999 的有哪些」都被这么砍过），
                // 所以这里回退词表复核，词表也认不出才判不支持。
                answer.getNotices().add("大模型判定不属于任何一类受控查询，已回退词表复核："
                        + trimReason(suggestion.reason()));
                suggestion = null;
            }
            AiIntent fromModel = suggestion == null ? null : catalog.byCode(suggestion.intentCode());
            if (fromModel != null && suggestion.confidence() >= threshold) {
                intent = fromModel;
                answer.setConfidence(BigDecimal.valueOf(suggestion.confidence())
                        .setScale(4, java.math.RoundingMode.HALF_UP));
                answer.getNotices().add("意图由大模型判定（" + intent.getIntentName()
                        + "，自评置信度 " + percent(answer.getConfidence()) + "%），已按白名单校验");
                answer.setGenerator("llm");
            } else {
                if (suggestion != null) {
                    answer.getNotices().add("大模型意图置信度低于阈值或编码不可用，已回退词表分类："
                            + (suggestion.reason().isBlank() ? "未给出理由" : trimReason(suggestion.reason())));
                }
                IntentClassifier.IntentMatch match = classifier.classify(question, groups);
                if (match.getIntent() == null) {
                    answer.setResultCode(ResultCode.UNSUPPORTED.getCode());
                    answer.setSummary("这个问题不属于当前支持的订单、交付、价格、库存、供应商交付五类查询。"
                            + "可以换个说法，或到对应业务页面查看。");
                    return answer;
                }
                if (match.getConfidence().doubleValue() < threshold) {
                    // 没有可澄清的实体时，反问「您想查哪一类」是空转：
                    // 用户说了半句，系统连他指哪个对象都不知道，给不出选项只会让人更困惑。
                    // 典型例子是知识型问句（「采购合同的违约金比例是多少」）被弱匹配到库存意图。
                    if (groups == null || groups.isEmpty()) {
                        answer.setResultCode(ResultCode.UNSUPPORTED.getCode());
                        answer.setSummary("这个问题不属于当前支持的订单、交付、价格、库存、供应商交付五类查询。"
                                + "可以换个说法，或到对应业务页面查看。");
                        return answer;
                    }
                    return ambiguousIntent(answer, match, threshold);
                }
                intent = match.getIntent();
                answer.setConfidence(match.getConfidence());
                answer.setGenerator("rule");
            }
        }
        answer.setIntentCode(intent.getIntentCode());
        answer.setIntentName(intent.getIntentName());
        answer.setServiceCode(intent.getServiceCode());

        // 越权统一拒绝（M7-2-3）：意图对应模块的查看权限点缺失就直接拒，不执行查询、不返回行数据
        List<String> required = permissionsOf(intent.getIntentCode());
        if (!required.isEmpty() && !anyPermission(required)) {
            answer.setResultCode(ResultCode.NO_PERMISSION.getCode());
            answer.setSummary("您的角色没有该类数据的查看权限（需要 " + String.join(" 或 ", required)
                    + "），已拒绝执行。如需查询请联系管理员授权。");
            return answer;
        }

        List<AiIntentParam> specs = catalog.paramsOf(intent.getId());

        // ---------- 2 实体归一与消歧 M9-3-1 / M9-3-2 / M9-3-3 ----------
        List<ChatVO.EntityRef> entities = new ArrayList<>();
        for (EntityNormalizer.Group group : groups) {
            ChatVO.EntityRef chosen = null;
            if (group.getCandidates().size() == 1) {
                chosen = group.getCandidates().get(0);
            } else if (confirmedEntityId != null) {
                chosen = group.getCandidates().stream()
                        .filter(item -> confirmedEntityId.equals(item.getId()))
                        .findFirst().orElse(null);
            }
            if (chosen == null && group.getCandidates().size() > 1) {
                return ambiguousEntity(answer, group, intent);
            }
            if (chosen != null) {
                entities.add(chosen);
            }
        }
        // 指代继承：问句没有实体且用「它 / 这个 / 该」时，沿用上一轮实体（M7-1-3）
        if (entities.isEmpty() && context != null && context.getEntities() != null
                && containsPronoun(question)) {
            entities.addAll(context.getEntities());
            answer.getNotices().add("问句未指明对象，已沿用上一轮的实体：" + describeEntities(context.getEntities()));
        }
        answer.setEntities(entities);

        // ---------- 3 参数提取与合并 M9-2-1 / M9-2-2 / M9-2-3 ----------
        ParamExtractor.Extraction extraction = extractor.extract(question, specs);
        answer.setTimeRange(extraction.getTimeRange());
        List<ChatVO.ParamItem> rejected = new ArrayList<>(extraction.getRejected());

        Map<String, ParamValidator.RawParam> raws = new LinkedHashMap<>();
        for (ChatVO.EntityRef entity : entities) {
            String code = paramCodeOf(entity.getType());
            if (code == null) {
                continue;
            }
            boolean supported = specs.stream().anyMatch(spec -> code.equals(spec.getParamCode()));
            if (!supported) {
                ChatVO.ParamItem item = new ChatVO.ParamItem();
                item.setCode(code);
                item.setName(entity.getTypeName());
                item.setValue(entity.getName());
                item.setSource("entity");
                item.setReason("当前意图不支持按" + entity.getTypeName() + "过滤");
                rejected.add(item);
                continue;
            }
            raws.put(code, new ParamValidator.RawParam(code, entity.getId(), "entity", entity.getName()));
        }
        for (Map.Entry<String, Object> entry : extraction.getValues().entrySet()) {
            raws.put(entry.getKey(), new ParamValidator.RawParam(entry.getKey(), entry.getValue(),
                    "question", extraction.getLabels().get(entry.getKey())));
        }
        if (context != null && context.getParams() != null) {
            for (Map.Entry<String, Object> entry : context.getParams().entrySet()) {
                raws.putIfAbsent(entry.getKey(), new ParamValidator.RawParam(entry.getKey(),
                        entry.getValue(), "context", null));
            }
        }
        if (overrideParams != null) {
            for (Map.Entry<String, Object> entry : overrideParams.entrySet()) {
                raws.put(entry.getKey(), new ParamValidator.RawParam(entry.getKey(), entry.getValue(),
                        "override", null));
            }
            correctEntitiesByOverride(entities, overrideParams, answer);
        }

        // ---------- 4 白名单校验 M9-2-4 / M9-2-5 ----------
        ParamValidator.Validation validation = validator.validate(specs, new ArrayList<>(raws.values()));
        rejected.addAll(validation.getRejected());
        answer.setFilters(validation.getFilters());
        answer.setRejected(rejected);

        if (!rejected.isEmpty()) {
            answer.setResultCode(ResultCode.INVALID_PARAMETER.getCode());
            StringBuilder sb = new StringBuilder("以下条件不在受控白名单内，已拒绝执行：");
            for (ChatVO.ParamItem item : rejected) {
                sb.append(item.getName()).append("=").append(item.getValue())
                        .append("（").append(item.getReason()).append("）；");
            }
            sb.append("请去掉这些条件或换一个问法。系统不会忽略条件后再回答，以免给出看似完整的错误结论。");
            answer.setSummary(sb.toString());
            answer.setClarifyType("param");
            answer.setClarifyQuestion("要按去掉被拒条件后的范围重新查询吗？");
            answer.setClarifyOptions(List.of(option("去掉被拒条件重查", "只保留白名单内的条件",
                    intent.getIntentCode(), validation.getAccepted())));
            return answer;
        }
        if (!validation.getMissing().isEmpty()) {
            answer.setResultCode(ResultCode.AMBIGUOUS.getCode());
            ChatVO.ParamItem missing = validation.getMissing().get(0);
            answer.setMissing(validation.getMissing());
            answer.setSummary("这类问题需要先知道" + missing.getName() + "，请补充后再查。");
            answer.setClarifyType("param");
            answer.setClarifyQuestion("请补充" + missing.getName());
            answer.setClarifyOptions(List.of());
            return answer;
        }

        // ---------- 5 执行与转述 M9-4 / M9-5 ----------
        DataQueryExecutor.Outcome outcome = executor.execute(intent.getIntentCode(), validation.getAccepted());
        answer.setTable(outcome.getTable());
        answer.setSummary(outcome.getSummary());
        answer.setRowCount(outcome.getRowCount());
        answer.setResultCode(outcome.getResultCode());
        answer.setScopeSnapshot(outcome.getScopeSnapshot());
        if (outcome.getNotices() != null) {
            answer.getNotices().addAll(outcome.getNotices());
        }
        return answer;
    }

    private DataAnswer ambiguousIntent(DataAnswer answer, IntentClassifier.IntentMatch match, double threshold) {
        // 列不出候选就等于空转：反问「您想查哪一类」却一个选项都给不出来，对用户毫无信息量。
        // 这种情况说明词表也只是弱匹配（如「采购合同的违约金比例是多少」匹配到 53%），
        // 直接判不支持比硬凑一个反问更诚实。
        if (match.getCandidates() == null || match.getCandidates().isEmpty()) {
            answer.setResultCode(ResultCode.UNSUPPORTED.getCode());
            answer.setSummary("这个问题不属于当前支持的订单、交付、价格、库存、供应商交付五类查询。"
                    + "可以换个说法，或到对应业务页面查看。");
            return answer;
        }
        answer.setResultCode(ResultCode.AMBIGUOUS.getCode());
        answer.setIntentCode(match.getIntent() == null ? null : match.getIntent().getIntentCode());
        answer.setIntentName(match.getIntent() == null ? null : match.getIntent().getIntentName());
        answer.setConfidence(match.getConfidence());
        answer.setSummary("这个问题可以对应多类查询，置信度 "
                + percent(match.getConfidence()) + "% 低于阈值 " + percent(BigDecimal.valueOf(threshold))
                + "%，请选择要查的方向。");
        answer.setClarifyType("intent");
        answer.setClarifyQuestion("您想查哪一类？");
        List<ChatVO.Option> options = new ArrayList<>();
        for (IntentClassifier.Candidate candidate : match.getCandidates()) {
            if (options.size() >= 3) {
                break;
            }
            options.add(option(candidate.getIntentName(),
                    "置信度 " + percent(candidate.getConfidence()) + "%",
                    candidate.getIntentCode(), Map.of()));
        }
        answer.setClarifyOptions(options);
        return answer;
    }

    private DataAnswer ambiguousEntity(DataAnswer answer, EntityNormalizer.Group group, AiIntent intent) {
        answer.setResultCode(ResultCode.AMBIGUOUS.getCode());
        answer.setIntentCode(intent.getIntentCode());
        answer.setIntentName(intent.getIntentName());
        answer.setSummary("「" + group.getMention() + "」匹配到 " + group.getCandidates().size()
                + " 个" + group.getCandidates().get(0).getTypeName() + "档案，请确认是哪一条。");
        answer.setClarifyType("entity");
        answer.setClarifyQuestion("请选择具体的" + group.getCandidates().get(0).getTypeName());
        List<ChatVO.Option> options = new ArrayList<>();
        for (ChatVO.EntityRef candidate : group.getCandidates()) {
            options.add(option(candidate.getName() + "（" + candidate.getNo() + "）",
                    "编码 " + candidate.getNo(), intent.getIntentCode(),
                    Map.of(paramCodeOf(candidate.getType()), candidate.getId())));
        }
        answer.setClarifyOptions(options);
        answer.setEntities(group.getCandidates());
        return answer;
    }

    private ChatVO.Option option(String label, String detail, String intentCode, Map<String, Object> params) {
        ChatVO.Option option = new ChatVO.Option();
        option.setLabel(label);
        option.setDetail(detail);
        option.setIntentCode(intentCode);
        if (params != null && !params.isEmpty()) {
            option.setParams(new LinkedHashMap<>(params));
        }
        return option;
    }

    private boolean containsPronoun(String question) {
        if (question == null) {
            return false;
        }
        for (String pronoun : RouteLexicon.PRONOUNS) {
            if (question.contains(pronoun)) {
                return true;
            }
        }
        return false;
    }

    private String describeEntities(List<ChatVO.EntityRef> entities) {
        StringBuilder sb = new StringBuilder();
        for (ChatVO.EntityRef entity : entities) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(entity.getTypeName()).append(" ").append(entity.getName());
        }
        return sb.toString();
    }

    /** 实体类型到参数编码的固定映射，与 ai_intent_param 的 param_code 对齐 */
    private String paramCodeOf(Integer entityType) {
        if (entityType == null) {
            return null;
        }
        return switch (entityType) {
            case 1 -> "material_id";
            case 2 -> "supplier_id";
            case 3 -> "warehouse_id";
            default -> null;
        };
    }

    private static final List<String> ENTITY_PARAM_CODES = List.of("material_id", "supplier_id", "warehouse_id");

    /**
     * 意图到模块查看权限点的映射（M7-2-3）。
     * 只有价格类需要额外门控：价格数据是 M6 的敏感口径，仓管员不持任何 m6 权限点，
     * 而库存、订单、供应商交付的列表权限点仓管员本来就有，与页面口径一致。
     */
    private static final Map<String, List<String>> INTENT_PERMISSIONS = Map.of(
            "purchase_price_history", List.of("m6:stat:view", "m6:price:view"));

    private List<String> permissionsOf(String intentCode) {
        return INTENT_PERMISSIONS.getOrDefault(intentCode, List.of());
    }

    private boolean anyPermission(List<String> codes) {
        CurrentUser user = AuthContext.get();
        if (user == null) {
            return false;
        }
        return codes.stream().anyMatch(user::hasPermission);
    }

    private Integer typeOfParam(String paramCode) {
        return switch (paramCode) {
            case "material_id" -> 1;
            case "supplier_id" -> 2;
            case "warehouse_id" -> 3;
            default -> null;
        };
    }

    /**
     * 参数修正重跑（M9-2-2 / M7-1-3）：override 命中的是实体参数时同步校正实体展示。
     * 只改展示口径，不改白名单校验——校验仍走 ParamValidator，避免绕过四道闸。
     */
    private void correctEntitiesByOverride(List<ChatVO.EntityRef> entities, Map<String, Object> overrideParams,
                                           DataAnswer answer) {
        boolean corrected = false;
        for (String code : ENTITY_PARAM_CODES) {
            if (!overrideParams.containsKey(code)) {
                continue;
            }
            ChatVO.EntityRef ref = normalizer.refById(typeOfParam(code), asLong(overrideParams.get(code)));
            if (ref == null) {
                continue;
            }
            entities.removeIf(entity -> java.util.Objects.equals(entity.getType(), ref.getType()));
            entities.add(ref);
            answer.getNotices().add("已按修正后的条件执行，本次对象为" + ref.getTypeName() + " " + ref.getName() + "。");
            corrected = true;
        }
        if (corrected) {
            answer.setEntities(entities);
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 置信度百分比保留一位小数：取整会出现「59.57% 显示 60% 却低于 60% 阈值」的自相矛盾 */
    private String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "0.0";
        }
        return ratio.multiply(BigDecimal.valueOf(100))
                .setScale(1, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String trimReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "未给出理由";
        }
        String flat = reason.replace('\n', ' ').trim();
        return flat.length() <= 30 ? flat : flat.substring(0, 30) + "…";
    }
}
