package com.zufe.procurement.m11.system.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.system.dto.RoleGrantRequest;
import com.zufe.procurement.m11.system.dto.RoleSaveRequest;
import com.zufe.procurement.m11.system.service.SysRoleService;
import com.zufe.procurement.m11.system.vo.RoleVO;
import com.zufe.procurement.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色与角色授权接口。覆盖功能点：
 * M11-2-5 角色列表与维护、M11-3-2 角色授权。
 */
@RestController
@RequestMapping("/api/m11/roles")
public class SysRoleController {

    private final SysRoleService roleService;

    public SysRoleController(SysRoleService roleService) {
        this.roleService = roleService;
    }

    /** M11-2-5 角色列表，附用户数与权限点数 */
    @GetMapping
    @RequirePermission("m11:role:list")
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.list());
    }

    /** M11-2-5 新增角色 */
    @PostMapping
    @RequirePermission("m11:role:create")
    public Result<Long> create(@Valid @RequestBody RoleSaveRequest request) {
        return Result.ok(roleService.create(request));
    }

    /** M11-2-5 编辑角色 */
    @PutMapping("/{id}")
    @RequirePermission("m11:role:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        request.setId(id);
        roleService.update(request);
        return Result.ok();
    }

    /** M11-3-2 查询角色已持有的权限点，供授权页回填 */
    @GetMapping("/{id}/permissions")
    @RequirePermission("m11:role:list")
    public Result<List<Long>> permissions(@PathVariable Long id) {
        return Result.ok(roleService.permissionIdsOf(id));
    }

    /** M11-3-2 角色授权，整体替换 */
    @PutMapping("/{id}/permissions")
    @RequirePermission("m11:role:grant")
    public Result<Void> grant(@PathVariable Long id, @Valid @RequestBody RoleGrantRequest request) {
        roleService.grantPermissions(id, request.getPermIds());
        return Result.ok();
    }
}
