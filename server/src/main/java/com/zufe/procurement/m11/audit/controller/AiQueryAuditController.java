package com.zufe.procurement.m11.audit.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.audit.service.AiQueryAuditService;
import com.zufe.procurement.m11.audit.vo.AiQueryAuditVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 智能查询审计接口，功能点 M11-4-5 列表、M11-4-6 详情与导出。
 * 只读接口，权限点全部为 m11:log:*，与智能模块的只读约束保持一致。
 */
@RestController
@RequestMapping("/api/m11/query-audits")
public class AiQueryAuditController {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AiQueryAuditService auditService;

    public AiQueryAuditController(AiQueryAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @RequirePermission("m11:log:list")
    public Result<PageResult<AiQueryAuditVO>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String intentCode,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(auditService.page(pageNo, pageSize, username, intentCode, resultCode, startTime, endTime));
    }

    @GetMapping("/{id}")
    @RequirePermission("m11:log:list")
    public Result<AiQueryAuditVO> detail(@PathVariable Long id) {
        return Result.ok(auditService.detail(id));
    }

    @GetMapping("/export")
    @RequirePermission("m11:log:export")
    public ResponseEntity<Resource> export(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String intentCode,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        byte[] csv = auditService.exportCsv(username, intentCode, resultCode, startTime, endTime);
        String filename = "智能查询审计_" + LocalDateTime.now().format(FILE_TIME) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(new ByteArrayResource(csv));
    }
}
