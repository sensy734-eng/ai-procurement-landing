package com.zufe.procurement.m5.inbound.dto;

import lombok.Data;

/**
 * 入库单作废请求（M5-1-4）。作废归采购主管，需二次确认（前端弹窗）
 * 与作废原因；作废即冲减库存余额并写冲减流水，回退订单已交付数量。
 */
@Data
public class InboundVoidRequest {

    /** 作废原因，必填（审计） */
    private String reason;
}
