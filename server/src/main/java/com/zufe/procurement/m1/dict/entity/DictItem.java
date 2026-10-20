package com.zufe.procurement.m1.dict.entity;

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
 * 字典项，对应 dict_item，功能点 M1-4-1。
 * 部门也以字典项表达（type_code=dept），parent_id 形成部门树。
 */
@Data
@TableName("dict_item")
public class DictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long typeId;

    private String itemCode;

    private String itemName;

    private Long parentId;

    private Integer sortNo;

    private Integer status;

    private String extValue;

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
