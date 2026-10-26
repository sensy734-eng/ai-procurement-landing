package com.zufe.procurement.m10.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 文档元数据维护（M10-4-2 版本与生效日期） */
@Data
public class DocumentSaveRequest {

    @NotBlank(message = "文档名称不能为空")
    @Size(max = 200, message = "文档名称超过 200 字")
    private String docName;

    @NotNull(message = "文档类型不能为空")
    private Integer docType;

    @Size(max = 200, message = "标签超过 200 字")
    private String tags;

    @Size(max = 20, message = "版本号超过 20 字")
    private String version;

    private LocalDate effectiveDate;

    private LocalDate expireDate;

    @Size(max = 500, message = "备注超过 500 字")
    private String remark;
}
