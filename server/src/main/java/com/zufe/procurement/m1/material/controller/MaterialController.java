package com.zufe.procurement.m1.material.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m1.material.dto.MaterialCategorySaveRequest;
import com.zufe.procurement.m1.material.dto.MaterialQuery;
import com.zufe.procurement.m1.material.dto.MaterialSaveRequest;
import com.zufe.procurement.m1.material.service.MaterialCategoryService;
import com.zufe.procurement.m1.material.service.MaterialService;
import com.zufe.procurement.m1.material.vo.MaterialCategoryVO;
import com.zufe.procurement.m1.material.vo.MaterialImportResultVO;
import com.zufe.procurement.m1.material.vo.MaterialVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 物料接口。功能点：M1-1-1 列表检索、M1-1-2 新增、M1-1-3 编辑、
 * M1-1-4 启停、M1-1-5 导入导出、M1-1-6 扩展属性（随主档保存）。
 */
@RestController
@RequestMapping("/api/m1/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /** M1-1-1 分页列表 */
    @GetMapping("/page")
    @RequirePermission("m1:material:list")
    public Result<PageResult<MaterialVO>> page(MaterialQuery query) {
        return Result.ok(materialService.page(query));
    }

    /** M1-1-1 详情，含扩展属性 */
    @GetMapping("/{id}")
    @RequirePermission("m1:material:view")
    public Result<MaterialVO> detail(@PathVariable Long id) {
        return Result.ok(materialService.detail(id));
    }

    /** M1-1-1 可选物料，仅启用，供新单据下拉 */
    @GetMapping("/options")
    @RequirePermission("m1:material:list")
    public Result<List<MaterialVO>> options() {
        return Result.ok(materialService.options());
    }

    /** M1-1-2 新增 */
    @PostMapping
    @RequirePermission("m1:material:create")
    public Result<Long> create(@RequestBody MaterialSaveRequest request) {
        return Result.ok(materialService.create(request));
    }

    /** M1-1-3 编辑 */
    @PutMapping("/{id}")
    @RequirePermission("m1:material:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialSaveRequest request) {
        materialService.update(id, request);
        return Result.ok();
    }

    /** M1-1-4 启用停用 */
    @PatchMapping("/{id}/status")
    @RequirePermission("m1:material:toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        materialService.toggleStatus(id);
        return Result.ok();
    }

    /** M1-1-5 批量导入，逐行返回成功失败明细 */
    @PostMapping("/import")
    @RequirePermission("m1:material:import")
    public Result<MaterialImportResultVO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.ok(materialService.importCsv(file));
    }

    /** M1-1-5 按当前筛选导出 CSV */
    @GetMapping("/export")
    @RequirePermission("m1:material:export")
    public ResponseEntity<byte[]> exportCsv(MaterialQuery query) {
        byte[] csv = materialService.exportCsv(query);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                URLEncoder.encode("物料档案.csv", StandardCharsets.UTF_8).replace("+", "%20"));
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    /** M1-1-5 导入模板下载 */
    @GetMapping("/import-template")
    @RequirePermission("m1:material:import")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] csv = materialService.template();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                URLEncoder.encode("物料导入模板.csv", StandardCharsets.UTF_8).replace("+", "%20"));
        return ResponseEntity.ok().headers(headers).body(csv);
    }
}
