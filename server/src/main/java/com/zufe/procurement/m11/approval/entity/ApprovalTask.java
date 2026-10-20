package com.zufe.procurement.m11.approval.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务，对应 approval_task，功能点 M11-5-4、M11-5-5、M2-3-7、M3-3-7。
 * 一个审批节点按审批人配置生成一条或多条任务；同一节点多人时：
 * 或签（sign_type=1）任一通过即推进，会签（sign_type=2）全部通过才推进、
 * 任一驳回则整单驳回（M11-5-3）。
 * 任务状态：1 待处理、2 已通过、3 已驳回、4 已失效（撤回、按行退回重启、
 * 他签通过导致的失效）。审批历史由本表按 node_seq 呈现，不另建历史表。
 * 注意：本表无 updated_by 列（D 域表同一约定），勿按 platform 域习惯建模。
 */
@Data
@TableName("approval_task")
public class ApprovalTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号，全系统唯一，TASK-{yyyyMMdd}-{seq:4} */
    private String taskNo;

    /** 1 采购申请、2 采购订单 */
    private Integer billType;

    private Long billId;

    /** 单据号，冗余，单据归档后仍可读 */
    private String billNo;

    private Integer nodeSeq;

    /** 节点名称，冗余 */
    private String nodeName;

    private Long approverId;

    /** 1 或签、2 会签，生成时从节点配置快照 */
    private Integer signType;

    private Integer taskStatus;

    /** 审批意见，驳回时必填 */
    private String opinion;

    private LocalDateTime handleTime;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
