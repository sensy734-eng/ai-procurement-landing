package com.zufe.procurement.m4.arrival.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单草稿摘要，随验收单详情返回（M4-3）。
 * 轮次 6 只读展示；确认与作废在轮次 7（M5-1）实现。
 */
@Data
public class InboundDraftVO {

    private Long id;

    private String inboundNo;

    private Long inspectionId;

    private String inspectionNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private Long warehouseId;

    private String warehouseName;

    private LocalDate inboundDate;

    /** 入库单状态：1 草稿、2 已确认、3 已作废 */
    private Integer status;

    private String statusName;

    /** 入库数量合计，按明细行现算 */
    private BigDecimal totalQty;

    private Long operatorId;

    private String operatorName;

    private String remark;
}
