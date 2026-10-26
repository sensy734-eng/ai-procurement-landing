package com.zufe.procurement.m10.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检索测试记录（M10-5-1、M10-5-2），对应 kb_retrieval_test。
 * 一次测试问句并行跑稀疏（BM25）与向量两路 Top-K，记录两路结果与命中判定，
 * 支撑同资料、同问题、同评价条件下的双路命中率对照。
 */
@Data
@TableName("kb_retrieval_test")
public class KbRetrievalTest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private Long expectDocId;
    private Long expectChunkId;
    /** 稀疏检索 Top-K 结果 JSON */
    private String bm25Topk;
    /** 向量检索 Top-K 结果 JSON */
    private String vectorTopk;
    /** 是否命中：0 未命中、1 命中（任一路在 Top-K 内命中期望对象即命中） */
    private Integer hitFlag;
    /** 命中位置，取两路中更靠前的排名，未命中为空 */
    private Integer hitRank;
    /** 命中的检索路：1 稀疏、2 向量、3 两路均命中 */
    private Integer hitRoute;
    private String testBatch;
    private Long operatorId;
    private Integer isDeleted;
    private LocalDateTime createdAt;
}
