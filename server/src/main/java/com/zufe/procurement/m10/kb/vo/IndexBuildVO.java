package com.zufe.procurement.m10.kb.vo;

import lombok.Data;

/** 索引重建结果（M10-3-4） */
@Data
public class IndexBuildVO {

    private int docCount;
    private int chunkCount;
    private int tokenCount;
    private long elapsedMs;
}
