package com.zufe.procurement.m3.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 交付汇总行，功能点 M3-5-5 订单交付汇总视图（按供应商/时间）。
 * 金额口径：已交付与未交付金额按行数量比例折算（HALF_UP），
 * 三者满足 合计金额 = 已交付金额 + 未交付金额；不含按单拆分精度保证，
 * 汇总视图只用于经营跟单，不作为结算依据。
 */
@Data
public class DeliverySummaryVO {

    private Long supplierId;

    private String supplierName;

    /** 订单数 */
    private Integer orderCount;

    /** 订单数量合计 */
    private BigDecimal totalQty;

    /** 已交付数量合计（delivered_qty 累加） */
    private BigDecimal deliveredQty;

    /** 未交付数量合计 = totalQty − deliveredQty */
    private BigDecimal remainingQty;

    /** 订单含税金额合计 */
    private BigDecimal totalAmount;

    private BigDecimal deliveredAmount;

    private BigDecimal remainingAmount;
}
