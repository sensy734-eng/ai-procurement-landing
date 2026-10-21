package com.zufe.procurement.m3.order.dto;

import lombok.Data;

import java.util.List;

/**
 * 按行关闭请求，功能点 M3-2-4。被勾选的待交付/部分交付行置已关闭，
 * 剩余数量不再要求交付；主单履约状态按行重算。
 */
@Data
public class OrderItemCloseRequest {

    private List<Long> itemIds;
}
