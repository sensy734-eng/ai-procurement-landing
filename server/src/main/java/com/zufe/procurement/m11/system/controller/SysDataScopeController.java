package com.zufe.procurement.m11.system.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.system.dto.ScopeGrantRequest;
import com.zufe.procurement.m11.system.service.SysDataScopeAdminService;
import com.zufe.procurement.m11.system.vo.ScopeOptionsVO;
import com.zufe.procurement.m11.system.vo.ScopeVO;
import com.zufe.procurement.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据范围授权接口，功能点 M11-3-3。
 * 授权结果由认证时装载进当前用户，页面查询与智能查询共用同一份条件。
 */
@RestController
@RequestMapping("/api/m11/data-scopes")
public class SysDataScopeController {

    private final SysDataScopeAdminService dataScopeService;

    public SysDataScopeController(SysDataScopeAdminService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    /** 查询某主体当前持有的数据范围 */
    @GetMapping
    @RequirePermission("m11:scope:list")
    public Result<List<ScopeVO>> list(@RequestParam Integer subjectType, @RequestParam Long subjectId) {
        return Result.ok(dataScopeService.list(subjectType, subjectId));
    }

    /** 三个维度的候选值 */
    @GetMapping("/options")
    @RequirePermission("m11:scope:list")
    public Result<ScopeOptionsVO> options() {
        return Result.ok(dataScopeService.options());
    }

    /** 保存某主体的数据范围，整体替换 */
    @PutMapping
    @RequirePermission("m11:scope:grant")
    public Result<Void> grant(@Valid @RequestBody ScopeGrantRequest request) {
        dataScopeService.grant(request);
        return Result.ok();
    }
}
