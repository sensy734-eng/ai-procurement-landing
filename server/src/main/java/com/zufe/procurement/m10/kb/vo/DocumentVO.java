package com.zufe.procurement.m10.kb.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 知识文档 VO（M10-4-1） */
@Data
public class DocumentVO {

    private Long id;
    private String docName;
    private Integer docType;
    private String docTypeName;
    private String version;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer status;
    private String statusName;
    private String fileType;
    private Long fileSize;
    /** 是否有上传文件（正文补录创建的文档没有） */
    private Boolean hasFile;
    private Integer parseStatus;
    private String parseStatusName;
    private Integer chunkCount;
    private String tags;
    private String remark;
    private LocalDateTime uploadAt;
}
