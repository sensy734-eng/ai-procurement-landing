package com.zufe.procurement.m2.request.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批历史条目，功能点 M2-3-6。
 * 由 approval_task 按 node_seq 呈现，不另建历史表；
 * 已失效任务（4）一并返回，保证审批轨迹完整可追溯。
 */
@Data
public class ApprovalHistoryVO {

    private Long taskId;

    private String taskNo;

    private Integer nodeSeq;

    private String nodeName;

    private Long approverId;

    private String approverName;

    /** 1 或签、2 会签，生成时从节点配置快照 */
    private Integer signType;

    private String signTypeName;

    /** 任务状态：1 待处理、2 已通过、3 已驳回、4 已失效 */
    private Integer taskStatus;

    private String taskStatusName;

    private String opinion;

    private LocalDateTime handleTime;
}
