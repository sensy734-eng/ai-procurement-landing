package com.zufe.procurement.m6.price.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m6.price.dto.DeviationQuery;
import com.zufe.procurement.m6.price.dto.DeviationRecalcRequest;
import com.zufe.procurement.m6.price.service.PriceDeviationService;
import com.zufe.procurement.m6.price.vo.DeviationOrderVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 价格偏离关注接口（M3-6 / M6-3）。
 * 关注清单只返回 deviation_flag > 0 的订单；重算供参考价变更后同步判定。
 */
@RestController
@RequestMapping("/api/m6/deviations")
public class PriceDeviationController {

    private final PriceDeviationService priceDeviationService;

    public PriceDeviationController(PriceDeviationService priceDeviationService) {
        this.priceDeviationService = priceDeviationService;
    }

    /** 偏离关注清单：可按等级（1 关注 / 2 显著）/物料/订单号过滤 */
    @GetMapping
    @RequirePermission("m6:stat:view")
    public Result<com.zufe.procurement.common.PageResult<DeviationOrderVO>> list(DeviationQuery query) {
        PriceDeviationService.DeviationPage page = priceDeviationService.pageDeviations(query);
        return Result.ok(com.zufe.procurement.common.PageResult.of(page.getRecords(), page.getTotal(),
                query.getPageNo(), query.getPageSize()));
    }

    /** 偏离汇总：关注/显著单数、按等级分布（P16 页面统计卡） */
    @GetMapping("/summary")
    @RequirePermission("m6:stat:view")
    public Result<Map<String, Object>> summary() {
        return Result.ok(priceDeviationService.summary());
    }

    /** 重算偏离：参考价变更后同步订单判定（挂 price:update，与参考价维护同角色） */
    @PostMapping("/recalc")
    @RequirePermission("m6:price:update")
    public Result<Integer> recalc(@RequestBody(required = false) DeviationRecalcRequest request) {
        return Result.ok(priceDeviationService.recalc(request));
    }
}
