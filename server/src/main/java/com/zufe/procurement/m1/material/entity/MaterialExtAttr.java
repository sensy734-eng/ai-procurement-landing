package com.zufe.procurement.m1.material.entity;

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
 * 物料扩展属性，对应 material_ext_attr，功能点 M1-1-6。
 * 行业专属字段以属性名值对存放，避免主表字段膨胀。
 * 唯一键 uk_ext_material_attr 不含 is_deleted，保存时物理删除后重建，
 * 与关联表授权同一处理模式。
 */
@Data
@TableName("material_ext_attr")
public class MaterialExtAttr {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;

    private String attrName;

    private String attrValue;

    private Integer sortNo;

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
