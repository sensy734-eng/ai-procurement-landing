package com.zufe.procurement.m5.stock.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存余额行 VO（M5-3-1 / M5-3-2）。
 * availableQty 不落库（R3），Service 层按 数量−冻结−预留 现算。
 */
@Data
public class StockBalanceVO {

    private Long materialId;

    private String materialNo;

    private String materialName;

    private String spec;

    private String categoryName;

    private Long warehouseId;

    private String warehouseName;

    /** 归属维度（groupByOwner=true 时有值，否则为合计口径的公共视图） */
    private Integer ownerType;

    private String ownerTypeName;

    private Long ownerId;

    private String ownerNo;

    private String ownerName;

    private BigDecimal quantity;

    private BigDecimal frozenQty;

    private BigDecimal reservedQty;

    /** 可用量 = quantity − frozenQty − reservedQty，后端现算 */
    private BigDecimal availableQty;

    /** 批次明细（物料+仓库维度展开时带） */
    private java.util.List<BatchRow> batches;

    @Data
    public static class BatchRow {

        private String batchNo;

        private BigDecimal quantity;

        private BigDecimal frozenQty;

        private BigDecimal reservedQty;

        private BigDecimal availableQty;
    }
}
