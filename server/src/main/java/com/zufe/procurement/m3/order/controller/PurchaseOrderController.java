package com.zufe.procurement.m3.order.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m2.request.vo.OwnerOptionVO;
import com.zufe.procurement.m2.request.vo.RequestVO;
import com.zufe.procurement.m2.request.vo.TransferResultVO;
import com.zufe.procurement.m3.order.dto.DeliveryRegisterRequest;
import com.zufe.procurement.m3.order.dto.FromRequestRequest;
import com.zufe.procurement.m3.order.dto.OrderChangeRequest;
import com.zufe.procurement.m3.order.dto.OrderCloseRequest;
import com.zufe.procurement.m3.order.dto.OrderItemCloseRequest;
import com.zufe.procurement.m3.order.dto.OrderItemSaveRequest;
import com.zufe.procurement.m3.order.dto.OrderQuery;
import com.zufe.procurement.m3.order.dto.OrderRejectRequest;
import com.zufe.procurement.m3.order.dto.OrderSaveRequest;
import com.zufe.procurement.m3.order.service.PurchaseOrderService;
import com.zufe.procurement.m3.order.vo.DeliveryRecordVO;
import com.zufe.procurement.m3.order.vo.DeliverySummaryVO;
import com.zufe.procurement.m3.order.vo.OrderChangeLogVO;
import com.zufe.procurement.m3.order.vo.OrderItemVO;
import com.zufe.procurement.m3.order.vo.OrderTodoVO;
import com.zufe.procurement.m3.order.vo.OrderVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单接口。功能点：M3-1 订单维护（列表/新建/由申请单生成/编辑/作废/导出）、
 * M3-2 明细行（增删改/按行关闭/归属对象）、M3-3 订单审批（提交/通过/驳回/撤回/
 * 反审/关闭）、M3-4 订单变更（登记/历史）、M3-5 履约跟踪（交付登记/流水/汇总/
 * 超期筛选）、M3-1-1 订单溯源。M3-6 价格偏离随轮次 8 的 M6 实现。
 * 每个接口过认证与动作级权限校验；数据范围由服务层注入部门条件。
 *
 * 说明：订单不提供删除接口——清单 M3 无删除功能点，错单走作废（M3-1-5），
 * 与单据留痕口径一致。
 */
@RestController
@RequestMapping("/api/m3/orders")
public class PurchaseOrderController {

    private final PurchaseOrderService orderService;

    public PurchaseOrderController(PurchaseOrderService orderService) {
        this.orderService = orderService;
    }

    // ================= M3-1-1 列表与检索 =================

    @GetMapping("/page")
    @RequirePermission("m3:order:list")
    public Result<PageResult<OrderVO>> page(OrderQuery query) {
        return Result.ok(orderService.page(query));
    }

    /** M3-1-1 详情：主表 + 明细 + 审批历史 + 变更历史 + 交付流水 */
    @GetMapping("/{id}")
    @RequirePermission("m3:order:view")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    // ================= M3-1-2 至 M3-1-6 维护 =================

    @PostMapping
    @RequirePermission("m3:order:create")
    public Result<Long> create(@RequestBody OrderSaveRequest request) {
        return Result.ok(orderService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("m3:order:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody OrderSaveRequest request) {
        orderService.update(id, request);
        return Result.ok();
    }

    /** M3-1-3 由申请单生成订单：带入已审核申请单的明细与归属对象 */
    @PostMapping("/from-request")
    @RequirePermission("m3:order:create")
    public Result<TransferResultVO> fromRequest(@RequestBody FromRequestRequest request) {
        return Result.ok(orderService.createFromRequest(request));
    }

    /** M3-1-5 作废订单：未发生到货的订单可作废 */
    @PostMapping("/{id}/void")
    @RequirePermission("m3:order:void")
    public Result<Void> voidOrder(@PathVariable Long id) {
        orderService.voidOrder(id);
        return Result.ok();
    }

    /** M3-1-6 打印 / 导出：CSV 平表视图，一行主表加多行明细 */
    @GetMapping("/{id}/export")
    @RequirePermission("m3:order:export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id) {
        byte[] csv = orderService.exportCsv(id);
        String fileName = "采购订单.csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    // ================= M3-2 明细行 =================

    @PostMapping("/{id}/items")
    @RequirePermission("m3:order_item:create")
    public Result<Long> addItem(@PathVariable Long id, @RequestBody OrderItemSaveRequest request) {
        return Result.ok(orderService.addItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    @RequirePermission("m3:order_item:update")
    public Result<Void> updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                   @RequestBody OrderItemSaveRequest request) {
        orderService.updateItem(id, itemId, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @RequirePermission("m3:order_item:delete")
    public Result<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        orderService.deleteItem(id, itemId);
        return Result.ok();
    }

    /** M3-2-4 按行关闭：剩余数量不再要求交付 */
    @PostMapping("/{id}/items/close")
    @RequirePermission("m3:order_item:close")
    public Result<Void> closeItems(@PathVariable Long id, @RequestBody OrderItemCloseRequest request) {
        orderService.closeItems(id, request);
        return Result.ok();
    }

    /** M3-2-5 归属对象下拉数据源，与 M2 共用同一份档案 */
    @GetMapping("/owner-options")
    @RequirePermission("m3:order_item:owner")
    public Result<OwnerOptionVO> ownerOptions() {
        return Result.ok(orderService.ownerOptions());
    }

    // ================= M3-4 订单变更 =================

    /** M3-4-1 变更登记：已审核订单的数量/单价/交期/地点/结算方式变更留痕 */
    @PostMapping("/{id}/change")
    @RequirePermission("m3:order_change:create")
    public Result<Void> change(@PathVariable Long id, @RequestBody OrderChangeRequest request) {
        orderService.change(id, request);
        return Result.ok();
    }

    /** M3-4-2 变更历史查看：时间线 */
    @GetMapping("/{id}/change-logs")
    @RequirePermission("m3:order_change:list")
    public Result<List<OrderChangeLogVO>> changeLogs(@PathVariable Long id) {
        return Result.ok(orderService.changeLogs(id));
    }

    // ================= M3-3 订单审批 =================

    @PostMapping("/{id}/submit")
    @RequirePermission("m3:order:submit")
    public Result<Void> submit(@PathVariable Long id) {
        orderService.submit(id);
        return Result.ok();
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("m3:order:approve")
    public Result<Void> approve(@PathVariable Long id) {
        orderService.approve(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("m3:order:reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody OrderRejectRequest request) {
        orderService.reject(id, request);
        return Result.ok();
    }

    @PostMapping("/{id}/withdraw")
    @RequirePermission("m3:order:withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        orderService.withdraw(id);
        return Result.ok();
    }

    /** M3-3-5 反审：仅系统管理员、已审核且未到货；需二次确认与审计 */
    @PostMapping("/{id}/reaudit")
    @RequirePermission("m3:order:reaudit")
    public Result<Void> reaudit(@PathVariable Long id,
                                @RequestParam(required = false) String reason) {
        orderService.reaudit(id, reason);
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    @RequirePermission("m3:order:close")
    public Result<Void> close(@PathVariable Long id, @RequestBody OrderCloseRequest request) {
        orderService.close(id, request);
        return Result.ok();
    }

    // ================= M3-1-1 订单溯源 =================

    /** 来源申请追溯：返回来源申请单详情，手工创建订单返回 NoData */
    @GetMapping("/{id}/trace")
    @RequirePermission("m3:order:trace")
    public Result<RequestVO> trace(@PathVariable Long id) {
        return Result.ok(orderService.trace(id));
    }

    // ================= M3-5 履约跟踪 =================

    /** M3-5-1 交付进度登记 */
    @PostMapping("/{id}/deliveries")
    @RequirePermission("m3:delivery:register")
    public Result<Long> registerDelivery(@PathVariable Long id,
                                         @RequestBody DeliveryRegisterRequest request) {
        return Result.ok(orderService.registerDelivery(id, request));
    }

    /** M3-5-1 交付流水查询 */
    @GetMapping("/{id}/deliveries")
    @RequirePermission("m3:delivery:list")
    public Result<List<DeliveryRecordVO>> deliveryRecords(@PathVariable Long id) {
        return Result.ok(orderService.deliveryRecords(id));
    }

    /** M3-5-5 交付汇总视图：按供应商汇总已交付/未交付数量与金额 */
    @GetMapping("/delivery-summary")
    @RequirePermission("m3:delivery:summary")
    public Result<List<DeliverySummaryVO>> deliverySummary(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Long supplierId) {
        return Result.ok(orderService.deliverySummary(startDate, endDate, supplierId));
    }

    // ================= M3-3-2 订单待办（P9） =================

    @GetMapping("/todo")
    @RequirePermission("m3:order:todo")
    public Result<PageResult<OrderTodoVO>> todo(@RequestParam(defaultValue = "1") long pageNo,
                                                @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.todoPage(pageNo, pageSize));
    }
}
