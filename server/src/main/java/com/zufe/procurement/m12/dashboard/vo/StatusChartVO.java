package com.zufe.procurement.m12.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购订单状态分布（M12-3-1）。
 *
 * 五个桶由三状态字段组合而成，互斥且完备：
 * 待审批 = lifecycle 2；待交付 / 部分交付 / 已完成 = lifecycle 3 且 fulfill 1/2/3；
 * 已关闭 = lifecycle 4 或 fulfill 4。
 * 草稿（lifecycle 1）与已作废（lifecycle 5）不属于业务在途单据，单独计入 excluded，
 * 页面上以「不计入」标注，保证五桶之和 + excluded 之和 = 单据总数（可对账）。
 */
@Data
public class StatusChartVO {

    /** 参与分桶的单据数 */
    private Integer orderCount;

    /** 参与分桶的金额合计 */
    private BigDecimal amount;

    /** 五个桶，顺序固定：待审批、待交付、部分交付、已完成、已关闭 */
    private List<Bucket> buckets = new ArrayList<>();

    /** 不计入分桶的单据（草稿、已作废），同样给数量与金额便于对账 */
    private List<Bucket> excluded = new ArrayList<>();

    private LocalDate startDate;

    private LocalDate endDate;

    @Data
    public static class Bucket {

        /** 桶编码，前端稳定取值用 */
        private String code;

        private String name;

        private Integer count;

        private BigDecimal amount;

        /** 占比（0~1，4 位小数），分母是 orderCount */
        private BigDecimal ratio;
    }
}
