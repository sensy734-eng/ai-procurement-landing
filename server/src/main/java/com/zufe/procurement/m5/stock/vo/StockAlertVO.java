package com.zufe.procurement.m5.stock.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存预警行 VO（M5-5-1 / M5-5-2）。
 * 判定基准是物料预警下限 safety_stock：可用量 < 下限即不足，
 * 缺口 = 下限 − 可用量（正数）。
 */
@Data
public class StockAlertVO {

    private Long materialId;

    private String materialNo;

    private String materialName;

    private String spec;

    private String categoryName;

    private Long warehouseId;

    private String warehouseName;

    private BigDecimal quantity;

    private BigDecimal availableQty;

    /** 物料预警下限 */
    private BigDecimal safetyStock;

    /** 缺口数量 = safetyStock − availableQty */
    private BigDecimal gapQty;

    /** 汇总口径（groupBy=warehouse/category 时）的物料行数 */
    private Integer materialCount;
}
