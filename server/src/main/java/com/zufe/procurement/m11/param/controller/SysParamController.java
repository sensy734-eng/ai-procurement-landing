package com.zufe.procurement.m11.param.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m11.param.dto.ParamUpdateRequest;
import com.zufe.procurement.m11.param.service.SysParamService;
import com.zufe.procurement.m11.param.vo.ParamVO;
import com.zufe.procurement.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统参数接口。覆盖功能点：
 * M11-4-1 价格偏离阈值、M11-4-2 库存预警下限、M11-4-3 单据编号规则。
 * 参数按分组查询，界面按组渲染，口径说明随 remark 一并返回。
 */
@RestController
@RequestMapping("/api/m11/params")
public class SysParamController {

    private final SysParamService paramService;

    public SysParamController(SysParamService paramService) {
        this.paramService = paramService;
    }

    @GetMapping
    @RequirePermission("m11:param:list")
    public Result<List<ParamVO>> list(@RequestParam(required = false) String group) {
        return Result.ok(paramService.list(group));
    }

    @PutMapping("/{paramCode}")
    @RequirePermission("m11:param:update")
    public Result<Void> update(@PathVariable String paramCode, @Valid @RequestBody ParamUpdateRequest request) {
        paramService.update(paramCode, request.getParamValue());
        return Result.ok();
    }
}
