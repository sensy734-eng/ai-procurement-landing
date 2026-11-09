package com.zufe.procurement.m7.assistant.task;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.*;
import static com.zufe.procurement.m7.assistant.task.TaskContracts.*;

/** Explicit read-only AI tasks. No endpoint creates, saves, submits or approves a business document. */
@RestController
@RequestMapping("/api/assistant/tasks")
public class AiTaskController {
    private final OrderReviewService reviews;
    private final RequestDraftService drafts;
    public AiTaskController(OrderReviewService reviews, RequestDraftService drafts) { this.reviews=reviews;this.drafts=drafts; }

    /** 单张订单审查：受控证据、规则检查和可降级的 AI 解读。 */
    @PostMapping("/orders/{id}/review")
    @RequirePermission({"m7:chat:query","m9:data:query","m3:order:view"})
    public Result<ReviewResult> review(@PathVariable Long id, @RequestBody ReviewRequest request) { return Result.ok(reviews.review(id,request)); }

    /** 采购需求起草：只提取候选字段，不创建业务单据。 */
    @PostMapping("/request-draft")
    @RequirePermission({"m7:chat:query","m2:request:create","m2:request_item:create","m1:material:list"})
    public Result<DraftResult> draft(@RequestBody DraftRequest request) { return Result.ok(drafts.draft(request)); }

    /** 人工确认候选字段后重新核对权限与档案，不保存采购申请。 */
    @PostMapping("/request-draft/validate")
    @RequirePermission({"m7:chat:query","m2:request:create","m2:request_item:create","m1:material:list"})
    public Result<DraftValidation> validate(@RequestBody DraftValidationRequest request) { return Result.ok(drafts.validate(request)); }
}
