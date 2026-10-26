package com.zufe.procurement.m6.price.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m6.price.dto.StatQuery;
import com.zufe.procurement.m6.price.service.PriceStatService;
import com.zufe.procurement.m6.price.vo.PriceStatVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成交价统计接口（M6-1-2、M6-1-3）。
 * 查询只读快照（E 域约束）；刷新是运营动作，挂 m6:param:config。
 */
@RestController
@RequestMapping("/api/m6/stats")
public class PriceStatController {

    private final PriceStatService priceStatService;

    public PriceStatController(PriceStatService priceStatService) {
        this.priceStatService = priceStatService;
    }

    /** 成交统计查询：均价 + 最近成交 + 当前参考价对比（R6 分离展示） */
    @GetMapping
    @RequirePermission("m6:stat:view")
    public Result<List<PriceStatVO>> list(StatQuery query) {
        return Result.ok(priceStatService.list(query));
    }

    /** 刷新统计快照：按 sys_param 窗口口径重算并覆盖快照（无样本物料跳过） */
    @PostMapping("/refresh")
    @RequirePermission("m6:param:config")
    public Result<Integer> refresh(@RequestParam(required = false) Long materialId) {
        return Result.ok(priceStatService.refresh(materialId));
    }
}
