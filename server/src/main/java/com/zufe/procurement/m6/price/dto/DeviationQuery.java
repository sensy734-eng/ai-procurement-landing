package com.zufe.procurement.m6.price.dto;

public class DeviationQuery {

    /** 偏离等级：1 关注、2 显著，空为全部（只返回有偏离的订单） */
    private Integer deviationLevel;
    private Long materialId;
    private String orderNo;
    private Integer pageNo = 1;
    private Integer pageSize = 10;

    public Integer getDeviationLevel() {
        return deviationLevel;
    }

    public void setDeviationLevel(Integer deviationLevel) {
        this.deviationLevel = deviationLevel;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
