package com.zufe.procurement.m7.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m7.assistant.dto.ChatRequest;
import com.zufe.procurement.m7.assistant.entity.AiMessage;
import com.zufe.procurement.m7.assistant.entity.AiSession;
import com.zufe.procurement.m7.assistant.port.AiDataQueryPort;
import com.zufe.procurement.m7.assistant.port.AiKnowledgePort;
import com.zufe.procurement.m7.assistant.vo.AiContext;
import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m7.assistant.vo.DataAnswer;
import com.zufe.procurement.m7.assistant.vo.KnowledgeAnswer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能助手编排（M7）：分类 → 鉴权 → 能力调用 → 组装 → 落库 → 审计。
 *
 * 三条硬约束在这里落地：
 * 1. 越权不是抛异常了事，而是返回 NoPermission 并说明原因，同时写审计（M7-2-3）；
 * 2. 每次问答无论结果如何都写 ai_message 与 ai_query_audit（M7-4-4）；
 * 3. 只有 M7 认识 M8/M9，反向不认识——能力通过端口注入，编排不散落在各模块。
 */
@Slf4j
@Service
public class AiChatService {

    private static final String PERM_KB_QUERY = "m8:kb:query";
    private static final String PERM_DATA_QUERY = "m9:data:query";
    private static final String PERM_RETRY = "m7:query:retry";

    private final AiSessionService sessionService;
    private final AiAuditService auditService;
    private final RouteService routeService;
    private final AiDataQueryPort dataQueryPort;
    private final AiKnowledgePort knowledgePort;
    private final ObjectMapper objectMapper;

    public AiChatService(AiSessionService sessionService, AiAuditService auditService,
                         RouteService routeService, AiDataQueryPort dataQueryPort,
                         AiKnowledgePort knowledgePort, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.routeService = routeService;
        this.dataQueryPort = dataQueryPort;
        this.knowledgePort = knowledgePort;
        this.objectMapper = objectMapper;
    }

    public ChatOutcome chat(ChatRequest request, String ip) {
        long started = System.currentTimeMillis();
        CurrentUser user = AuthContext.getOrThrow();
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "问题不能为空");
        }
        boolean override = request.getOverrideRoute() != null || request.getOverrideIntentCode() != null
                || request.getOverrideParams() != null || request.getConfirmedEntityId() != null;
        if (override && !user.hasPermission(PERM_RETRY)) {
            throw new BizException(ResultCode.NO_PERMISSION, "没有参数修正与重跑的权限");
        }

        AiSession session = sessionService.resolve(user.getUserId(), request.getSessionId(), question);
        // 上下文只读一次：路由、意图、指代都用同一份，避免三处各自读库读到不同快照
        AiContext historyContext = sessionService.readContext(session);
        RouteService.Decision decision = routeService.decide(question, historyContext);
        Integer route = request.getOverrideRoute() != null ? request.getOverrideRoute() : decision.getRoute();

        int msgType = route == null ? 6 : route == 3 ? 3 : route == 2 ? 2 : 1;
        AiMessage userMessage = sessionService.appendUserMessage(session.getId(), question, msgType);

        ChatVO vo = new ChatVO();
        vo.setSessionId(session.getId());
        vo.setQuestionMessageId(userMessage.getId());
        vo.setRoute(route);
        vo.setRouteName(routeService.routeName(route));
        vo.setConfidence(decision.getConfidence());
        vo.setNotices(new ArrayList<>());
        // 默认由规则链路产出，M8/M9 拿到模型结果后会覆盖成 llm
        vo.setGenerator("rule");
        if (decision.isModelAssisted()) {
            vo.getNotices().add("问题类型由大模型补判（置信度 "
                    + percentOf(decision.getConfidence()) + "%），只读边界与数据范围仍由后端把关");
        }

        String code;
        String intentCode = null;
        Map<String, Object> effectiveParams = new LinkedHashMap<>();
        Object scopeSnapshot = null;
        int rowCount = 0;

        try {
            if (request.getOverrideRoute() == null && decision.getStatus() == RouteService.Status.UNSUPPORTED) {
                code = ResultCode.UNSUPPORTED.getCode();
                vo.setAnswer(decision.getMessage());
                vo.setResultCodeHint(ResultCode.UNSUPPORTED.getCode() + "：该问题不属于智能助手的三类能力范围");
            } else if (request.getOverrideRoute() == null
                    && decision.getStatus() == RouteService.Status.READONLY_REFUSED) {
                code = ResultCode.UNSUPPORTED.getCode();
                vo.setAnswer(decision.getMessage());
                vo.setResultCodeHint(ResultCode.UNSUPPORTED.getCode() + "：智能助手只读，不执行业务变更");
            } else if (request.getOverrideRoute() == null
                    && decision.getStatus() == RouteService.Status.CLARIFY) {
                code = ResultCode.AMBIGUOUS.getCode();
                vo.setAnswer(decision.getMessage());
                ChatVO.Clarify clarify = new ChatVO.Clarify();
                clarify.setType("route");
                clarify.setQuestion("您想查哪一类？");
                clarify.setOptions(decision.getOptions());
                vo.setClarify(clarify);
                vo.setResultCodeHint(ResultCode.AMBIGUOUS.getCode() + "：问题类型不明确，请选择方向");
            } else if (route != null && (route == 1 || route == 2)) {
                if (!user.hasPermission(PERM_KB_QUERY)) {
                    code = ResultCode.NO_PERMISSION.getCode();
                    vo.setAnswer("您的角色没有制度与教程问答权限（" + PERM_KB_QUERY + "），请联系管理员授权。");
                    vo.setResultCodeHint(ResultCode.NO_PERMISSION.getCode() + "：缺少制度问答权限");
                } else {
                    KnowledgeAnswer answer = knowledgePort.answer(question, route);
                    intentCode = route == 1 ? "kb_document_qa" : "kb_tutorial_qa";
                    code = answer.getResultCode() == null ? ResultCode.SUCCESS.getCode() : answer.getResultCode();
                    applyKnowledge(vo, answer, intentCode);
                }
            } else if (route != null && route == 3) {
                if (!user.hasPermission(PERM_DATA_QUERY)) {
                    code = ResultCode.NO_PERMISSION.getCode();
                    vo.setAnswer("您的角色没有受控数据查询权限（" + PERM_DATA_QUERY + "），请联系管理员授权。");
                    vo.setResultCodeHint(ResultCode.NO_PERMISSION.getCode() + "：缺少数据查询权限");
                } else {
                    DataAnswer answer = dataQueryPort.query(question, request.getOverrideIntentCode(),
                            request.getOverrideParams(), request.getConfirmedEntityId(), historyContext);
                    intentCode = answer.getIntentCode();
                    code = answer.getResultCode() == null ? ResultCode.SUCCESS.getCode() : answer.getResultCode();
                    applyData(vo, answer, user.hasPermission(PERM_RETRY));
                    effectiveParams = currentParams(answer);
                    scopeSnapshot = answer.getScopeSnapshot();
                    rowCount = answer.getRowCount() == null ? 0 : answer.getRowCount();
                    if (answer.getResultCode() != null
                            && !ResultCode.SUCCESS.getCode().equals(answer.getResultCode())) {
                        vo.setResultCodeHint(hintOf(answer.getResultCode()));
                    }
                }
            } else {
                code = ResultCode.UNSUPPORTED.getCode();
                vo.setAnswer("请先选择问题类型（制度问答 / 操作教程 / 数据查询）。");
                vo.setResultCodeHint("Unsupported：未选择问题类型");
            }
        } catch (BizException e) {
            code = e.getResultCode().getCode();
            vo.setAnswer(e.getMessage());
            vo.setResultCodeHint(hintOf(code));
        } catch (Exception e) {
            log.error("智能助手处理失败：{}", question, e);
            code = ResultCode.UNSUPPORTED.getCode();
            vo.setAnswer("抱歉，处理这个问题时出现内部错误，已记录日志。可以换个问法或稍后重试。");
            vo.setResultCodeHint("Unsupported：处理失败");
        }

        int elapsed = (int) (System.currentTimeMillis() - started);
        vo.setResultCode(code);
        vo.setElapsedMs(elapsed);
        if (vo.getAnswer() == null) {
            vo.setAnswer("已完成处理。");
        }

        // 重跑对比 M7-3-4
        if (override) {
            AiMessage previous = sessionService.lastAssistantMessage(session.getId());
            if (previous != null) {
                ChatVO.RerunCompare compare = new ChatVO.RerunCompare();
                compare.setPreviousResultCode(previous.getResultCode());
                compare.setPreviousSummary(truncate(previous.getContent(), 80));
                compare.setPreviousRowCount(rowCountOf(previous.getResultJson()));
                compare.setChangedParams(sessionService.diffParams(previous.getParamsJson(), effectiveParams));
                vo.setCompare(compare);
            }
        }

        AiMessage assistantMessage = sessionService.appendAssistantMessage(session.getId(), vo.getAnswer(),
                answerMsgType(route, msgType, code, vo), intentCode, jsonOrNull(effectiveParams),
                resultJson(vo, rowCount), refDocIds(vo), code, elapsed);
        vo.setAnswerMessageId(assistantMessage.getId());

        AiContext context = new AiContext();
        context.setIntentCode(intentCode);
        context.setEntities(vo.getCard() == null ? vo.getEntities() : vo.getCard().getEntities());
        context.setParams(effectiveParams);
        sessionService.updateContext(session.getId(), intentCode, context);

        auditService.record(session.getId(), question, intentCode, effectiveParams, vo.getCard() == null
                        ? null : vo.getCard().getRejected(), scopeSnapshot, code, rowCount, elapsed, ip);

        return new ChatOutcome(code, vo);
    }

    // ---------------- 结果映射 ----------------

    /**
     * 助手回答的消息类型（M7-4 / M8-5-2）。
     * 拒答记 5、澄清记 4：否则拒答会被算进「知识类有效回答」，
     * 引用覆盖率与拒答率两个统计同时失真（覆盖率虚高、拒答数恒为 0）。
     */
    private int answerMsgType(Integer route, int msgType, String code, ChatVO vo) {
        if (ResultCode.NO_DATA.getCode().equals(code) && route != null && (route == 1 || route == 2)) {
            return 5;
        }
        if (ResultCode.AMBIGUOUS.getCode().equals(code) && vo.getClarify() != null) {
            return 4;
        }
        return msgType;
    }

    private void applyData(ChatVO vo, DataAnswer answer, boolean retryEnabled) {
        vo.setIntentCode(answer.getIntentCode());
        vo.setIntentName(answer.getIntentName());
        vo.setConfidence(answer.getConfidence());
        vo.setAnswer(answer.getSummary());
        vo.setTable(answer.getTable());
        if (answer.getGenerator() != null) {
            vo.setGenerator(answer.getGenerator());
        }
        if (answer.getNotices() != null) {
            vo.getNotices().addAll(answer.getNotices());
        }
        ChatVO.IntentCard card = new ChatVO.IntentCard();
        card.setIntentCode(answer.getIntentCode());
        card.setIntentName(answer.getIntentName());
        card.setServiceCode(answer.getServiceCode());
        card.setEntities(answer.getEntities());
        card.setTimeRange(answer.getTimeRange());
        card.setFilters(answer.getFilters());
        card.setMissing(answer.getMissing());
        card.setRejected(answer.getRejected());
        card.setEditable(retryEnabled);
        vo.setCard(card);
        vo.setEntities(answer.getEntities());
        if (answer.getClarifyType() != null) {
            ChatVO.Clarify clarify = new ChatVO.Clarify();
            clarify.setType(answer.getClarifyType());
            clarify.setQuestion(answer.getClarifyQuestion());
            clarify.setOptions(answer.getClarifyOptions() == null ? List.of() : answer.getClarifyOptions());
            vo.setClarify(clarify);
        }
    }

    private void applyKnowledge(ChatVO vo, KnowledgeAnswer answer, String intentCode) {
        vo.setIntentCode(intentCode);
        vo.setIntentName("kb_document_qa".equals(intentCode) ? "制度条款问答" : "操作教程问答");
        vo.setAnswer(answer.getAnswer());
        vo.setRefs(answer.getRefs());
        vo.setDualPath(answer.getDualPath());
        vo.setRefusalReason(answer.getRefusalReason());
        vo.setVersionConflict(answer.getVersionConflict());
        vo.setConfidence(answer.getTopScore());
        if (answer.getGenerator() != null) {
            vo.setGenerator(answer.getGenerator());
        }
        if (answer.getNotices() != null) {
            vo.getNotices().addAll(answer.getNotices());
        }
        if (answer.getStepCount() != null) {
            vo.getNotices().add("后端按原文顺序规整出 " + answer.getStepCount() + " 个操作步骤");
        }
        if (answer.getRefusalReason() != null) {
            vo.setResultCodeHint(hintOf(ResultCode.NO_DATA.getCode()));
        }
    }

    private Map<String, Object> currentParams(DataAnswer answer) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (answer.getFilters() == null) {
            return params;
        }
        for (ChatVO.ParamItem item : answer.getFilters()) {
            if (item.getValue() != null) {
                params.put(item.getCode(), item.getValue());
            }
        }
        return params;
    }

    private String resultJson(ChatVO vo, int rowCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rowCount", rowCount);
        snapshot.put("table", vo.getTable());
        snapshot.put("refs", vo.getRefs());
        snapshot.put("dualPath", vo.getDualPath());
        snapshot.put("card", vo.getCard());
        snapshot.put("clarify", vo.getClarify());
        snapshot.put("refusalReason", vo.getRefusalReason());
        snapshot.put("versionConflict", vo.getVersionConflict());
        return jsonOrNull(snapshot);
    }

    private String refDocIds(ChatVO vo) {
        if (vo.getRefs() == null || vo.getRefs().isEmpty()) {
            return null;
        }
        return vo.getRefs().stream().map(ref -> String.valueOf(ref.getDocId()))
                .distinct().collect(Collectors.joining(","));
    }

    private Integer rowCountOf(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(resultJson, Map.class);
            Object value = map.get("rowCount");
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String jsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** 置信度百分比保留一位小数，供提示文案使用 */
    private String percentOf(java.math.BigDecimal ratio) {
        if (ratio == null) {
            return "0.0";
        }
        return ratio.multiply(java.math.BigDecimal.valueOf(100))
                .setScale(1, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    /** 七种结果码的差异化提示（M7-4-3） */
    private String hintOf(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "Success" -> "Success：已按条件查到结果";
            case "NotFound" -> "NotFound：单据编号不存在（与「条件内无数据」不是一回事）";
            case "NoData" -> "NoData：条件内没有匹配数据，或知识库依据不足";
            case "Ambiguous" -> "Ambiguous：存在歧义，请补充条件或从候选里选一个";
            case "NoPermission" -> "NoPermission：超出您的角色或数据范围";
            case "Unsupported" -> "Unsupported：暂不支持该类问题，不做臆测";
            case "InvalidParameter" -> "InvalidParameter：参数不在白名单内，已拒绝执行";
            default -> code;
        };
    }

    @Data
    public static class ChatOutcome {
        private final String code;
        private final ChatVO vo;

        public ChatOutcome(String code, ChatVO vo) {
            this.code = code;
            this.vo = vo;
        }
    }
}
