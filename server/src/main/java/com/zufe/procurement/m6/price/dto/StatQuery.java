package com.zufe.procurement.m6.price.dto;

public class StatQuery {

    /** 物料 id，空为全部物料 */
    private Long materialId;
    /** 供应商口径：0 或空为全部供应商 */
    private Long supplierId;

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }
}
