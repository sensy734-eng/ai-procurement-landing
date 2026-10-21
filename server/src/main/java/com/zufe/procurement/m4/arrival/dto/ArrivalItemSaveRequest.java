package com.zufe.procurement.m4.arrival.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 到货登记行请求，功能点 M4-1-2、M4-1-3、M4-2-3。
 * arrivalQty 是本次到货量，校验不超过订单行剩余到货配额（M4-1-3）；
 * qualifiedQty + unqualifiedQty 必须等于 arrivalQty（R12），
 * 缺省按全合格处理（登记阶段允许先只记到货量）。
 * batchNo 为预登记批次号（M5-2-1），生成入库单时带入。
 */
@Data
public class ArrivalItemSaveRequest {

    /** 订单明细行id，必须属于同一张订单且未关闭 */
    private Long orderItemId;

    /** 本次到货数量，大于 0 */
    private BigDecimal arrivalQty;

    /** 合格数量，缺省 0；提交时与不合格数之和须等于到货数 */
    private BigDecimal qualifiedQty;

    /** 不合格数量，缺省 0 */
    private BigDecimal unqualifiedQty;

    /** 预登记批次号，可空 */
    private String batchNo;

    private String remark;
}
