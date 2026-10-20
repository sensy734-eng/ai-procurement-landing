package com.zufe.procurement.m11.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 角色授权请求，功能点 M11-3-2。
 * 整体提交该角色的权限点集合，服务端先删后插，避免增量比对遗漏。
 */
@Data
public class RoleGrantRequest {

    @NotEmpty(message = "权限点不能为空，如需清空请先停用该角色")
    private List<Long> permIds;
}
