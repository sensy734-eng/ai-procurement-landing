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
 * 审批流程节点配置，对应 approval_flow_node，功能点 M11-5-1、M11-5-2。
 * 同一 bill_type 加 node_seq 为一个节点，节点下可配置多行审批人，
 * 一行一个角色或一个具体用户，多行即多人，或签会签规则由 sign_type 决定。
 * 节点顺序固定，不做金额区间条件分支，不做发起人自选与转办。
 */
@Data
@TableName("approval_flow_node")
public class ApprovalFlowNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1 采购申请、2 采购订单 */
    private Integer billType;

    private Integer nodeSeq;

    private String nodeName;

    /** 1 按角色、2 按具体用户 */
    private Integer approverType;

    private Long roleId;

    private Long userId;

    /** 1 或签、2 会签 */
    private Integer signType;

    private Integer status;

    private String remark;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
