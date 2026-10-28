package com.zufe.procurement.m12.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购单据汇总行（M12-2-1）。
 * 按状态 / 供应商 / 物料 / 月份分组，输出单据数、明细行数与数量金额合计。
 */
@Data
public class PurchaseSummaryRow {

    /** 分组键：状态值 / 供应商 id / 物料 id / 月份 yyyy-MM */
    private String groupKey;

    /** 分组名：状态中文 / 供应商名 / 物料名 / 月份 */
    private String groupName;

    /** 单据数 */
    private Integer orderCount;

    /** 明细行数 */
    private Integer itemCount;

    /** 数量合计（按明细行数量，不是订单总数量字段） */
    private BigDecimal qty;

    /** 金额合计 */
    private BigDecimal amount;
}
