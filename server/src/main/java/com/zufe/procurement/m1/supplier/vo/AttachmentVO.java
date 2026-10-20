package com.zufe.procurement.m1.supplier.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商资质附件展示对象，功能点 M1-2-5。
 */
@Data
public class AttachmentVO {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private LocalDate expireDate;
    private String remark;
    private LocalDateTime createdAt;
}
