package com.zufe.procurement.m11.system.vo;

import lombok.Data;

/**
 * 角色展示对象，功能点 M11-2-5。
 * userCount 与 permCount 用于授权页判断该角色是否在用，不返回明细。
 */
@Data
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private Integer dataScopeAll;
    private Integer isSystem;
    private Integer status;
    private String remark;
    private Integer userCount;
    private Integer permCount;
}
