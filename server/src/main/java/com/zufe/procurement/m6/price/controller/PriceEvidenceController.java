package com.zufe.procurement.m6.price.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m6.price.service.PriceEvidenceService;
import com.zufe.procurement.m6.price.vo.EvidenceVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批证据面板接口（M6-4-1 / M6-4-2）。
 * 给审批人一屏展示：单价、下单时参考价、偏离率/等级、历史成交与来源单号。
 */
@RestController
@RequestMapping("/api/m6/evidence")
public class PriceEvidenceController {

    private final PriceEvidenceService priceEvidenceService;

    public PriceEvidenceController(PriceEvidenceService priceEvidenceService) {
        this.priceEvidenceService = priceEvidenceService;
    }

    /** 订单价格证据：明细行级参考价/偏离/历史成交/来源单号 */
    @GetMapping("/order/{orderId}")
    @RequirePermission("m6:evidence:view")
    public Result<EvidenceVO> orderEvidence(@PathVariable Long orderId) {
        return Result.ok(priceEvidenceService.orderEvidence(orderId));
    }
}
