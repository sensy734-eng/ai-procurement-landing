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
 * 会话，对应 sys_session，功能点 M11-1-1、M11-1-2、M11-1-3。
 * 不引 Redis，会话落库，服务重启不丢。
 */
@Data
@TableName("sys_session")
public class SysSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionToken;

    private Long userId;

    private String loginIp;

    private String userAgent;

    private LocalDateTime loginAt;

    private LocalDateTime lastActiveAt;

    private LocalDateTime expireAt;

    private LocalDateTime logoutAt;

    /** 1 主动退出、2 超时失效、3 管理员强制下线 */
    private Integer logoutType;

    /** 1 有效、2 已退出、3 已过期 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
