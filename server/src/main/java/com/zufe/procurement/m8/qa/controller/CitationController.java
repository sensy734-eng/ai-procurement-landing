package com.zufe.procurement.m8.qa.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m8.qa.service.CitationStatsService;
import com.zufe.procurement.m8.qa.service.CitationValidator;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 引用有效性校验（M8-3-3）与引用覆盖率（M8-3-4）。
 *
 * verify 单独开放，是为了让「引用确实来自所引文档」这件事可以被外部验证：
 * 拿答案里的 docId + chunkId 直接查一次，换成别的 docId 就应当判无效。
 */
@RestController
@RequestMapping("/api/assistant/citations")
public class CitationController {

    private final CitationValidator citationValidator;
    private final CitationStatsService citationStatsService;

    public CitationController(CitationValidator citationValidator,
                              CitationStatsService citationStatsService) {
        this.citationValidator = citationValidator;
        this.citationStatsService = citationStatsService;
    }

    @PostMapping("/verify")
    @RequirePermission("m8:kb:cite")
    public Result<CitationValidator.Check> verify(@RequestBody Map<String, Object> body) {
        Long docId = toLong(body.get("docId"));
        Long chunkId = toLong(body.get("chunkId"));
        return Result.ok(citationValidator.verify(docId, chunkId));
    }

    @GetMapping("/coverage")
    @RequirePermission("m9:audit:list")
    public Result<Map<String, Object>> coverage() {
        return Result.ok(citationStatsService.coverage());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
