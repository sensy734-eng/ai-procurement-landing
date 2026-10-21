package com.zufe.procurement.m4.arrival.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 可登记的已审核订单，功能点 M4-1-1「关联订单，列出未交付明细」。
 * 带出全部未关闭明细行与剩余到货配额，登记页选单后直接渲染，无需再查。
 */
@Data
public class ArrivalOrderVO {

    private Long id;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private LocalDate deliveryDate;

    private String deliveryPlace;

    private List<ArrivalItemVO> items;

    /** 整单剩余到货配额合计 */
    private BigDecimal remainingArrivalQty;
}
