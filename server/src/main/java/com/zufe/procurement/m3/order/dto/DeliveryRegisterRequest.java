package com.zufe.procurement.m3.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交付进度登记请求，功能点 M3-5-1。
 * 只写 delivery_progress 流水，不改 delivered_qty（入库单确认才回写，R2）；
 * 累计登记数量不得超过订单数量（跟单登记同样守恒）。
 */
@Data
public class DeliveryRegisterRequest {

    private Long orderItemId;

    private BigDecimal deliveryQty;

    /** 本次交付日期，缺省取当天 */
    private LocalDate deliveryDate;

    private String batchNo;

    private String remark;
}
