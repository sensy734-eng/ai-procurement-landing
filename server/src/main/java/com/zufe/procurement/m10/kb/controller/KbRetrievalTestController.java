package com.zufe.procurement.m10.kb.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m10.kb.dto.RetrievalTestRequest;
import com.zufe.procurement.m10.kb.dto.TestStatQuery;
import com.zufe.procurement.m10.kb.service.KbRetrievalTestService;
import com.zufe.procurement.m10.kb.vo.RetrievalTestVO;
import com.zufe.procurement.m10.kb.vo.TestStatVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索测试接口（M10-5-1 检索测试台、M10-5-2 命中率统计）。
 */
@RestController
@RequestMapping("/api/m10/retrieval-tests")
public class KbRetrievalTestController {

    private final KbRetrievalTestService testService;
    private final com.zufe.procurement.m10.kb.service.RetrievalManifestService manifestService;

    public KbRetrievalTestController(KbRetrievalTestService testService,
            com.zufe.procurement.m10.kb.service.RetrievalManifestService manifestService) {
        this.testService = testService;
        this.manifestService = manifestService;
    }

    /** 完整在线语料与非敏感配置快照，用于实验前后守卫。 */
    @GetMapping("/manifest")
    @RequirePermission("m10:test:run")
    public Result<java.util.Map<String, Object>> manifest() {
        return Result.ok(manifestService.manifest());
    }

    /** M10-5-1 跑一次测试问句：稀疏与向量两路 Top-K 并列展示并做命中判定 */
    @PostMapping
    @RequirePermission("m10:test:run")
    public Result<RetrievalTestVO> run(@jakarta.validation.Valid @RequestBody RetrievalTestRequest request) {
        return Result.ok(testService.run(request));
    }

    /** M10-5-2 命中率统计：按测试批次或时间范围汇总两路命中率 */
    @GetMapping("/stats")
    @RequirePermission("m10:test:stat")
    public Result<TestStatVO> stats(TestStatQuery query) {
        return Result.ok(testService.stats(query));
    }
}
