package com.zufe.procurement.m3.order.dto;

import lombok.Data;

/**
 * 整单关闭请求，功能点 M3-3-6。原因必填并落 close_reason。
 */
@Data
public class OrderCloseRequest {

    private String reason;
}
