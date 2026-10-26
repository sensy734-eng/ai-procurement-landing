package com.zufe.procurement.m6.price.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m6.price.dto.PriceReferenceQuery;
import com.zufe.procurement.m6.price.dto.PriceReferenceSaveRequest;
import com.zufe.procurement.m6.price.service.PriceReferenceService;
import com.zufe.procurement.m6.price.vo.PriceReferenceVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 价格参考接口（M6-1-1）。
 * 版本策略：改价格/生效日 = 新版本（旧版留痕）；改备注/停用 = 原地更新。
 */
@RestController
@RequestMapping("/api/m6/references")
public class PriceReferenceController {

    private final PriceReferenceService priceReferenceService;

    public PriceReferenceController(PriceReferenceService priceReferenceService) {
        this.priceReferenceService = priceReferenceService;
    }

    /** 参考价分页查询 */
    @GetMapping
    @RequirePermission("m6:price:list")
    public Result<com.zufe.procurement.common.PageResult<PriceReferenceVO>> page(PriceReferenceQuery query) {
        PriceReferenceService.PageResult page = priceReferenceService.page(query);
        return Result.ok(com.zufe.procurement.common.PageResult.of(page.getRecords(), page.getTotal(),
                query.getPageNo(), query.getPageSize()));
    }

    /** 详情（含版本历史） */
    @GetMapping("/{id}")
    @RequirePermission("m6:price:view")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(priceReferenceService.detail(id));
    }

    /** 某物料的版本历史 */
    @GetMapping("/versions/{materialId}")
    @RequirePermission("m6:price:view")
    public Result<List<PriceReferenceVO>> versions(@PathVariable Long materialId) {
        return Result.ok(priceReferenceService.versions(materialId));
    }

    /** 新建参考价（生成新版本） */
    @PostMapping
    @RequirePermission("m6:price:create")
    public Result<Long> create(@RequestBody PriceReferenceSaveRequest request) {
        return Result.ok(priceReferenceService.create(request));
    }

    /** 更新：改价/改生效日生成新版本，改备注/停用原地更新 */
    @PutMapping
    @RequirePermission("m6:price:update")
    public Result<Long> update(@RequestBody PriceReferenceSaveRequest request) {
        return Result.ok(priceReferenceService.update(request));
    }
}
