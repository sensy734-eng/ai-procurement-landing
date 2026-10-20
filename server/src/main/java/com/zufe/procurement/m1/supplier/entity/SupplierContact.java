package com.zufe.procurement.m1.supplier.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商联系人，对应 supplier_contact，功能点 M1-2-4。
 * 无唯一键约束，逻辑删除即可；主联系人同一供应商只允许一个。
 */
@Data
@TableName("supplier_contact")
public class SupplierContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private String contactName;

    private String position;

    private String phone;

    private String email;

    /** 是否主联系人：0 否、1 是 */
    private Integer isDefault;

    private Integer status;

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
