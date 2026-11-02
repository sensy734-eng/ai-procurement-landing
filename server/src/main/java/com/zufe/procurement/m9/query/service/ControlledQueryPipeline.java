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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 受控查询流水线（M9 全流程）：理解 → 实体归一 → 参数提取 → 白名单校验 → 执行 → 转述。
 *
 * 两档处理（这是本类的核心结构）：
 *
 * **模型档**：{@link LlmQueryPlanner} 一次调用同时产出意图、实体指称、查询参数与时间归一。
 * 这一步替换掉的是「规则最到不了」的三处——正则抽不出「近三个月」的中文数字、
 * 字符串匹配认不出「螺栓」就是「内六角螺栓」、contains 认不出「签了没」是审批状态。
 *
 * **降级档**：模型未配置、调用失败、输出不可解析、编码不在白名单、自评置信度低于阈值，
 * 任一情况走 {@link IntentClassifier} + {@link ParamExtractor} + {@link EntityNormalizer}
 * 的规则链路，行为与未接入模型时逐条一致。
 *
 * 两档共用同一道裁决：模型给的东西不被信任，实体编号要撞库、参数要过四道闸、
 * 意图要在启用清单内、数据范围由后端注入。模型说错话的代价是「按错意图查了一次受限数据」，
 * 而不是「绕过权限查了全表」。
 *
 * 每一步都可能中断并返回非 Success 结果码，且中断原因都以澄清选项或说明文字返回，
 * 不抛异常黑箱：用户看到的是「系统为什么不能查」，而不是一句参数错误。
 */
@Slf4j
@Service
public class ControlledQueryPipeline implements AiDataQueryPort {

    private final AiIntentCatalog catalog;
    private final IntentClassifier classifier;
    private final ParamExtractor extractor;
    private final ParamValidator validator;
    private final EntityNormalizer normalizer;
    private final DataQueryExecutor executor;
    private final LlmQueryPlanner planner;

    public ControlledQueryPipeline(AiIntentCatalog catalog, IntentClassifier classifier,
                                   ParamExtractor extractor, ParamValidator validator,
                                   EntityNormalizer normalizer, DataQueryExecutor executor,
                                   LlmQueryPlanner planner) {
        this.catalog = catalog;
        this.classifier = classifier;
        this.extractor = extractor;
        this.validator = validator;
        this.normalizer = normalizer;
        this.executor = executor;
        this.planner = planner;
    }

    @Override
    public DataAnswer query(String question, String overrideIntentCode, Map<String, Object> overrideParams,
                            Long confirmedEntityId, AiContext context) {
        DataAnswer answer = new DataAnswer();
        List<EntityNormalizer.Archive> archives = normalizer.archives();

        // ---------- 0 查询理解（模型档）----------
        // 一次调用拿到意图 + 实体 + 参数；返回 null 说明模型没接管，下面整条走规则。
        // override 是用户在上一次结果上手工改条件，此时问句不作数，不该再花一次调用去理解它。
        LlmQueryPlanner.Plan plan = null;
        if (overrideIntentCode == null || overrideIntentCode.isBlank()) {
            List<AiIntent> enabled = catalog.enabledIntents();
            plan = planner.plan(question, enabled, catalog.paramsByIntent(enabled), archives, context);
            if (plan != null && plan.isNone()) {
                // 模型说「不属于任何一类」只作参考，不作决定：直接据此判 Unsupported 会把
                // 规则本来答得上的问题砍掉（「台州的供应商」被这么砍过），回退词表复核。
                answer.getNotices().add("大模型判定不属于任何一类受控查询，已回退词表复核："
                        + trimReason(plan.reason()));
                plan = null;
            }
        }

        // ---------- 1 意图 M9-1-1 / M9-1-2 ----------
        AiIntent intent;
        boolean modelAccepted = false;
        List<EntityNormalizer.Group> ruleGroups = null;
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
            AiIntent fromPlan = plan == null ? null : catalog.byCode(plan.intentCode());
            if (fromPlan != null && plan.confidence() >= threshold) {
                intent = fromPlan;
                modelAccepted = true;
                answer.setConfidence(BigDecimal.valueOf(plan.confidence())
                        .setScale(4, java.math.RoundingMode.HALF_UP));
                answer.getNotices().add("意图、实体与参数由大模型一次性理解（" + intent.getIntentName()
                        + "，自评置信度 " + percent(answer.getConfidence()) + "%），已按白名单逐项校验");
                answer.setGenerator("llm");
            } else {
                if (plan != null) {
                    // 排障日志：notice 把「低置信」与「编码不可用」合成同一句话返回给用户，
                    // 但两者的处理方向相反（前者校准提示词，后者查契约与白名单），
                    // 只有日志能分清是哪一种，否则只能靠猜。
                    log.info("查询理解未采用：intent={} confidence={} threshold={} 问题={}",
                            plan.intentCode(), plan.confidence(), threshold, question);
                    answer.getNotices().add("大模型理解结果的置信度低于阈值或编码不可用，已回退规则链路："
                            + (plan.reason().isBlank() ? "未给出理由" : trimReason(plan.reason())));
                }
                ruleGroups = normalizer.detect(question, archives);
                IntentClassifier.IntentMatch match = classifier.classify(question, ruleGroups);
                if (match.getIntent() == null) {
                    answer.setResultCode(ResultCode.UNSUPPORTED.getCode());
                    answer.setSummary(unsupportedSummary(plan));
                    return answer;
                }
                if (match.getConfidence().doubleValue() < threshold) {
                    // 低置信时要不要反问，取决于「用户是不是真的在问这一类」。
                    // 判据是命中意图的专用词表有没有分：有分说明问句里确实出现了该领域的词
                    // （「最近哪家供应商交货老迟到」里的「供应商」「交货」），只是没说清哪个对象，
                    // 反问「请补充供应商」是有意义的；一个领域词都没有（「采购合同的违约金比例是多少」
                    // 只靠「多少」蹭上库存意图），反问就是空转，还给了用户一个本来就答不上的选项。
                    if (match.getLexiconScore() <= 0 && (ruleGroups == null || ruleGroups.isEmpty())) {
                        answer.setResultCode(ResultCode.UNSUPPORTED.getCode());
                        answer.setSummary(unsupportedSummary(plan));
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
        // 模型档用模型给的指称去撞库；它没认出实体时才让规则再看一遍，
        // 因为档案全名出现在问句里时规则比模型更可靠（字符串就是全名）。
        List<EntityNormalizer.Group> groups;
        if (modelAccepted && plan != null && !plan.entities().isEmpty()) {
            EntityNormalizer.Group inferred = inferredGroup(plan, archives);
            if (inferred != null) {
                // 语义推断不当场生效：给出最接近的候选让用户点一下，比替他决定更稳
                answer.getNotices().add("「" + inferred.getMention() + "」不是档案里的名称，"
                        + "按语义最接近「" + inferred.getCandidates().get(0).getName()
                        + "」，需要确认后再查");
                return ambiguousEntity(answer, inferred, intent);
            }
            groups = groupsOf(plan, archives, answer);
        } else if (ruleGroups != null) {
            groups = ruleGroups;
        } else {
            groups = normalizer.detect(question, archives);
        }
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
        // 模型档直接用模型给的参数与时间（「近三个月」已换算成具体日期、
        // 「签了没」已换算成 audit_status=2）；规则档走正则。
        ParamExtractor.Extraction extraction = modelAccepted && plan != null
                ? extractionOf(plan, specs)
                : extractor.extract(question, specs);
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

    /**
     * 找出「需要语义跳跃才能对上」的实体指称。
     *
     * 模型很擅长把「螺丝」想到「内六角螺栓」，但这类推断不该由系统替他确认：
     * 档案里可能只有一种螺栓，也可能有十种紧固件，答错一次就是「查了错的物料还理直气壮」。
     * 判据是字面关系——「螺栓」在「内六角螺栓」里、「温州正泰」在「温州正泰电气销售有限公司」里，
     * 这种叫可解释；「螺丝」对「内六角螺栓」一个字都不沾，就必须让用户点一下确认。
     *
     * 只在模型档生效：规则档的模糊匹配本来就要求片段包含，不存在这种跳跃。
     */
    private EntityNormalizer.Group inferredGroup(LlmQueryPlanner.Plan plan,
                                                 List<EntityNormalizer.Archive> archives) {
        for (LlmQueryPlanner.EntityMention mention : plan.entities()) {
            ChatVO.EntityRef ref = normalizer.refByNo(mention.type(), mention.no(), archives);
            if (ref == null) {
                ref = normalizer.refByName(mention.type(), mention.mention(), archives);
            }
            if (ref == null || literal(mention.mention(), ref)) {
                continue;
            }
            EntityNormalizer.Group group = new EntityNormalizer.Group(mention.type());
            group.setMention(mention.mention());
            group.getCandidates().add(ref);
            return group;
        }
        return null;
    }

    /**
     * 指称能不能字面解释得通。
     *
     * 只有「一个字都不沾」才算语义跳跃——「螺丝」对「内六角螺栓」是典型：
     * 用户说的是类别，档案里是具体型号，答不答得看运气。
     * 下面三种都算可解释，不走澄清：
     * 1. 指称就是业务编号（SUP-001），本来就不该和中文名比字面；
     * 2. 指称含代词（这个物料、那张单），它指向的是上一轮，由上下文继承负责；
     * 3. 指称与档案名有足够字符重叠（「内六角罗栓」对「内六角螺栓」是错别字，
     *    重叠 4/5；「螺丝」只重叠 1/2，不能算）。
     */
    private boolean literal(String mention, ChatVO.EntityRef ref) {
        if (mention == null || mention.isBlank() || ref.getName() == null) {
            return true;
        }
        String text = mention.trim();
        if (ref.getNo() != null && ref.getNo().equalsIgnoreCase(text)) {
            return true;
        }
        if (text.contains("这个") || text.contains("那个") || text.contains("该")
                || text.contains("它") || text.contains("这张") || text.contains("那家")) {
            return true;
        }
        int hit = 0;
        for (char ch : text.toCharArray()) {
            if (ref.getName().indexOf(ch) >= 0) {
                hit++;
            }
        }
        return (double) hit / text.length() >= 0.6;
    }

    /**
     * 把模型给出的实体指称落成可执行实体。
     *
     * 模型只给「类型 + 编号 + 指称」，不给 id——编号是它的建议，必须在这里撞库验证真的存在。
     * 不验证就等于信任模型编出来的 SUP-999，结果是「查了一个不存在的供应商」
     * 而不是「找不到这个供应商」，两种失败在用户看来完全不同。
     */
    private List<EntityNormalizer.Group> groupsOf(LlmQueryPlanner.Plan plan,
                                                  List<EntityNormalizer.Archive> archives,
                                                  DataAnswer answer) {
        List<EntityNormalizer.Group> groups = new ArrayList<>();
        for (LlmQueryPlanner.EntityMention mention : plan.entities()) {
            ChatVO.EntityRef ref = normalizer.refByNo(mention.type(), mention.no(), archives);
            if (ref == null) {
                ref = normalizer.refByName(mention.type(), mention.mention(), archives);
            }
            if (ref == null) {
                // 编号与名称都撞不上：模型可能把「宁波那家」对到了一个不存在的档案。
                // 不采信、也不静默丢掉——交给规则按指称再找一次，还找不到就明确告知已忽略。
                EntityNormalizer.Group hit = normalizer.detect(mention.mention(), archives).stream()
                        .filter(group -> group.getType().equals(mention.type()))
                        .findFirst().orElse(null);
                if (hit != null) {
                    groups.add(hit);
                } else {
                    answer.getNotices().add("未能把「" + mention.mention() + "」对应到"
                            + normalizer.typeName(mention.type()) + "档案，该条件已忽略。");
                }
                continue;
            }
            ref.setMatchedBy("llm");
            EntityNormalizer.Group group = new EntityNormalizer.Group(mention.type());
            group.setMention(mention.mention());
            group.getCandidates().add(ref);
            groups.add(group);
        }
        return groups;
    }

    /**
     * 把模型给出的参数转成与正则提取同构的结果，后续走同一个 ParamValidator。
     *
     * 口径与规则档保持一致：意图不支持的条件记 rejected（上层据此回 InvalidParameter），
     * 不悄悄丢掉。丢掉会让用户拿到一个「看似按他要求查了、其实少了条件」的答案。
     */
    private ParamExtractor.Extraction extractionOf(LlmQueryPlanner.Plan plan, List<AiIntentParam> specs) {
        ParamExtractor.Extraction extraction = new ParamExtractor.Extraction();
        for (Map.Entry<String, Object> entry : plan.params().entrySet()) {
            String code = entry.getKey();
            Object value = entry.getValue();
            // 实体类参数一律不采信模型的值：它拿到的只有业务编号（M-1001），
            // 而这里要的是主键。这一步走 entities → 撞库 → 主键，已在上面完成。
            if (ENTITY_PARAM_CODES.contains(code)) {
                continue;
            }
            boolean supported = specs.stream().anyMatch(spec -> code.equals(spec.getParamCode()));
            if (!supported) {
                extraction.reject(code, code, value, "当前意图不支持该条件");
                continue;
            }
            extraction.put(code, value, paramNameOf(specs, code));
        }
        timeRangeOf(plan, extraction);
        return extraction;
    }

    private String paramNameOf(List<AiIntentParam> specs, String code) {
        return specs.stream().filter(spec -> code.equals(spec.getParamCode()))
                .map(AiIntentParam::getParamName).findFirst().orElse(code);
    }

    /** 时间范围用于前端展示「这次查的是哪一段」，模型已把相对时间换成具体日期 */
    private void timeRangeOf(LlmQueryPlanner.Plan plan, ParamExtractor.Extraction extraction) {
        Object from = plan.params().get("date_from");
        Object to = plan.params().get("date_to");
        if (from == null && to == null) {
            return;
        }
        ChatVO.TimeRange range = new ChatVO.TimeRange();
        range.setFrom(from == null ? null : String.valueOf(from));
        range.setTo(to == null ? null : String.valueOf(to));
        range.setLabel(from != null && to != null ? from + " 至 " + to
                : String.valueOf(from == null ? to : from));
        extraction.setTimeRange(range);
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

    /**
     * 判不出意图时给用户看的说明。
     *
     * 模型判了但没把握时（「指代不明，疑为比价但对象不清」），把它的判断转告用户：
     * 直接回一句「不属于支持的五类查询」会把用户丢在原地——他明明是在追问，只是没说清对象，
     * 系统却告诉他这类问题根本不受理。
     *
     * 只改说明文字，**不改结果码**：能力边界仍然是边界，
     * 不能因为模型猜了一句就把「答不上」改成「答得上」。
     */
    private String unsupportedSummary(LlmQueryPlanner.Plan plan) {
        String base = "这个问题不属于当前支持的订单、交付、价格、库存、供应商交付五类查询。";
        if (plan == null || plan.reason() == null || plan.reason().isBlank()) {
            return base + "可以换个说法，或到对应业务页面查看。";
        }
        return base + "大模型的判断是「" + trimReason(plan.reason()) + "」。"
                + "如果是接着上一轮追问，请补上对象（物料名、供应商名或单据号）；"
                + "也可以到对应业务页面查看。";
    }
}
