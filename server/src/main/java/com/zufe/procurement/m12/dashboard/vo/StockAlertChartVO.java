package com.zufe.procurement.m12.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存不足物料统计（M12-3-4）。
 *
 * 判定与 M5-5-1 库存预警完全一致：可用量 = 数量 − 冻结 − 预留，
 * 可用量 < 物料 safety_stock 即不足，缺口 = 下限 − 可用量；
 * 预警下限为 0 的物料不参与（没有下限就没有不足）。
 * 这里是同一口径的图表化视图，不是第二套算法。
 */
@Data
public class StockAlertChartVO {

    /** 不足物料数 */
    private Integer materialCount;

    /** 缺口数量合计（指标卡） */
    private BigDecimal totalGap;

    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row {

        private Long materialId;

        private String materialNo;

        private String materialName;

        private String spec;

        private String categoryName;

        /** 当前数量（按查询仓库范围合计） */
        private BigDecimal quantity;

        private BigDecimal availableQty;

        private BigDecimal safetyStock;

        private BigDecimal gapQty;
    }
}
