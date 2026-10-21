package com.zufe.procurement.m4.arrival.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 验收结果统计行，功能点 M4-4-3。
 * 合格率 = 合格数量 ÷ 到货数量，保留四位小数（百分比由前端换算展示）；
 * 到货为 0 时合格率记 0，不除零。
 */
@Data
public class ArrivalStatVO {

    private Long supplierId;

    private String supplierName;

    /** 验收单数（已验收，不含草稿） */
    private long inspectionCount;

    /** 到货数量合计 */
    private BigDecimal arrivalQty;

    /** 合格数量合计 */
    private BigDecimal qualifiedQty;

    /** 不合格数量合计 */
    private BigDecimal unqualifiedQty;

    /** 合格率 = 合格 ÷ 到货，到货为 0 记 0 */
    private BigDecimal qualifiedRate;
}
