package com.zufe.procurement.m10.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 检索测试问句（M10-5-1） */
@Data
public class RetrievalTestRequest {

    @NotBlank(message = "测试问句不能为空")
    @Size(max = 500, message = "测试问句超过 500 字")
    private String question;

    /** 期望命中的文档 id，不传则只跑两路展示不做命中判定 */
    private Long expectDocId;

    /** 期望命中的切块 id，优先级高于期望文档 */
    private Long expectChunkId;

    /** 各路召回条数，缺省取 sys_param kb_recall_topk */
    private Integer topK;

    @Size(max = 50, message = "测试批次标识超过 50 字")
    private String testBatch;
    /** bm25 / bm25_hash / semantic / bm25_semantic */
    private String strategy;
    /** Evaluation requests never silently fall back. */
    private boolean strict = true;
}
