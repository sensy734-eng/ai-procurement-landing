package com.zufe.procurement.m12.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商采购金额排名（M12-3-3）。
 *
 * 口径与月度趋势一致：只统计已审核及之后的订单（lifecycle 3/4 且 audit 2），
 * 按金额降序取 Top-N；占比分母是区间内全部已成交金额，不是 Top-N 之和——
 * 否则前 N 名的占比会虚高成 100%。
 */
@Data
public class SupplierRankVO {

    private List<Row> rows = new ArrayList<>();

    /** 区间内全部已成交金额（占比分母） */
    private BigDecimal totalAmount;

    /** 区间内供应商家数（含未进 Top-N 的） */
    private Integer supplierCount;

    private Integer topN;

    private LocalDate startDate;

    private LocalDate endDate;

    @Data
    public static class Row {

        private Integer rank;

        private Long supplierId;

        private String supplierNo;

        private String supplierName;

        private Integer orderCount;

        private BigDecimal amount;

        /** 占区间总金额的比例（0~1，4 位小数） */
        private BigDecimal ratio;
    }
}
