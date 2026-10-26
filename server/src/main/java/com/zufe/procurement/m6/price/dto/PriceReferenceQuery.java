package com.zufe.procurement.m6.price.dto;

public class PriceReferenceQuery {

    private Long materialId;
    /** 状态：1 生效、2 停用，空为全部 */
    private Integer status;
    private Integer pageNo = 1;
    private Integer pageSize = 10;

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
