package com.zufe.procurement.m5.stock.dto;

import lombok.Data;

/**
 * 库存流水查询（M5-4-2）。物料/仓库/时间/单据类型筛选；
 * 数据范围按仓库维度注入（同 StockQuery 口径）。
 */
@Data
public class FlowQuery {

    private long pageNo = 1;

    private long pageSize = 10;

    private Long materialId;

    private Long warehouseId;

    private String materialKeyword;

    /** 来源单据类型：1 采购入库单 */
    private Integer sourceType;

    /** 来源单据id */
    private Long sourceId;

    /** 批次号 */
    private String batchNo;

    /** 变动类型：1 入库增加、2 作废冲减 */
    private Integer changeType;

    private String dateFrom;

    private String dateTo;
}
