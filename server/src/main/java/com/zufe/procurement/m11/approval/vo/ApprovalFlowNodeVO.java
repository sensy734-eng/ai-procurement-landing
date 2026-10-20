package com.zufe.procurement.m11.approval.vo;

import lombok.Data;

import java.util.List;

/**
 * 审批流程节点展示对象，功能点 M11-5-1、M11-5-2。
 * 一个节点对应一个 VO，approvers 为该节点下配置的审批人，
 * approvers 多于一个时按 signType 解释为或签或会签。
 */
@Data
public class ApprovalFlowNodeVO {

    private Integer nodeSeq;

    private String nodeName;

    /** 1 或签、2 会签 */
    private Integer signType;

    private String remark;

    private List<ApproverVO> approvers;

    @Data
    public static class ApproverVO {
        /** 1 按角色、2 按具体用户 */
        private Integer approverType;
        private Long roleId;
        private Long userId;
        private String displayName;
    }
}
