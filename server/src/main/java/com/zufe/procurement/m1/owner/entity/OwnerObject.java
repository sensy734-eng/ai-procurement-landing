package com.zufe.procurement.m1.owner.entity;

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
 * 归属对象，对应 owner_object，功能点 M1/M2-2-5。
 * 归属用 owner_type / owner_id / owner_no 三字段抽象，不建外键，
 * 本表是归属对象的登记处，供数据范围授权的归属维度选择。
 */
@Data
@TableName("owner_object")
public class OwnerObject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1 公共、2 项目、3 工单 */
    private Integer ownerType;

    private String ownerNo;

    private String ownerName;

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