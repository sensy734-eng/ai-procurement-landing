package com.zufe.procurement.m7.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.m7.assistant.entity.AiMessage;
import com.zufe.procurement.m7.assistant.entity.AiSession;
import com.zufe.procurement.m7.assistant.mapper.AiMessageMapper;
import com.zufe.procurement.m7.assistant.mapper.AiSessionMapper;
import com.zufe.procurement.m7.assistant.vo.AiContext;
import com.zufe.procurement.m7.assistant.vo.ChatMessageVO;
import com.zufe.procurement.m7.assistant.vo.SessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话与消息（M7-1-1、M7-1-2、M7-1-3）。
 *
 * 会话只增不删：审计要求每次问答都能回溯到当时的问句与结果，
 * 因此助手行的 result_json 原样保存，前端刷新后重新渲染而不是重跑查询。
 */
@Slf4j
@Service
public class AiSessionService {

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public AiSessionService(AiSessionMapper sessionMapper, AiMessageMapper messageMapper,
                            ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    /** 取会话；sessionId 为空或不属于当前用户时新建 */
    public AiSession resolve(Long userId, Long sessionId, String question) {
        if (sessionId != null) {
            AiSession session = sessionMapper.selectById(sessionId);
            if (session != null && userId.equals(session.getUserId())) {
                return session;
            }
        }
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setTitle(title(question));
        session.setMessageCount(0);
        session.setLastTime(LocalDateTime.now());
        session.setIsDeleted(0);
        session.setVersion(0);
        sessionMapper.insert(session);
        return session;
    }

    public AiMessage appendUserMessage(Long sessionId, String content, int msgType) {
        return append(sessionId, 1, content, msgType, null, null, null, null, null, 0);
    }

    public AiMessage appendAssistantMessage(Long sessionId, String content, int msgType, String intentCode,
                                            String paramsJson, String resultJson, String refDocIds,
                                            String resultCode, int elapsedMs) {
        return append(sessionId, 2, content, msgType, intentCode, paramsJson, resultJson, refDocIds,
                resultCode, elapsedMs);
    }

    private AiMessage append(Long sessionId, int role, String content, int msgType, String intentCode,
                             String paramsJson, String resultJson, String refDocIds, String resultCode,
                             int elapsedMs) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setMsgType(msgType);
        message.setIntentCode(intentCode);
        message.setParamsJson(paramsJson);
        message.setResultJson(resultJson);
        message.setRefDocIds(refDocIds);
        message.setResultCode(resultCode);
        message.setElapsedMs(elapsedMs);
        message.setIsDeleted(0);
        messageMapper.insert(message);

        AiSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
            session.setLastTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        return message;
    }

    /** 更新上一轮意图与实体，供下一轮指代消解（M7-1-3） */
    public void updateContext(Long sessionId, String intentCode, AiContext context) {
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        session.setLastIntentCode(intentCode);
        try {
            session.setContextJson(context == null ? null : objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("上下文序列化失败", e);
        }
        session.setLastTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    public AiContext readContext(AiSession session) {
        if (session == null || session.getContextJson() == null || session.getContextJson().isBlank()) {
            return null;
        }
        try {
            AiContext context = objectMapper.readValue(session.getContextJson(), AiContext.class);
            return AccessSnapshot.fingerprint(AuthContext.getOrThrow()).equals(context.getAccessFingerprint())
                    ? context : null;
        } catch (Exception e) {
            return null;
        }
    }

    public List<SessionVO> listSessions(Long userId) {
        List<AiSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getUserId, userId)
                .orderByDesc(AiSession::getLastTime)
                .last("LIMIT 50"));
        List<SessionVO> result = new ArrayList<>();
        for (AiSession session : sessions) {
            SessionVO vo = new SessionVO();
            vo.setId(session.getId());
            vo.setTitle(session.getTitle());
            vo.setMessageCount(session.getMessageCount());
            vo.setLastIntentCode(session.getLastIntentCode());
            vo.setLastTime(session.getLastTime());
            vo.setCreatedAt(session.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    public List<ChatMessageVO> messages(Long sessionId, Long userId) {
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new BizException(ResultCode.NO_PERMISSION, "只能查看自己的会话");
        }
        List<AiMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getId));
        List<ChatMessageVO> result = new ArrayList<>();
        for (AiMessage message : messages) {
            ChatMessageVO vo = new ChatMessageVO();
            vo.setId(message.getId());
            vo.setRole(message.getRole());
            vo.setContent(message.getContent());
            vo.setMsgType(message.getMsgType());
            vo.setIntentCode(message.getIntentCode());
            vo.setParamsJson(message.getParamsJson());
            vo.setResultJson(message.getResultJson());
            vo.setRefDocIds(message.getRefDocIds());
            vo.setResultCode(message.getResultCode());
            vo.setElapsedMs(message.getElapsedMs());
            vo.setCreatedAt(message.getCreatedAt());
            if (Integer.valueOf(2).equals(message.getRole()) && !mayReadSnapshot(message)) {
                vo.setContent("当前权限或数据范围已变化，该历史结果不再展示。请重新提问以获取当前权限范围内的结果。");
                vo.setResultJson(null);
                vo.setParamsJson(null);
                vo.setRefDocIds(null);
                vo.setResultCode(ResultCode.NO_PERMISSION.getCode());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 解析错误反馈（M7-3-3）：作为测试集候选留痕。
     * 复用消息表存一条反馈记录（role=1、intent_code=parse_feedback），
     * 不为一个低频反馈单开一张表，H 域表结构保持不变。
     */
    public void appendFeedback(Long sessionId, String content, Map<String, Object> payload) {
        if (sessionId == null || sessionId <= 0L) {
            log.warn("反馈未关联会话，已忽略：{}", content);
            return;
        }
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null || !AuthContext.getOrThrow().getUserId().equals(session.getUserId())) {
            throw new BizException(ResultCode.NO_PERMISSION, "只能反馈自己的会话");
        }
        String paramsJson = null;
        try {
            paramsJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("反馈参数序列化失败", e);
        }
        append(sessionId, 1, content, 6, "parse_feedback", paramsJson, null, null, null, 0);
    }

    /** 上一条助手回答，用于重跑对比（M7-3-4） */    public AiMessage lastAssistantMessage(Long sessionId) {
        AiMessage message = messageMapper.selectOne(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .eq(AiMessage::getRole, 2)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT 1"));
        return message != null && mayReadSnapshot(message) ? message : null;
    }

    private boolean mayReadSnapshot(AiMessage message) {
        var user = AuthContext.getOrThrow();
        boolean knowledge = message.getIntentCode() != null && message.getIntentCode().startsWith("kb_");
        if (!user.hasPermission(knowledge ? "m8:kb:query" : "m9:data:query")) return false;
        try {
            var json = objectMapper.readTree(message.getResultJson() == null ? "{}" : message.getResultJson());
            String saved = json.path("accessFingerprint").asText("");
            if (!saved.isBlank()) return saved.equals(AccessSnapshot.fingerprint(user));
            // Legacy public knowledge remains readable; legacy data requires unrestricted scope.
            return knowledge || user.getScope().isAll();
        } catch (Exception ignored) { return false; }
    }

    /** 解析重跑前后参数差异，供前端展示「改了哪一项」 */
    public List<String> diffParams(String beforeJson, Map<String, Object> after) {
        Map<String, Object> before = new LinkedHashMap<>();
        if (beforeJson != null && !beforeJson.isBlank()) {
            try {
                before = objectMapper.readValue(beforeJson, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                return List.of();
            }
        }
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object old = before.get(entry.getKey());
            if (old == null || !String.valueOf(old).equals(String.valueOf(entry.getValue()))) {
                changed.add(entry.getKey() + "：" + (old == null ? "未设置" : old) + " → " + entry.getValue());
            }
        }
        for (String key : before.keySet()) {
            if (!after.containsKey(key)) {
                changed.add(key + "：" + before.get(key) + " → 已去掉");
            }
        }
        return changed;
    }

    private String title(String question) {
        String text = question == null ? "新会话" : question.trim();
        return text.length() <= 20 ? text : text.substring(0, 20);
    }
}
