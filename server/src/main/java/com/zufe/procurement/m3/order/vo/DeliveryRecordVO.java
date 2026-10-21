package com.zufe.procurement.m3.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 交付进度登记视图，功能点 M3-5-1。
 * 过程流水，不参与已交付数量与履约状态判定。
 */
@Data
public class DeliveryRecordVO {

    private Long id;

    private Long orderId;

    private String orderNo;

    private Long orderItemId;

    private Integer itemNo;

    private String materialName;

    private BigDecimal deliveryQty;

    private String deliveryDate;

    private String batchNo;

    private String remark;

    private String operatorName;

    private String createdAt;
}
