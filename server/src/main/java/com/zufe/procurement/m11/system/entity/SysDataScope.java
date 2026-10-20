package com.zufe.procurement.m11.system.entity;

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
 * 数据范围授权，对应 sys_data_scope，功能点 M11-3-3。
 * 三维度：1 仓库、2 部门、3 归属对象，主体可以是用户或角色。
 */
@Data
@TableName("sys_data_scope")
public class SysDataScope {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1 用户、2 角色 */
    private Integer subjectType;

    private Long subjectId;

    /** 1 仓库、2 部门、3 归属对象 */
    private Integer scopeDimension;

    private String scopeValue;

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
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
