package com.zufe.procurement.m1.supplier.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商资质附件，对应 supplier_attachment，功能点 M1-2-5。
 * file_path 存相对路径，不含主机信息；文件本体放在服务端上传目录，
 * 删除记录不删文件，保留审计依据。
 */
@Data
@TableName("supplier_attachment")
public class SupplierAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String filePath;

    private LocalDate expireDate;

    private String remark;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
