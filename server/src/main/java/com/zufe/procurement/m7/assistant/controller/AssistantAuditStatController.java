package com.zufe.procurement.m7.assistant.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m7.assistant.service.AiAuditService;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 智能查询审计概览（P17 顶部卡片）。
 *
 * 列表、详情、导出已由 M11-4-5/4-6 的 /api/m11/query-audits 提供，这里只补聚合口径：
 * 结果码分布、意图分布、拒答率、引用覆盖率。不复制一套列表接口，避免两条读路径口径漂移。
 */
@RestController
@RequestMapping("/api/assistant/audits")
public class AssistantAuditStatController {

    private final AiAuditService auditService;

    public AssistantAuditStatController(AiAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/stats")
    @RequirePermission("m9:audit:list")
    public Result<Map<String, Object>> stats() {
        return Result.ok(auditService.stats());
    }
}
