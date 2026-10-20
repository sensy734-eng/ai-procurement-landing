package com.zufe.procurement.m1.warehouse.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m1.warehouse.dto.LocationSaveRequest;
import com.zufe.procurement.m1.warehouse.dto.WarehouseSaveRequest;
import com.zufe.procurement.m1.warehouse.dto.WarehouseUserSaveRequest;
import com.zufe.procurement.m1.warehouse.service.WarehouseService;
import com.zufe.procurement.m1.warehouse.vo.LocationVO;
import com.zufe.procurement.m1.warehouse.vo.WarehouseUserVO;
import com.zufe.procurement.m1.warehouse.vo.WarehouseVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仓库接口。功能点：M1-3-1 仓库档案、M1-3-2 库位、M1-3-3 仓库使用人。
 */
@RestController
@RequestMapping("/api/m1/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /** M1-3-1 分页列表 */
    @GetMapping("/page")
    @RequirePermission("m1:warehouse:list")
    public Result<PageResult<WarehouseVO>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status) {
        return Result.ok(warehouseService.page(pageNo, pageSize, keyword, status));
    }

    /** M1-3-1 详情 */
    @GetMapping("/{id}")
    @RequirePermission("m1:warehouse:list")
    public Result<WarehouseVO> detail(@PathVariable Long id) {
        return Result.ok(warehouseService.detail(id));
    }

    /** M1-3-1 可选仓库，仅启用 */
    @GetMapping("/options")
    @RequirePermission("m1:warehouse:list")
    public Result<List<WarehouseVO>> options() {
        return Result.ok(warehouseService.options());
    }

    /** M1-3-1 新增仓库 */
    @PostMapping
    @RequirePermission("m1:warehouse:create")
    public Result<Long> create(@RequestBody WarehouseSaveRequest request) {
        return Result.ok(warehouseService.create(request));
    }

    /** M1-3-1 编辑仓库 */
    @PutMapping("/{id}")
    @RequirePermission("m1:warehouse:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody WarehouseSaveRequest request) {
        warehouseService.update(id, request);
        return Result.ok();
    }

    /** M1-3-2 库位列表 */
    @GetMapping("/{id}/locations")
    @RequirePermission("m1:location:list")
    public Result<List<LocationVO>> locations(@PathVariable Long id) {
        return Result.ok(warehouseService.locations(id));
    }

    /** M1-3-2 新增库位 */
    @PostMapping("/{id}/locations")
    @RequirePermission("m1:location:create")
    public Result<Long> createLocation(@PathVariable Long id, @RequestBody LocationSaveRequest request) {
        return Result.ok(warehouseService.createLocation(id, request));
    }

    /** M1-3-2 编辑库位 */
    @PutMapping("/locations/{locationId}")
    @RequirePermission("m1:location:update")
    public Result<Void> updateLocation(@PathVariable Long locationId, @RequestBody LocationSaveRequest request) {
        warehouseService.updateLocation(locationId, request);
        return Result.ok();
    }

    /** M1-3-3 仓库使用人列表 */
    @GetMapping("/{id}/users")
    @RequirePermission("m1:warehouse:list")
    public Result<List<WarehouseUserVO>> warehouseUsers(@PathVariable Long id) {
        return Result.ok(warehouseService.warehouseUsers(id));
    }

    /** M1-3-3 仓库使用人整体保存 */
    @PutMapping("/{id}/users")
    @RequirePermission("m1:warehouse_user:manage")
    public Result<Void> saveWarehouseUsers(@PathVariable Long id, @RequestBody WarehouseUserSaveRequest request) {
        warehouseService.saveWarehouseUsers(id, request);
        return Result.ok();
    }
}
