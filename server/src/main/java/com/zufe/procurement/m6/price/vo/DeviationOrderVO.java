package com.zufe.procurement.m6.price.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 偏离关注清单行（订单维度，M3-6） */
public class DeviationOrderVO {

    private Long orderId;
    private String orderNo;
    private String supplierName;
    /** 整单偏离标记：0 无、1 关注、2 显著 */
    private Integer deviationFlag;
    private String deviationFlagName;
    /** 明细行最高偏离率 */
    private BigDecimal maxDeviationRate;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private List<DeviationItemVO> items;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public Integer getDeviationFlag() {
        return deviationFlag;
    }

    public void setDeviationFlag(Integer deviationFlag) {
        this.deviationFlag = deviationFlag;
    }

    public String getDeviationFlagName() {
        return deviationFlagName;
    }

    public void setDeviationFlagName(String deviationFlagName) {
        this.deviationFlagName = deviationFlagName;
    }

    public BigDecimal getMaxDeviationRate() {
        return maxDeviationRate;
    }

    public void setMaxDeviationRate(BigDecimal maxDeviationRate) {
        this.maxDeviationRate = maxDeviationRate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<DeviationItemVO> getItems() {
        return items;
    }

    public void setItems(List<DeviationItemVO> items) {
        this.items = items;
    }

    /** 行摘要：物料 + 单价 + 偏离 */
    public static class DeviationItemVO {
        private Long itemId;
        private Long materialId;
        private String materialNo;
        private String materialName;
        private BigDecimal unitPrice;
        private BigDecimal refPrice;
        private BigDecimal deviationRate;
        private Integer deviationLevel;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Long getMaterialId() {
            return materialId;
        }

        public void setMaterialId(Long materialId) {
            this.materialId = materialId;
        }

        public String getMaterialNo() {
            return materialNo;
        }

        public void setMaterialNo(String materialNo) {
            this.materialNo = materialNo;
        }

        public String getMaterialName() {
            return materialName;
        }

        public void setMaterialName(String materialName) {
            this.materialName = materialName;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getRefPrice() {
            return refPrice;
        }

        public void setRefPrice(BigDecimal refPrice) {
            this.refPrice = refPrice;
        }

        public BigDecimal getDeviationRate() {
            return deviationRate;
        }

        public void setDeviationRate(BigDecimal deviationRate) {
            this.deviationRate = deviationRate;
        }

        public Integer getDeviationLevel() {
            return deviationLevel;
        }

        public void setDeviationLevel(Integer deviationLevel) {
            this.deviationLevel = deviationLevel;
        }
    }
}
