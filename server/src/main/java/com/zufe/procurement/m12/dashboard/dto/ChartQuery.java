package com.zufe.procurement.m12.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 图表查询条件（M12-3 采购运营可视化）。
 *
 * 四个图共用一个筛选条（M12-3-5 时间范围与筛选联动）：
 * 时间范围对状态分布、月度趋势、供应商排名生效，
 * 仓库与分类只对库存不足统计生效——库存没有业务时间维度。
 */
@Data
public class ChartQuery {

    private LocalDate startDate;

    private LocalDate endDate;

    /** Top-N 供应商排名的条数，默认 10，上限 50 */
    private Integer topN;

    /** 库存不足统计：仓库 */
    private Long warehouseId;

    /** 库存不足统计：物料分类 */
    private Long categoryId;
}
