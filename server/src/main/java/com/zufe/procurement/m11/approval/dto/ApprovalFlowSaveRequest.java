package com.zufe.procurement.m11.approval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 审批流程配置保存请求，功能点 M11-5-1、M11-5-2。
 * 按单据类型整体提交节点列表，节点序号必须从 1 连续，
 * 每个节点至少一个审批人，保证提交时不会生成无人可审的节点。
 */
@Data
public class ApprovalFlowSaveRequest {

    @NotEmpty(message = "至少配置一个审批节点")
    @Size(max = 10, message = "审批节点不超过 10 个")
    @Valid
    private List<NodeItem> nodes;

    @Data
    public static class NodeItem {

        @NotNull(message = "节点序号不能为空")
        @Min(value = 1, message = "节点序号从 1 开始")
        private Integer nodeSeq;

        @NotNull(message = "节点名称不能为空")
        @Size(max = 50, message = "节点名称不超过 50 字")
        private String nodeName;

        /** 1 或签、2 会签 */
        @NotNull(message = "会签方式不能为空")
        @Min(value = 1, message = "会签方式只能为 1 或 2")
        @Max(value = 2, message = "会签方式只能为 1 或 2")
        private Integer signType;

        @NotEmpty(message = "节点至少指定一个审批人")
        @Valid
        private List<ApproverItem> approvers;
    }

    @Data
    public static class ApproverItem {

        /** 1 按角色、2 按具体用户 */
        @NotNull(message = "审批人指定方式不能为空")
        @Min(value = 1, message = "审批人指定方式只能为 1 或 2")
        @Max(value = 2, message = "审批人指定方式只能为 1 或 2")
        private Integer approverType;

        /** approverType 为 1 时必填 */
        private Long roleId;

        /** approverType 为 2 时必填 */
        private Long userId;
    }
}
