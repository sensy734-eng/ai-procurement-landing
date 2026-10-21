package com.zufe.procurement.m3.order.dto;

import lombok.Data;

/**
 * 由申请单生成订单请求，功能点 M3-1-3。
 * 订单侧发起的转单入口：带入已审核申请单的明细与归属对象，
 * 实际生成逻辑复用 M2-4 的转订单服务（已转数量唯一写入点不变）。
 */
@Data
public class FromRequestRequest {

    private Long requestId;
}
