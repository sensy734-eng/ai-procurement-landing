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
 * 用户，对应 sys_user，功能点 M11-1、M11-2。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，不存明文 */
    private String passwordHash;

    private String realName;

    private Long deptId;

    private String phone;

    private String email;

    /** 1 启用、2 停用 */
    private Integer status;

    private LocalDateTime pwdUpdateTime;

    /** 0 否、1 是，管理员重置后置 1 */
    private Integer pwdNeedChange;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

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
