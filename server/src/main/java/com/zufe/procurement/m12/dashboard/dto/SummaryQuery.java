package com.zufe.procurement.m12.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 汇总查询条件（M12-2-1 采购单据汇总 / M12-2-2 库存汇总）。
 *
 * groupBy 取值按功能点区分：
 * - 采购单据汇总：status / supplier / material / month
 * - 库存汇总：warehouse / category / owner
 *
 * 时间范围只对采购单据生效（库存余额没有时间维度）。
 */
@Data
public class SummaryQuery {

    /** 分组维度，缺省按采购单据的状态、库存的仓库 */
    private String groupBy;

    /** 时间范围起（含），按 order_date 过滤 */
    private LocalDate startDate;

    /** 时间范围止（含） */
    private LocalDate endDate;

    /** 采购单据过滤：供应商 */
    private Long supplierId;

    /** 采购单据过滤：物料（走明细行） */
    private Long materialId;

    /** 采购单据过滤：三状态任一维度 */
    private Integer lifecycleStatus;

    private Integer auditStatus;

    private Integer fulfillStatus;

    /** 库存过滤：仓库 */
    private Long warehouseId;

    /** 库存过滤：物料分类 */
    private Long categoryId;

    /** 库存过滤：归属对象编号（PUBLIC 表示公共库存） */
    private String ownerNo;
}
