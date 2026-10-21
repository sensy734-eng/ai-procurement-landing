package com.zufe.procurement.m3.order.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 订单主表保存请求，功能点 M3-1-2 新建、M3-1-4 编辑。
 * 明细行不在本请求内，按行逐个保存（M3-2-1）。
 */
@Data
public class OrderSaveRequest {

    private Long supplierId;

    /** 下单日期，缺省取当天 */
    private LocalDate orderDate;

    /** 整单交货期，明细行未设置时取此值 */
    private LocalDate deliveryDate;

    private String deliveryPlace;

    /** 结算方式：1 月结、2 货到付款、3 预付款，缺省月结 */
    private Integer settlementType;

    private String remark;
}
