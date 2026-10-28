package com.zufe.procurement.m7.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.m11.audit.entity.AiQueryAudit;
import com.zufe.procurement.m11.audit.mapper.AiQueryAuditMapper;
import com.zufe.procurement.m7.assistant.mapper.AiMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能查询审计写入与概览（M7-4-4、M9-5-5）。
 *
 * 写入走 M11-4-5 已交付的 ai_query_audit 表与 Mapper，不另建表、不另建 Mapper；
 * 列表、详情、导出复用 /api/m11/query-audits，本类只补两件 M11 没有的事：
 * 记录一次问答，以及按结果码与意图聚合的概览。
 *
 * 被拒参数与数据范围是本课题「受控」主张的取证材料：
 * 前者证明越界参数被拦下，后者证明过滤条件进入了数据库而不是在内存里筛选。
 */
@Slf4j
@Service
public class AiAuditService {

    private final AiQueryAuditMapper auditMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public AiAuditService(AiQueryAuditMapper auditMapper, AiMessageMapper messageMapper,
                          ObjectMapper objectMapper) {
        this.auditMapper = auditMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public void record(Long sessionId, String question, String intentCode, Object params,
                       Object rejectedParams, Object dataScope, String resultCode, int rowCount,
                       int elapsedMs, String ip) {
        try {
            AiQueryAudit audit = new AiQueryAudit();
            audit.setSessionId(sessionId);
            CurrentUser user = AuthContext.get();
            audit.setUserId(user == null ? 0L : user.getUserId());
            audit.setUsername(user == null ? null : user.getUsername());
            audit.setQuestion(question == null ? "" : (question.length() > 500
                    ? question.substring(0, 500) : question));
            audit.setIntentCode(intentCode);
            audit.setParamsJson(json(params));
            audit.setRejectedParamsJson(json(rejectedParams));
            audit.setDataScopeJson(json(dataScope));
            audit.setResultCode(resultCode);
            audit.setRowCount(rowCount);
            audit.setElapsedMs(elapsedMs);
            audit.setIp(ip);
            audit.setIsDeleted(0);
            auditMapper.insert(audit);
        } catch (Exception e) {
            // 审计写入失败不能吞掉用户答案，但必须留下日志
            log.error("智能查询审计写入失败：{}", question, e);
        }
    }

    /** 概览：结果码分布、意图分布、拒答率、引用覆盖率（M8-3-4、M8-5-3、M9-5-1） */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Long> byCode = new LinkedHashMap<>();
        long total = 0;
        for (Map<String, Object> row : auditMapper.countByResultCode()) {
            String code = row.get("code") == null ? "Unknown" : String.valueOf(row.get("code"));
            long count = toLong(row.get("cnt"));
            byCode.put(code, count);
            total += count;
        }
        Map<String, Long> byIntent = new LinkedHashMap<>();
        for (Map<String, Object> row : auditMapper.countByIntent()) {
            String code = row.get("code") == null ? "unrouted" : String.valueOf(row.get("code"));
            byIntent.put(code, toLong(row.get("cnt")));
        }
        long noData = byCode.getOrDefault("NoData", 0L);
        long noPermission = byCode.getOrDefault("NoPermission", 0L);
        long invalid = byCode.getOrDefault("InvalidParameter", 0L);
        long effective = messageMapper.countEffectiveAnswers();
        long cited = messageMapper.countCitedAnswers();
        long refusals = messageMapper.countRefusals();
        stats.put("total", total);
        stats.put("byResultCode", byCode);
        stats.put("byIntent", byIntent);
        stats.put("refusalCount", refusals);
        stats.put("refusalRate", rate(noData, total));
        stats.put("noPermissionCount", noPermission);
        stats.put("invalidParamCount", invalid);
        stats.put("effectiveAnswers", effective);
        stats.put("citedAnswers", cited);
        stats.put("citationCoverage", rate(cited, effective));
        return stats;
    }

    private double rate(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round(part * 10000.0 / total) / 10000.0;
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
