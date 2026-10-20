package com.zufe.procurement.m3.order.entity;

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
 * 采购订单主表，对应 purchase_order，功能点 M3-1、M3-3。
 * 轮次 4 仅由申请转订单生成草稿（M2-4），完整维护在轮次 5 实现。
 * 三状态约定（R1）：lifecycle 单据生命周期、audit 审批结果、fulfill 业务履约，
 * 三者独立；fulfill_status 由明细行重算，不由人工选择。
 */
@Data
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 来源申请单id，手工建单时为空 */
    private Long sourceRequestId;

    /** 来源申请单号，冗余 */
    private String sourceRequestNo;

    private Long supplierId;

    /** 采购员，数据范围按此人与部门过滤 */
    private Long buyerId;

    /** 采购部门，数据范围按部门过滤 */
    private Long deptId;

    private LocalDate orderDate;

    /** 整单交货期，明细行未设置时取此值 */
    private LocalDate deliveryDate;

    private String deliveryPlace;

    /** 结算方式：1 月结、2 货到付款、3 预付款 */
    private Integer settlementType;

    private Integer lifecycleStatus;

    private Integer auditStatus;

    private Integer fulfillStatus;

    private BigDecimal totalQty;

    private BigDecimal totalAmount;

    private BigDecimal noTaxAmount;

    private BigDecimal taxAmount;

    /** 整单偏离标记：0 无、1 关注、2 显著，取明细行最高等级，轮次 6 实现 */
    private Integer deviationFlag;

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
