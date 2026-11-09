package com.zufe.procurement.m10.kb.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 检索测试结果（M10-5-1）：两路 Top-K 并列 + 命中判定 */
@Data
public class RetrievalTestVO {

    private Long id;
    private String question;
    private Long expectDocId;
    private String expectDocName;
    private Long expectChunkId;
    private Integer topK;
    private List<RetrievalHitVO> bm25;
    private List<RetrievalHitVO> vector;
    private List<RetrievalHitVO> ranked;
    private String requestedStrategy;
    private String actualStrategy;
    private String configId;
    private String degradedReason;
    private Long embeddingTokens;
    /** 是否命中：0 未命中、1 命中 */
    private Integer hitFlag;
    /** 命中位置（两路中更靠前的排名） */
    private Integer hitRank;
    /** 命中的检索路：1 稀疏、2 向量、3 两路均命中 */
    private Integer hitRoute;
    private String hitRouteName;
    private String testBatch;
    private LocalDateTime createdAt;
}
