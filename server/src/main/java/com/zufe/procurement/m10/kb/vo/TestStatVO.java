package com.zufe.procurement.m10.kb.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 命中率统计（M10-5-2） */
@Data
public class TestStatVO {

    private long total;
    private long hitCount;
    /** 综合命中率（任一路在 Top-K 内命中期望对象） */
    private BigDecimal hitRate;
    /** 稀疏路命中数 */
    private long bm25Hit;
    /** 向量路命中数 */
    private long vectorHit;
    /** 两路均命中数 */
    private long bothHit;
    private BigDecimal bm25Rate;
    private BigDecimal vectorRate;
    /** 平均命中排名（仅命中样本，越小越好） */
    private BigDecimal avgHitRank;
}
