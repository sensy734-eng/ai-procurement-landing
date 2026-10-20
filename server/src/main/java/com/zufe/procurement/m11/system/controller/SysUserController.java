package com.zufe.procurement.m11.system.controller;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.system.service.SysUserService;
import com.zufe.procurement.m11.system.vo.UserVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口，功能点 M11-2-1 用户列表。
 * 动作级权限点 m11:user:list，系统管理员持有。
 */
@RestController
@RequestMapping("/api/m11/users")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequirePermission("m11:user:list")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long pageNo,
                                           @RequestParam(defaultValue = "10") long pageSize,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(userService.page(pageNo, pageSize, keyword, AuthContext.getOrThrow()));
    }
}
