package com.zufe.procurement.m11.approval.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.approval.dto.ApprovalFlowSaveRequest;
import com.zufe.procurement.m11.approval.service.ApprovalFlowService;
import com.zufe.procurement.m11.approval.vo.ApprovalFlowNodeVO;
import com.zufe.procurement.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审批流程配置接口，功能点 M11-5-1 流程配置与 M11-5-2 审批人指定。
 * 按单据类型整体保存，节点顺序固定，不做金额分支与发起人自选。
 */
@RestController
@RequestMapping("/api/m11/approval-flows")
public class ApprovalFlowController {

    private final ApprovalFlowService approvalFlowService;

    public ApprovalFlowController(ApprovalFlowService approvalFlowService) {
        this.approvalFlowService = approvalFlowService;
    }

    /** M11-5-1 查询某单据类型的节点配置 */
    @GetMapping("/{billType}")
    @RequirePermission("m11:approval_node:list")
    public Result<List<ApprovalFlowNodeVO>> list(@PathVariable Integer billType) {
        return Result.ok(approvalFlowService.listByBillType(billType));
    }

    /** M11-5-2 审批人候选，含角色与用户 */
    @GetMapping("/options")
    @RequirePermission("m11:approval_node:list")
    public Result<Map<String, Object>> options() {
        return Result.ok(approvalFlowService.approverOptions());
    }

    /** M11-5-1、M11-5-2 整体保存节点配置 */
    @PutMapping("/{billType}")
    @RequirePermission("m11:approval_node:update")
    public Result<Void> save(@PathVariable Integer billType,
                             @Valid @RequestBody ApprovalFlowSaveRequest request) {
        approvalFlowService.save(billType, request);
        return Result.ok();
    }
}
