package com.zufe.procurement.m11.system.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.system.service.SysPermissionService;
import com.zufe.procurement.m11.system.vo.PermissionGroupVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限点接口，功能点 M11-3-1 权限点列表。
 * 按模块分组返回，授权页据此渲染勾选树，前端不硬编码权限点。
 */
@RestController
@RequestMapping("/api/m11/permissions")
public class SysPermissionController {

    private final SysPermissionService permissionService;

    public SysPermissionController(SysPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @RequirePermission("m11:permission:list")
    public Result<List<PermissionGroupVO>> grouped() {
        return Result.ok(permissionService.grouped());
    }
}
