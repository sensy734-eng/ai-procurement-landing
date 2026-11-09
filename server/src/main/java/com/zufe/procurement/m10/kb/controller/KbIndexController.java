package com.zufe.procurement.m10.kb.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m10.kb.service.KbIndexService;
import com.zufe.procurement.m10.kb.service.KbIndexService.IndexBuildResult;
import com.zufe.procurement.m10.kb.vo.IndexBuildVO;
import com.zufe.procurement.security.RequirePermission;
import com.zufe.procurement.m10.kb.semantic.SemanticIndexService;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final SemanticIndexService semanticIndex;

    public KbIndexController(KbIndexService indexService, SemanticIndexService semanticIndex) {
        this.indexService = indexService;
        this.semanticIndex = semanticIndex;
    }

    /** 单文档索引增量重建 */
    @PostMapping("/documents/{docId}/index/rebuild")
    @RequirePermission("m10:index:rebuild")
    public Result<IndexBuildVO> rebuildDoc(@PathVariable Long docId, @RequestParam(defaultValue = "hash") String target) {
        checkTarget(target);
        return Result.ok(toVO("semantic".equals(target) ? semanticIndex.rebuild(docId) : indexService.rebuildDoc(docId)));
    }

    /** 索引一键重建（全量，记录耗时） */
    @PostMapping("/index/rebuild")
    @RequirePermission("m10:index:rebuild")
    public Result<IndexBuildVO> rebuildAll(@RequestParam(defaultValue = "hash") String target) {
        checkTarget(target);
        return Result.ok(toVO("semantic".equals(target) ? semanticIndex.rebuild(null) : indexService.rebuildAll()));
    }

    private void checkTarget(String target) {
        if (!"hash".equals(target) && !"semantic".equals(target)) {
            throw new com.zufe.procurement.common.BizException(com.zufe.procurement.common.ResultCode.INVALID_PARAMETER,
                    "target 必须为 hash 或 semantic");
        }
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
