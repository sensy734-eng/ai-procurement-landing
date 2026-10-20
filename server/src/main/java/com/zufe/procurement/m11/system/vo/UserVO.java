package com.zufe.procurement.m11.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表展示对象，功能点 M11-2-1。
 * 不返回密码哈希，避免敏感字段外泄。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private Long deptId;
    private String deptName;
    private String phone;
    private String email;
    private Integer status;
    private Integer pwdNeedChange;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createdAt;
}
