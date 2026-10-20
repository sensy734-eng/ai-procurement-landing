package com.zufe.procurement.m2.request.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m2.request.dto.CloseRequest;
import com.zufe.procurement.m2.request.dto.RejectRequest;
import com.zufe.procurement.m2.request.dto.RequestItemSaveRequest;
import com.zufe.procurement.m2.request.dto.RequestQuery;
import com.zufe.procurement.m2.request.dto.RequestSaveRequest;
import com.zufe.procurement.m2.request.dto.ReturnItemsRequest;
import com.zufe.procurement.m2.request.dto.TransferRequest;
import com.zufe.procurement.m2.request.service.PurchaseRequestService;
import com.zufe.procurement.m2.request.service.RequestTransferService;
import com.zufe.procurement.m2.request.vo.OwnerOptionVO;
import com.zufe.procurement.m2.request.vo.RequestVO;
import com.zufe.procurement.m2.request.vo.TodoVO;
import com.zufe.procurement.m2.request.vo.TransferResultVO;
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

/**
 * 采购申请接口。功能点：M2-1-1 至 M2-1-6 申请单维护、M2-2-1 至 M2-2-5 明细行、
 * M2-3-1 至 M2-3-8 申请审批、M2-4-1 至 M2-4-2 申请转订单。
 * 每个接口过认证与动作级权限校验；数据范围由服务层注入部门条件。
 */
@RestController
@RequestMapping("/api/m2/requests")
public class PurchaseRequestController {

    private final PurchaseRequestService requestService;
    private final RequestTransferService transferService;

    public PurchaseRequestController(PurchaseRequestService requestService,
                                     RequestTransferService transferService) {
        this.requestService = requestService;
        this.transferService = transferService;
    }

    // ================= M2-1-1 列表与检索 =================

    @GetMapping("/page")
    @RequirePermission("m2:request:list")
    public Result<PageResult<RequestVO>> page(RequestQuery query) {
        return Result.ok(requestService.page(query));
    }

    /** M2-1-1 详情，含明细行与审批历史（M2-3-6） */
    @GetMapping("/{id}")
    @RequirePermission("m2:request:view")
    public Result<RequestVO> detail(@PathVariable Long id) {
        return Result.ok(requestService.detail(id));
    }

    // ================= M2-1-2 至 M2-1-6 维护 =================

    @PostMapping
    @RequirePermission("m2:request:create")
    public Result<Long> create(@RequestBody RequestSaveRequest request) {
        return Result.ok(requestService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("m2:request:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody RequestSaveRequest request) {
        requestService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("m2:request:delete")
    public Result<Void> delete(@PathVariable Long id) {
        requestService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/copy")
    @RequirePermission("m2:request:copy")
    public Result<Long> copy(@PathVariable Long id) {
        return Result.ok(requestService.copy(id));
    }

    /** M2-1-6 打印 / 导出：CSV 平表视图，一行主表加多行明细 */
    @GetMapping("/{id}/export")
    @RequirePermission("m2:request:export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id) {
        byte[] csv = requestService.exportCsv(id);
        String fileName = "采购申请单.csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    // ================= M2-2-1 至 M2-2-5 明细行 =================

    @PostMapping("/{id}/items")
    @RequirePermission("m2:request_item:create")
    public Result<Long> addItem(@PathVariable Long id, @RequestBody RequestItemSaveRequest request) {
        return Result.ok(requestService.addItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    @RequirePermission("m2:request_item:update")
    public Result<Void> updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                   @RequestBody RequestItemSaveRequest request) {
        requestService.updateItem(id, itemId, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @RequirePermission("m2:request_item:delete")
    public Result<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        requestService.deleteItem(id, itemId);
        return Result.ok();
    }

    /** M2-2-4 按行退回：单据拆分为通过部分与退回部分 */
    @PostMapping("/{id}/return-items")
    @RequirePermission("m2:request_item:return")
    public Result<Long> returnItems(@PathVariable Long id, @RequestBody ReturnItemsRequest request) {
        return Result.ok(requestService.returnItems(id, request));
    }

    /** M2-2-5 归属对象下拉数据源 */
    @GetMapping("/owner-options")
    @RequirePermission("m2:request_item:owner")
    public Result<OwnerOptionVO> ownerOptions() {
        return Result.ok(requestService.ownerOptions());
    }

    // ================= M2-3-1 至 M2-3-6 审批 =================

    @PostMapping("/{id}/submit")
    @RequirePermission("m2:request:submit")
    public Result<Void> submit(@PathVariable Long id) {
        requestService.submit(id);
        return Result.ok();
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("m2:request:approve")
    public Result<Void> approve(@PathVariable Long id) {
        requestService.approve(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("m2:request:reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        requestService.reject(id, request);
        return Result.ok();
    }

    @PostMapping("/{id}/withdraw")
    @RequirePermission("m2:request:withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        requestService.withdraw(id);
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    @RequirePermission("m2:request:close")
    public Result<Void> close(@PathVariable Long id, @RequestBody CloseRequest request) {
        requestService.close(id, request);
        return Result.ok();
    }

    // ================= M2-3-8 我的待办 =================

    @GetMapping("/todo")
    @RequirePermission("m2:request:todo")
    public Result<PageResult<TodoVO>> todo(@RequestParam(defaultValue = "1") long pageNo,
                                           @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(requestService.todoPage(pageNo, pageSize));
    }

    // ================= M2-4-1 / M2-4-2 申请转订单 =================

    @PostMapping("/transfer")
    @RequirePermission("m2:request:transfer")
    public Result<TransferResultVO> transfer(@RequestBody TransferRequest request) {
        return Result.ok(transferService.transfer(request));
    }
}
