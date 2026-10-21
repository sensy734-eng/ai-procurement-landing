package com.zufe.procurement.m3.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单变更登记请求，功能点 M3-4-1。
 * itemId 为空表示整单级变更（交货期、交货地点、结算方式）；
 * 非空表示明细行级变更（数量、含税单价、行交货期、行交货地点）。
 * 只传要改的字段，未传字段不登记不变更；原因必填，保证痕迹可读。
 */
@Data
public class OrderChangeRequest {

    private Long itemId;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private LocalDate deliveryDate;

    private String deliveryPlace;

    private Integer settlementType;

    private String reason;
}
