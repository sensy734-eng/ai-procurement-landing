package com.zufe.procurement.m6.price.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成交价统计快照（M6-1-2、M6-1-3）。
 * 快照由刷新任务计算写入，查询链路只读快照（E 域约束）；
 * 统计口径（窗口类型与取值、最少样本数）由 sys_param 的 price 分组固化。
 * supplier_id = 0 表示全部供应商口径。
 */
@TableName("price_stat_snapshot")
public class PriceStatSnapshot {

    /** 统计类型：1 历史采购均价、2 最近一次成交价 */
    public static final int STAT_AVG = 1;
    public static final int STAT_LAST = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private Long supplierId;
    private Integer statType;
    private BigDecimal statValue;
    private String statWindow;
    private Integer sampleCount;
    private String sourceBillNo;
    private LocalDateTime calcTime;
    private Integer isDeleted;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Integer getStatType() {
        return statType;
    }

    public void setStatType(Integer statType) {
        this.statType = statType;
    }

    public BigDecimal getStatValue() {
        return statValue;
    }

    public void setStatValue(BigDecimal statValue) {
        this.statValue = statValue;
    }

    public String getStatWindow() {
        return statWindow;
    }

    public void setStatWindow(String statWindow) {
        this.statWindow = statWindow;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public String getSourceBillNo() {
        return sourceBillNo;
    }

    public void setSourceBillNo(String sourceBillNo) {
        this.sourceBillNo = sourceBillNo;
    }

    public LocalDateTime getCalcTime() {
        return calcTime;
    }

    public void setCalcTime(LocalDateTime calcTime) {
        this.calcTime = calcTime;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
