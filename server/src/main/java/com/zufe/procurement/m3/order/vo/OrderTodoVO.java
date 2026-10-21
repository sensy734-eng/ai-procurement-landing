package com.zufe.procurement.m3.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单待办视图，功能点 M3-3-2（P9 订单审批）。
 * 与 M2 待办同构：任务维度一条记录，签批方式从节点配置快照。
 */
@Data
public class OrderTodoVO {

    private Long taskId;

    private String taskNo;

    private Long billId;

    private String billNo;

    private Integer nodeSeq;

    private String nodeName;

    private Integer signType;

    private String signTypeName;

    private String createdTime;

    private String supplierName;

    private String buyerName;

    private String orderDate;

    private String deliveryDate;

    private BigDecimal totalAmount;

    private Integer itemCount;

    /** 整单超期天数，后端计算 */
    private Integer overdueDays;
}
