package com.zufe.procurement.m1.material.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m1.material.dto.MaterialCategorySaveRequest;
import com.zufe.procurement.m1.material.service.MaterialCategoryService;
import com.zufe.procurement.m1.material.vo.MaterialCategoryVO;
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
 * 物料分类接口，功能点 M1-1-7。
 * 计量单位属字典项（类型 unit），走字典接口维护，本接口只管分类树。
 */
@RestController
@RequestMapping("/api/m1/material-categories")
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;

    public MaterialCategoryController(MaterialCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** M1-1-7 分类树 */
    @GetMapping("/tree")
    @RequirePermission("m1:material_category:list")
    public Result<List<MaterialCategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    /** M1-1-7 新增分类 */
    @PostMapping
    @RequirePermission("m1:material_category:create")
    public Result<Long> create(@RequestBody MaterialCategorySaveRequest request) {
        return Result.ok(categoryService.create(request));
    }

    /** M1-1-7 编辑分类，含启停与调整父节点 */
    @PutMapping("/{id}")
    @RequirePermission("m1:material_category:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialCategorySaveRequest request) {
        categoryService.update(id, request);
        return Result.ok();
    }
}
