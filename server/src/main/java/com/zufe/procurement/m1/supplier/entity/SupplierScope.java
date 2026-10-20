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
 * 供应商供货范围，对应 supplier_scope，功能点 M1-2-6。
 * scope_type：1 按物料（scope_id 为 material.id）、2 按物料分类
 * （scope_id 为 material_category.id）。订单选择物料时据此提示，
 * 不做强制拦截。唯一键 uk_scope 不含 is_deleted，保存时物理删除后重建。
 */
@Data
@TableName("supplier_scope")
public class SupplierScope {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private Integer scopeType;

    private Long scopeId;

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
