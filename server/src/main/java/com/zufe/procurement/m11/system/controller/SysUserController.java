package com.zufe.procurement.m11.system.controller;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.system.dto.UserSaveRequest;
import com.zufe.procurement.m11.system.service.SysUserService;
import com.zufe.procurement.m11.system.vo.ResetPwdVO;
import com.zufe.procurement.m11.system.vo.UserVO;
import com.zufe.procurement.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口。覆盖功能点：
 * M11-2-1 列表与检索、M11-2-2 新增与编辑、M11-2-3 启用与停用、
 * M11-2-4 重置密码、M11-2-6 用户角色分配。
 * 每个接口都过动作级权限点，系统管理员持有全部 m11:user:*。
 */
@RestController
@RequestMapping("/api/m11/users")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    /** M11-2-1 用户列表与检索 */
    @GetMapping
    @RequirePermission("m11:user:list")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long pageNo,
                                           @RequestParam(defaultValue = "10") long pageSize,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long deptId,
                                           @RequestParam(required = false) Integer status) {
        return Result.ok(userService.page(pageNo, pageSize, keyword, deptId, status, AuthContext.getOrThrow()));
    }

    /** M11-2-1 用户详情，供编辑表单回填 */
    @GetMapping("/{id}")
    @RequirePermission("m11:user:list")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    /** M11-2-2 新增用户，响应返回一次性初始密码 */
    @PostMapping
    @RequirePermission("m11:user:create")
    public Result<ResetPwdVO> create(@Valid @RequestBody UserSaveRequest request) {
        return Result.ok(userService.create(request));
    }

    /** M11-2-2 编辑用户 */
    @PutMapping("/{id}")
    @RequirePermission("m11:user:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        request.setId(id);
        userService.update(request);
        return Result.ok();
    }

    /** M11-2-3 启用与停用 */
    @PatchMapping("/{id}/status")
    @RequirePermission("m11:user:toggle")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.toggleStatus(id, body.get("status"), AuthContext.getOrThrow());
        return Result.ok();
    }

    /** M11-2-4 重置密码，响应返回一次性初始密码 */
    @PostMapping("/{id}/reset-password")
    @RequirePermission("m11:user:resetpwd")
    public Result<ResetPwdVO> resetPassword(@PathVariable Long id) {
        return Result.ok(userService.resetPassword(id));
    }

    /** M11-2-6 用户角色分配，整体替换 */
    @PutMapping("/{id}/roles")
    @RequirePermission("m11:user:update")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
        return Result.ok();
    }
}
