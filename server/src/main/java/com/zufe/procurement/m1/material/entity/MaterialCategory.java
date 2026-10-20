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
 * 物料分类，对应 material_category，功能点 M1-1-7。
 * parent_id 形成分类树，level_no 随父节点层级计算，更换父节点时
 * 需连带重算子树层级并防止成环。
 */
@Data
@TableName("material_category")
public class MaterialCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String categoryNo;

    private String categoryName;

    /** 上级分类id，0 表示根节点 */
    private Long parentId;

    private Integer levelNo;

    private Integer sortNo;

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
