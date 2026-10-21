package com.zufe.procurement.m3.order.dto;

import lombok.Data;

/**
 * 审批驳回请求，功能点 M3-3-3。意见必填（与 M2-3-3 同口径）。
 */
@Data
public class OrderRejectRequest {

    private String opinion;
}
