package com.zufe.procurement.m6.price.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PriceReferenceSaveRequest {

    /** 参考价 id，更新时必填 */
    private Long id;
    /** 物料 id */
    private Long materialId;
    /** 参考价，必须 >= 0 */
    private BigDecimal refPrice;
    /** 生效日期 */
    private LocalDate effectiveDate;
    /** 状态：1 生效、2 停用（更新时可原地切换） */
    private Integer status;
    /** 备注 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getRefPrice() {
        return refPrice;
    }

    public void setRefPrice(BigDecimal refPrice) {
        this.refPrice = refPrice;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
