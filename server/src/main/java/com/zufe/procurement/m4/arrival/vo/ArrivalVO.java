package com.zufe.procurement.m4.arrival.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 到货验收单视图，功能点 M4-4-1、M4-4-2。
 * 明细行带配额现算值（订单量、已到货、剩余到货、可入量），
 * 数量一律不落库现算，与订单剩余数量口径一致。
 */
@Data
public class ArrivalVO {

    private Long id;

    private String inspectionNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private LocalDate arrivalDate;

    private Long warehouseId;

    private String warehouseName;

    private Integer inspectionMode;

    private String inspectionModeName;

    private Integer inspectionResult;

    private String inspectionResultName;

    private BigDecimal qualifiedQty;

    private BigDecimal unqualifiedQty;

    private Integer concessionFlag;

    private String concessionReason;

    private Long inspectorId;

    private String inspectorName;

    /** 验收单状态：1 草稿、2 已验收 */
    private Integer status;

    private String statusName;

    private String remark;

    private LocalDateTime createdAt;

    private Long createdBy;

    private String createdByName;

    private List<ArrivalItemVO> items;

    /** 本验收单已生成的入库单草稿（M4-3-3 多批次可有多张） */
    private List<InboundDraftVO> inboundOrders;

    /** 整单可入量合计：让步单 = 合格 + 不合格，否则只合格 */
    private BigDecimal inboundableQty;

    /** 已生成入库量合计（草稿 + 已确认） */
    private BigDecimal generatedQty;
}
