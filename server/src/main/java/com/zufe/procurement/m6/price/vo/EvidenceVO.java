package com.zufe.procurement.m6.price.vo;

import java.math.BigDecimal;
import java.util.List;

/** 审批证据面板（M6-4-1/2）：一行订单明细一组价格证据 */
public class EvidenceVO {

    private Long orderId;
    private String orderNo;
    private String supplierName;
    private BigDecimal totalAmount;
    private Integer deviationFlag;
    private List<EvidenceRow> rows;

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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getDeviationFlag() {
        return deviationFlag;
    }

    public void setDeviationFlag(Integer deviationFlag) {
        this.deviationFlag = deviationFlag;
    }

    public List<EvidenceRow> getRows() {
        return rows;
    }

    public void setRows(List<EvidenceRow> rows) {
        this.rows = rows;
    }

    public static class EvidenceRow {
        private Long itemId;
        private String materialNo;
        private String materialName;
        private String spec;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        /** 下单时选用的参考价（基准日 = 订单创建日），无则 null */
        private BigDecimal refPrice;
        private String refEffectiveDate;
        private BigDecimal deviationRate;
        private Integer deviationLevel;
        private String deviationLevelName;
        /** 历史成交均价（快照），无样本 null */
        private BigDecimal avgPrice;
        private Integer avgSampleCount;
        /** 最近一次成交价（快照）+ 来源单号（M6-4-2 溯源） */
        private BigDecimal lastPrice;
        private String lastBillNo;
        private String statWindow;
        /** 证据缺口提示：无参考价 / 无成交样本（不伪造，明确告知） */
        private String note;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
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

        public String getSpec() {
            return spec;
        }

        public void setSpec(String spec) {
            this.spec = spec;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getRefPrice() {
            return refPrice;
        }

        public void setRefPrice(BigDecimal refPrice) {
            this.refPrice = refPrice;
        }

        public String getRefEffectiveDate() {
            return refEffectiveDate;
        }

        public void setRefEffectiveDate(String refEffectiveDate) {
            this.refEffectiveDate = refEffectiveDate;
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

        public String getDeviationLevelName() {
            return deviationLevelName;
        }

        public void setDeviationLevelName(String deviationLevelName) {
            this.deviationLevelName = deviationLevelName;
        }

        public BigDecimal getAvgPrice() {
            return avgPrice;
        }

        public void setAvgPrice(BigDecimal avgPrice) {
            this.avgPrice = avgPrice;
        }

        public Integer getAvgSampleCount() {
            return avgSampleCount;
        }

        public void setAvgSampleCount(Integer avgSampleCount) {
            this.avgSampleCount = avgSampleCount;
        }

        public BigDecimal getLastPrice() {
            return lastPrice;
        }

        public void setLastPrice(BigDecimal lastPrice) {
            this.lastPrice = lastPrice;
        }

        public String getLastBillNo() {
            return lastBillNo;
        }

        public void setLastBillNo(String lastBillNo) {
            this.lastBillNo = lastBillNo;
        }

        public String getStatWindow() {
            return statWindow;
        }

        public void setStatWindow(String statWindow) {
            this.statWindow = statWindow;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}
