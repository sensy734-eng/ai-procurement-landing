package com.zufe.procurement.m10.kb.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m10.kb.dto.ChunkMergeRequest;
import com.zufe.procurement.m10.kb.service.KbChunkService;
import com.zufe.procurement.m10.kb.service.KbIndexService.IndexBuildResult;
import com.zufe.procurement.m10.kb.vo.IndexBuildVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 切块调整接口（M10-2-3 切块预览与调整的手工合并部分）。
 * 合并后重建该文档倒排与向量索引（增量单位是文档，M10-3-3）。
 */
@RestController
@RequestMapping("/api/m10/documents")
public class KbChunkController {

    private final KbChunkService chunkService;

    public KbChunkController(KbChunkService chunkService) {
        this.chunkService = chunkService;
    }

    /** 手工合并相邻切块 */
    @PostMapping("/{docId}/chunks/merge")
    @RequirePermission("m10:chunk:edit")
    public Result<IndexBuildVO> merge(@PathVariable Long docId, @RequestBody ChunkMergeRequest request) {
        IndexBuildResult result = chunkService.merge(docId, request);
        IndexBuildVO vo = new IndexBuildVO();
        vo.setDocCount(result.getDocCount());
        vo.setChunkCount(result.getChunkCount());
        vo.setTokenCount(result.getTokenCount());
        vo.setElapsedMs(result.getElapsedMs());
        return Result.ok(vo);
    }
}
