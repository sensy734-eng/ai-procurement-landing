package com.zufe.procurement.m10.kb.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 单条召回结果（两路通用） */
@Data
public class RetrievalHitVO {

    private int rank;
    private Long chunkId;
    private Long docId;
    private String docName;
    private String sectionNo;
    private String sectionTitle;
    private BigDecimal score;
    private String snippet;
}
