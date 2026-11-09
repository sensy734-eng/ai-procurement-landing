package com.zufe.procurement.m5.inbound.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m5.inbound.dto.InboundQuery;
import com.zufe.procurement.m5.inbound.dto.InboundSaveRequest;
import com.zufe.procurement.m5.inbound.dto.InboundVoidRequest;
import com.zufe.procurement.m5.inbound.service.InboundOrderService;
import com.zufe.procurement.m5.inbound.vo.InboundVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购入库单接口。功能点：M5-1 入库单维护与确认作废溯源
 * （M5-1-1 至 M5-1-5）、M5-2-1 批次号规则生成（编辑明细时登记）。
 * 每个接口过认证与动作级权限校验；确认（M5-1-3）是库存唯一写入点，
 * 作废（M5-1-4）归采购主管。
 */
@RestController
@RequestMapping("/api/m5/inbounds")
public class InboundOrderController {

    private final InboundOrderService inboundOrderService;

    public InboundOrderController(InboundOrderService inboundOrderService) {
        this.inboundOrderService = inboundOrderService;
    }

    /** M5-1-2 入库单列表：单号/仓库/日期/状态筛选，按数据范围过滤 */
    @GetMapping("/page")
    @RequirePermission("m5:inbound:list")
    public Result<com.zufe.procurement.common.PageResult<InboundVO>> page(InboundQuery query) {
        return Result.ok(inboundOrderService.page(query));
    }

    /** M5-1-2 详情：主表 + 明细 + 来源链 + 库存流水摘要 */
    @GetMapping("/{id}")
    @RequirePermission("m5:inbound:view")
    public Result<InboundVO> detail(@PathVariable Long id) {
        return Result.ok(inboundOrderService.detail(id));
    }

    /** M5-1-1 手工新建入库单（草稿）：全部带订单行=关联单，全部不带=手工单 */
    @PostMapping
    @RequirePermission("m5:inbound:create")
    public Result<Long> create(@RequestBody InboundSaveRequest request) {
        return Result.ok(inboundOrderService.create(request));
    }

    /** M5-1-1 编辑入库单：仅草稿可编辑，明细整体替换，批次/库位在此登记 */
    @PutMapping("/{id}")
    @RequirePermission("m5:inbound:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody InboundSaveRequest request) {
        inboundOrderService.update(id, request);
        return Result.ok();
    }

    /**
     * M5-1-3 确认入库：库存唯一写入点。同事务写余额、流水、
     * 回写订单已交付数量；重复确认被状态机闸门拦截（M5-5-3）。
     */
    @PostMapping("/{id}/confirm")
    @RequirePermission("m5:inbound:confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        inboundOrderService.confirm(id);
        return Result.ok();
    }

    /** 删除入库草稿：复用草稿维护权限，事务内锁定状态。 */
    @DeleteMapping("/{id}")
    @RequirePermission("m5:inbound:update")
    public Result<Void> delete(@PathVariable Long id) {
        inboundOrderService.deleteDraft(id);
        return Result.ok();
    }

    /**
     * M5-1-4 作废入库单：归采购主管，需二次确认与原因；
     * 冲减库存并写冲减流水，回退订单已交付数量。
     */
    @PostMapping("/{id}/void")
    @RequirePermission("m5:inbound:void")
    public Result<Void> voidOrder(@PathVariable Long id, @RequestBody InboundVoidRequest request) {
        inboundOrderService.voidOrder(id, request);
        return Result.ok();
    }

    /** M5-1-5 入库单溯源：入库单 → 验收单 → 订单 → 申请单 */
    @GetMapping("/{id}/trace")
    @RequirePermission("m5:inbound:trace")
    public Result<InboundVO.TraceChain> trace(@PathVariable Long id) {
        return Result.ok(inboundOrderService.trace(id));
    }

    /**
     * M5-2-1 批次号规则生成：B + yyyyMMdd + 3 位当日流水。
     * 挂在编辑权限上——批次登记是明细行维护动作。
     */
    @GetMapping("/next-batch-no")
    @RequirePermission("m5:inbound:update")
    public Result<String> nextBatchNo() {
        return Result.ok(inboundOrderService.nextBatchNo());
    }
}
