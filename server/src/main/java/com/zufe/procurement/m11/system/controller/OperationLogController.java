package com.zufe.procurement.m11.system.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.system.service.OperationLogService;
import com.zufe.procurement.m11.system.vo.OperationLogVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志接口，功能点 M11-4-4 操作日志检索。
 * 导出为 CSV，UTF-8 BOM，Excel 可直接打开，不引入额外的 Excel 二进制库。
 */
@RestController
@RequestMapping("/api/m11/operation-logs")
public class OperationLogController {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OperationLogService logService;

    public OperationLogController(OperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    @RequirePermission("m11:log:list")
    public Result<PageResult<OperationLogVO>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(logService.page(pageNo, pageSize, username, moduleCode, action, startTime, endTime));
    }

    @GetMapping("/export")
    @RequirePermission("m11:log:export")
    public ResponseEntity<Resource> export(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        byte[] csv = logService.exportCsv(username, moduleCode, action, startTime, endTime);
        String filename = "操作日志_" + LocalDateTime.now().format(FILE_TIME) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(new ByteArrayResource(csv));
    }
}
