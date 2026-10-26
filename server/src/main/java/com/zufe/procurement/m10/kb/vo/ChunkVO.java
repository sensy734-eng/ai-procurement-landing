package com.zufe.procurement.m10.kb.vo;

import lombok.Data;

/** 文档切块 VO（M10-2-3 预览、M10-4-4 切块查看） */
@Data
public class ChunkVO {

    private Long id;
    private Integer chunkIndex;
    private String sectionNo;
    private String sectionTitle;
    private Integer charCount;
    private String chunkText;
    /** 是否已生成向量（未生成说明该文档索引未重建） */
    private Boolean hasVector;
}
