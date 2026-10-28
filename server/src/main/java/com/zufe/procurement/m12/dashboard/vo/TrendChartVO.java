package com.zufe.procurement.m12.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购金额月度趋势（M12-3-2）。
 *
 * 口径：按 order_date 所在月份汇总，只统计已审核及之后（lifecycle 3/4 且 audit 2）的订单，
 * 与 M6 历史成交「已成交订单」口径一致；草稿、审批中、驳回、作废不计入。
 * 近 12 个月连续补零——月份没有单据时给 0 而不是断点，否则折线图会把两个不相邻的月连成一条。
 */
@Data
public class TrendChartVO {

    /** 12 个连续月份，从早到晚 */
    private List<Point> points = new ArrayList<>();

    /** 区间内最大月金额，前端据此定纵轴刻度 */
    private BigDecimal maxAmount;

    /** 区间合计金额 */
    private BigDecimal totalAmount;

    private Integer totalOrders;

    private LocalDate startDate;

    private LocalDate endDate;

    @Data
    public static class Point {

        /** yyyy-MM */
        private String month;

        /** 展示标签，形如 10月 */
        private String label;

        private Integer orderCount;

        private BigDecimal amount;
    }
}
