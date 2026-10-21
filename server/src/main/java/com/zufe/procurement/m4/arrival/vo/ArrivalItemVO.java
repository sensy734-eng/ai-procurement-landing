package com.zufe.procurement.m4.arrival.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 到货验收明细行视图，功能点 M4-4-2。
 * 配额类字段（orderQty/arrivedQty/remainingArrival/inboundableQty）由
 * 服务层现算，不落库：
 * - arrivedQty 含本单在内该订单行全部到货量；
 * - remainingArrival = 订单量 − 已到货，新建登记时的到货上限；
 * - inboundableQty = 本行可入量，让步单含不合格部分，否则只合格部分。
 */
@Data
public class ArrivalItemVO {

    private Long id;

    private Long inspectionId;

    private Long orderItemId;

    private Integer orderItemNo;

    private Long materialId;

    private String materialNo;

    private String materialName;

    private String spec;

    private Long unitId;

    private String unitName;

    private BigDecimal arrivalQty;

    private BigDecimal qualifiedQty;

    private BigDecimal unqualifiedQty;

    private String batchNo;

    private Integer ownerType;

    private Long ownerId;

    private String ownerNo;

    private String remark;

    /** 订单行数量 */
    private BigDecimal orderQty;

    /** 该订单行累计到货量（含本单） */
    private BigDecimal arrivedQty;

    /** 剩余到货配额 = 订单量 − 累计到货 */
    private BigDecimal remainingArrival;

    /** 本行可入量 */
    private BigDecimal inboundableQty;
}
