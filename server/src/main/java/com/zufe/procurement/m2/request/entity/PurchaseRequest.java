package com.zufe.procurement.m2.request.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购申请单主表，对应 purchase_request，功能点 M2-1、M2-3。
 * 三状态约定（R1）：本表只设单据生命周期状态与审批结果状态，不产生履约状态。
 *   lifecycle_status 1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废
 *   audit_status     1 未审核、2 通过、3 驳回
 * 驳回时 lifecycle_status 回落为 1，由 audit_status=3 表达"已驳回"，
 * 因此"草稿 / 已驳回可编辑"统一表现为 lifecycle_status=1。
 */
@Data
@TableName("purchase_request")
public class PurchaseRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请单号，全系统唯一，按 sys_param 编号规则生成 */
    private String requestNo;

    private LocalDate requestDate;

    /** 申请部门，关联 dict_item.id（字典类型 dept），数据范围按此字段过滤 */
    private Long deptId;

    private Long applicantId;

    /** 需求日期，默认取明细行最早需求日期，服务层计算 */
    private LocalDate requireDate;

    /** 预估金额合计，明细行 est_amount 汇总，服务层计算 */
    private BigDecimal totalAmount;

    private Integer lifecycleStatus;

    private Integer auditStatus;

    /** 关闭原因（M2-3-5 整单关闭） */
    private String closeReason;

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
