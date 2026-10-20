package com.zufe.procurement.m1.dict.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m1.dict.dto.DictItemSaveRequest;
import com.zufe.procurement.m1.dict.dto.DictTypeSaveRequest;
import com.zufe.procurement.m1.dict.service.DictService;
import com.zufe.procurement.m1.dict.vo.DictItemVO;
import com.zufe.procurement.m1.dict.vo.DictTypeVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口。功能点：M1-4-1 字典类型与字典项维护、M1-4-2 单据类型与供应商类型配置。
 * 下拉选项（/{typeCode}/items）只返回启用项，四个角色都持有 m1:dict:list；
 * 维护接口按动作拆权限，仅管理员持有创建与更新。
 */
@RestController
@RequestMapping("/api/m1/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /** 下拉选项：仅启用项 */
    @GetMapping("/{typeCode}/items")
    @RequirePermission("m1:dict:list")
    public Result<List<DictItemVO>> items(@PathVariable String typeCode) {
        return Result.ok(dictService.items(typeCode));
    }

    /** M1-4-1 字典类型列表，含项数 */
    @GetMapping("/types")
    @RequirePermission("m1:dict:list")
    public Result<List<DictTypeVO>> listTypes() {
        return Result.ok(dictService.listTypes());
    }

    /** M1-4-1 新增字典类型 */
    @PostMapping("/types")
    @RequirePermission("m1:dict:create")
    public Result<Long> createType(@RequestBody DictTypeSaveRequest request) {
        return Result.ok(dictService.createType(request));
    }

    /** M1-4-1 编辑字典类型 */
    @PutMapping("/types/{id}")
    @RequirePermission("m1:dict:update")
    public Result<Void> updateType(@PathVariable Long id, @RequestBody DictTypeSaveRequest request) {
        dictService.updateType(id, request);
        return Result.ok();
    }

    /** M1-4-1 管理端全量字典项，含停用项 */
    @GetMapping("/types/{typeCode}/items")
    @RequirePermission("m1:dict:list")
    public Result<List<DictItemVO>> itemsAdmin(@PathVariable String typeCode) {
        return Result.ok(dictService.itemsAdmin(typeCode));
    }

    /** M1-4-1、M1-4-2 新增字典项 */
    @PostMapping("/items")
    @RequirePermission("m1:dict:create")
    public Result<Long> createItem(@RequestBody DictItemSaveRequest request) {
        return Result.ok(dictService.createItem(request));
    }

    /** M1-4-1、M1-4-2 编辑字典项 */
    @PutMapping("/items/{id}")
    @RequirePermission("m1:dict:update")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody DictItemSaveRequest request) {
        dictService.updateItem(id, request);
        return Result.ok();
    }
}
