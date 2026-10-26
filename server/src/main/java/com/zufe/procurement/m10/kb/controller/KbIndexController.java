package com.zufe.procurement.m10.kb.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m10.kb.service.KbIndexService;
import com.zufe.procurement.m10.kb.service.KbIndexService.IndexBuildResult;
import com.zufe.procurement.m10.kb.vo.IndexBuildVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 索引接口（M10-3-3 单文档增量重建、M10-3-4 一键全量重建）。
 */
@RestController
@RequestMapping("/api/m10")
public class KbIndexController {

    private final KbIndexService indexService;

    public KbIndexController(KbIndexService indexService) {
        this.indexService = indexService;
    }

    /** 单文档索引增量重建 */
    @PostMapping("/documents/{docId}/index/rebuild")
    @RequirePermission("m10:index:rebuild")
    public Result<IndexBuildVO> rebuildDoc(@PathVariable Long docId) {
        return Result.ok(toVO(indexService.rebuildDoc(docId)));
    }

    /** 索引一键重建（全量，记录耗时） */
    @PostMapping("/index/rebuild")
    @RequirePermission("m10:index:rebuild")
    public Result<IndexBuildVO> rebuildAll() {
        return Result.ok(toVO(indexService.rebuildAll()));
    }

    private static IndexBuildVO toVO(IndexBuildResult result) {
        IndexBuildVO vo = new IndexBuildVO();
        vo.setDocCount(result.getDocCount());
        vo.setChunkCount(result.getChunkCount());
        vo.setTokenCount(result.getTokenCount());
        vo.setElapsedMs(result.getElapsedMs());
        return vo;
    }
}
