package com.zufe.procurement.m6.price.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成交价统计 VO：均价 + 最近成交 + 当前参考价（对比展示，R6 严格分离） */
public class PriceStatVO {

    private Long materialId;
    private String materialNo;
    private String materialName;
    /** 历史采购均价（金额加权），无样本为 null */
    private BigDecimal avgPrice;
    private Integer avgSampleCount;
    /** 最近一次成交价，无成交为 null */
    private BigDecimal lastPrice;
    private String lastBillNo;
    private Integer lastSampleCount;
    /** 统计窗口描述，如 LAST_5_DEALS */
    private String statWindow;
    private LocalDateTime calcTime;
    /** 当前生效参考价（手工设定，非当前供应商报价） */
    private BigDecimal refPrice;
    private LocalDateTime refEffectiveDate;
    /** 当前订单均价相对参考价的偏离率，服务层计算 */
    private BigDecimal avgDeviationRate;

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

    public Integer getLastSampleCount() {
        return lastSampleCount;
    }

    public void setLastSampleCount(Integer lastSampleCount) {
        this.lastSampleCount = lastSampleCount;
    }

    public String getStatWindow() {
        return statWindow;
    }

    public void setStatWindow(String statWindow) {
        this.statWindow = statWindow;
    }

    public LocalDateTime getCalcTime() {
        return calcTime;
    }

    public void setCalcTime(LocalDateTime calcTime) {
        this.calcTime = calcTime;
    }

    public BigDecimal getRefPrice() {
        return refPrice;
    }

    public void setRefPrice(BigDecimal refPrice) {
        this.refPrice = refPrice;
    }

    public LocalDateTime getRefEffectiveDate() {
        return refEffectiveDate;
    }

    public void setRefEffectiveDate(LocalDateTime refEffectiveDate) {
        this.refEffectiveDate = refEffectiveDate;
    }

    public BigDecimal getAvgDeviationRate() {
        return avgDeviationRate;
    }

    public void setAvgDeviationRate(BigDecimal avgDeviationRate) {
        this.avgDeviationRate = avgDeviationRate;
    }
}
