package com.zufe.procurement.m11.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志，对应 sys_operation_log，功能点 M11-4-4。
 * 授权变更留痕（M11-3-5）也写本表，detail 存修改前后值。
 * 用户名与姓名冗余存放，用户改名或删除后日志仍可读。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String realName;

    private String moduleCode;

    private String action;

    private String targetType;

    private Long targetId;

    private String targetNo;

    private String detail;

    private String resultCode;

    private String ip;

    private Integer isDeleted;

    private LocalDateTime createdAt;
}
