package com.zufe.procurement.m4.arrival.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m4.arrival.dto.ArrivalQuery;
import com.zufe.procurement.m4.arrival.dto.ArrivalSaveRequest;
import com.zufe.procurement.m4.arrival.dto.ArrivalStatQuery;
import com.zufe.procurement.m4.arrival.dto.ConcessionRequest;
import com.zufe.procurement.m4.arrival.dto.InboundGenerateRequest;
import com.zufe.procurement.m4.arrival.service.ArrivalInspectionService;
import com.zufe.procurement.m4.arrival.vo.ArrivalOrderVO;
import com.zufe.procurement.m4.arrival.vo.ArrivalStatVO;
import com.zufe.procurement.m4.arrival.vo.ArrivalVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 到货验收接口。功能点：M4-1 到货登记（列表选单/明细/新建/编辑/删除草稿）、
 * M4-2 验收登记与判定（提交/让步接收）、M4-3 生成采购入库单（草稿）、
 * M4-4 验收记录查询（列表/详情/统计）。
 * 每个接口过认证与动作级权限校验；列表按仓库维度注入数据范围，
 * 详情与动作由权限点把关，统计为跨仓库质量视角不注入范围。
 *
 * 说明：不提供作废端点——清单 M4 无作废功能点；已验收单不可删除，
 * 草稿删除挂 create 权限（放弃未提交登记），见服务层注释。
 */
@RestController
@RequestMapping("/api/m4/inspections")
public class ArrivalInspectionController {

    private final ArrivalInspectionService inspectionService;

    public ArrivalInspectionController(ArrivalInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    // ================= M4-4-1 验收记录查询 =================

    @GetMapping("/page")
    @RequirePermission("m4:inspection:list")
    public Result<PageResult<ArrivalVO>> page(ArrivalQuery query) {
        return Result.ok(inspectionService.page(query));
    }

    /** M4-4-2 验收明细查看：主表 + 明细行（带配额现算）+ 已生成入库单草稿 */
    @GetMapping("/{id}")
    @RequirePermission("m4:inspection:view")
    public Result<ArrivalVO> detail(@PathVariable Long id) {
        return Result.ok(inspectionService.detail(id));
    }

    // ================= M4-4-3 验收结果统计 =================

    @GetMapping("/stat")
    @RequirePermission("m4:inspection:stat")
    public Result<List<ArrivalStatVO>> stat(ArrivalStatQuery query) {
        return Result.ok(inspectionService.stat(query));
    }

    // ================= M4-1 到货登记 =================

    /** M4-1-1 选单下拉：可登记的已审核订单（含未到货明细与剩余到货量） */
    @GetMapping("/arrivable-orders")
    @RequirePermission("m4:inspection:create")
    public Result<List<ArrivalOrderVO>> arrivableOrders() {
        return Result.ok(inspectionService.arrivableOrders());
    }

    /** M4-1-1 单个订单的未到货明细 */
    @GetMapping("/arrivable-orders/{orderId}")
    @RequirePermission("m4:inspection:create")
    public Result<ArrivalOrderVO> orderArrivalItems(@PathVariable Long orderId) {
        return Result.ok(inspectionService.orderArrivalItems(orderId));
    }

    /** M4-1-1 至 M4-1-3 新建到货登记（草稿） */
    @PostMapping
    @RequirePermission("m4:inspection:create")
    public Result<Long> create(@RequestBody ArrivalSaveRequest request) {
        return Result.ok(inspectionService.create(request));
    }

    /** M4-1 编辑到货登记（仅草稿） */
    @PutMapping("/{id}")
    @RequirePermission("m4:inspection:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody ArrivalSaveRequest request) {
        inspectionService.update(id, request);
        return Result.ok();
    }

    /** 删除草稿验收单（挂 create 权限：放弃未提交登记） */
    @DeleteMapping("/{id}")
    @RequirePermission("m4:inspection:create")
    public Result<Void> delete(@PathVariable Long id) {
        inspectionService.delete(id);
        return Result.ok();
    }

    // ================= M4-2 验收登记与判定 =================

    /** M4-2-5 验收单提交：守恒校验后进入已验收态 */
    @PostMapping("/{id}/submit")
    @RequirePermission("m4:inspection:submit")
    public Result<Void> submit(@PathVariable Long id) {
        inspectionService.submit(id);
        return Result.ok();
    }

    /** M4-2-4 让步接收标记（整单动作，归采购主管） */
    @PostMapping("/{id}/concession")
    @RequirePermission("m4:inspection:concession")
    public Result<Void> concession(@PathVariable Long id, @RequestBody ConcessionRequest request) {
        inspectionService.concession(id, request);
        return Result.ok();
    }

    // ================= M4-3 生成采购入库单 =================

    /** M4-3-1 至 M4-3-4 由已验收单生成入库单草稿，可多次生成（部分入库/多批次） */
    @PostMapping("/{id}/inbound")
    @RequirePermission("m4:inbound:generate")
    public Result<Long> generateInbound(@PathVariable Long id, @RequestBody InboundGenerateRequest request) {
        return Result.ok(inspectionService.generateInbound(id, request));
    }
}
