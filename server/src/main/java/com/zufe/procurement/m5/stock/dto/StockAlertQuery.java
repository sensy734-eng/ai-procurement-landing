package com.zufe.procurement.m5.stock.dto;

import lombok.Data;

/**
 * 库存预警查询（M5-5-1 / M5-5-2）。
 * groupBy=warehouse 按仓库汇总缺口；groupBy=category 按物料分类汇总；
 * 缺省按物料明细列行（物料+仓库维度）。
 */
@Data
public class StockAlertQuery {

    private Long warehouseId;

    private Long categoryId;

    private String groupBy;
}
