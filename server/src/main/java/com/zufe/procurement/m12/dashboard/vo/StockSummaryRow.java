package com.zufe.procurement.m12.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存汇总行（M12-2-2）。
 * 按仓库 / 物料分类 / 归属对象分组，输出余额数量与可用量。
 * 可用量 = 数量 − 冻结 − 预留，与 M5-3-2 现算口径一致，不落库、不缓存。
 */
@Data
public class StockSummaryRow {

    /** 分组键：仓库 id / 分类 id / 归属对象编号 */
    private String groupKey;

    /** 分组名：仓库名 / 分类名 / 归属对象名 */
    private String groupName;

    /** 涉及物料数（去重） */
    private Integer materialCount;

    /** 明细行数（物料 + 仓库 + 批次 + 归属的组合数） */
    private Integer rowCount;

    /** 数量合计 */
    private BigDecimal quantity;

    /** 冻结数量合计 */
    private BigDecimal frozenQty;

    /** 预留数量合计 */
    private BigDecimal reservedQty;

    /** 可用量合计 = 数量 − 冻结 − 预留 */
    private BigDecimal availableQty;
}
