package com.zufe.procurement.m11.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表展示对象，功能点 M11-2-1、M11-2-6。
 * 不返回密码哈希，避免敏感字段外泄；角色以 id 与名称两组返回，供编辑表单回填。
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
    private String remark;
    private Integer status;
    private Integer pwdNeedChange;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createdAt;

    private List<Long> roleIds;
    private List<String> roleNames;
}
