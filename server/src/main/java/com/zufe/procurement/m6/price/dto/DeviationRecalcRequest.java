package com.zufe.procurement.m6.price.dto;

/**
 * 偏离重算请求：materialId / orderId 都可空（空 = 全量重算）。
 * 用于参考价变更后同步订单判定，重算写新值并留操作日志。
 */
public class DeviationRecalcRequest {

    private Long materialId;
    private Long orderId;

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
