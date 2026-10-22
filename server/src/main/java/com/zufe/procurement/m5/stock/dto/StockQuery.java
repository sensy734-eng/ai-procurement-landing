package com.zufe.procurement.m5.stock.dto;

import lombok.Data;

/**
 * 库存余额查询（M5-3-1 / M5-3-2 / M5-3-3）。
 * available=true 时只返回有库存的行并按可用量排序（可用量口径专用视图）；
 * groupByOwner=true 时按归属对象拆行呈现（公共库存与项目库存分开）。
 */
@Data
public class StockQuery {

    private long pageNo = 1;

    private long pageSize = 10;

    private Long materialId;

    private Long warehouseId;

    /** 物料关键字（编号/名称，匹配走 material 表） */
    private String materialKeyword;

    /** 只返回有库存（quantity > 0）的行 */
    private Boolean onlyPositive;

    private Boolean available;

    private Boolean groupByOwner;
}
