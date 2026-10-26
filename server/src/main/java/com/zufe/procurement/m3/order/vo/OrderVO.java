package com.zufe.procurement.m3.order.vo;

import com.zufe.procurement.m2.request.vo.ApprovalHistoryVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购订单视图，功能点 M3-1-1 列表、M3-1-1 详情。
 * 三状态分三个独立字段（R1）；履约状态由明细行重算，不由人工选择；
 * 超期天数为服务端按当前日期计算（M3-5-4），不落库。
 */
@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    /** 来源申请单（M3-1-1 追溯），手工建单为空 */
    private Long sourceRequestId;

    private String sourceRequestNo;

    private Long supplierId;

    private String supplierName;

    private Long buyerId;

    private String buyerName;

    private Long deptId;

    private String deptName;

    private String orderDate;

    private String deliveryDate;

    private String deliveryPlace;

    private Integer settlementType;

    private String settlementTypeName;

    private Integer lifecycleStatus;

    private String lifecycleStatusName;

    private Integer auditStatus;

    private String auditStatusName;

    private Integer fulfillStatus;

    private String fulfillStatusName;

    private BigDecimal totalQty;

    private BigDecimal totalAmount;

    private BigDecimal noTaxAmount;

    private BigDecimal taxAmount;

    /** 整单超期天数：交货期已过且未交付完，后端计算；未超期为 0 */
    private Integer overdueDays;

    /** 整单偏离标记：0 无、1 关注、2 显著，取明细行最高等级（M3-6） */
    private Integer deviationFlag;

    private String closeReason;

    private String remark;

    private List<OrderItemVO> items = new ArrayList<>();

    /** 审批历史（M11-5 任务流，与 M2 同构呈现） */
    private List<ApprovalHistoryVO> approvalHistory = new ArrayList<>();

    /** 变更历史时间线（M3-4-2） */
    private List<OrderChangeLogVO> changeLogs = new ArrayList<>();

    /** 交付进度流水（M3-5-1） */
    private List<DeliveryRecordVO> deliveryRecords = new ArrayList<>();
}
