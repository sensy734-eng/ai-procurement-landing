package com.zufe.procurement.m1.dict.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m1.dict.service.DictService;
import com.zufe.procurement.m1.dict.vo.DictItemVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口，功能点 M1-4-1 字典查询。
 * 四个角色均持有 m1:dict:list，用于下拉与部门树。
 */
@RestController
@RequestMapping("/api/m1/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/{typeCode}/items")
    @RequirePermission("m1:dict:list")
    public Result<List<DictItemVO>> items(@PathVariable String typeCode) {
        return Result.ok(dictService.items(typeCode));
    }
}
