package com.zufe.procurement.m5.stock.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 余额与流水对账结果（M5-4-4）。
 * 口径：指定时间点前流水累计（含冲减）应与余额逐行相等；
 * 差集两侧都列出——只有流水没有余额、或余额与流水不一致的行都是差异。
 */
@Data
public class ReconcileVO {

    /** 对账时间点 */
    private String asOf;

    /** 覆盖的键行数（流水侧 ∪ 余额侧） */
    private Integer totalKeys;

    /** 一致行数 */
    private Integer matchedKeys;

    /** 差异行数 */
    private Integer diffKeys;

    /** 流水侧累计总量 */
    private BigDecimal flowTotal;

    /** 余额侧总量 */
    private BigDecimal balanceTotal;

    private List<Row> rows;

    @Data
    public static class Row {

        private Long materialId;

        private String materialNo;

        private String materialName;

        private Long warehouseId;

        private String warehouseName;

        private String batchNo;

        private String ownerNo;

        /** 流水累计（该时间点前） */
        private BigDecimal flowQty;

        /** 当前余额 */
        private BigDecimal balanceQty;

        /** 差异 = flowQty − balanceQty */
        private BigDecimal diffQty;

        /** 是否一致 */
        private Boolean matched;
    }
}
