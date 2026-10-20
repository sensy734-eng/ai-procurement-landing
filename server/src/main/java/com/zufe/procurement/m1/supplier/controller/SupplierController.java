package com.zufe.procurement.m1.supplier.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.common.storage.FileStorageService;
import com.zufe.procurement.m1.supplier.dto.ContactSaveRequest;
import com.zufe.procurement.m1.supplier.dto.ScopeSaveRequest;
import com.zufe.procurement.m1.supplier.dto.SupplierQuery;
import com.zufe.procurement.m1.supplier.dto.SupplierSaveRequest;
import com.zufe.procurement.m1.supplier.service.SupplierService;
import com.zufe.procurement.m1.supplier.vo.AttachmentVO;
import com.zufe.procurement.m1.supplier.vo.ContactVO;
import com.zufe.procurement.m1.supplier.vo.ScopeVO;
import com.zufe.procurement.m1.supplier.vo.SupplierVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 供应商接口。功能点：M1-2-1 列表检索、M1-2-2 新增编辑、M1-2-3 启停、
 * M1-2-4 联系人、M1-2-5 资质附件、M1-2-6 供货范围。
 */
@RestController
@RequestMapping("/api/m1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /** M1-2-1 分页列表 */
    @GetMapping("/page")
    @RequirePermission("m1:supplier:list")
    public Result<PageResult<SupplierVO>> page(SupplierQuery query) {
        return Result.ok(supplierService.page(query));
    }

    /** M1-2-1 详情 */
    @GetMapping("/{id}")
    @RequirePermission("m1:supplier:view")
    public Result<SupplierVO> detail(@PathVariable Long id) {
        return Result.ok(supplierService.detail(id));
    }

    /** M1-2-1 可选供应商，仅启用，供新订单下拉 */
    @GetMapping("/options")
    @RequirePermission("m1:supplier:list")
    public Result<List<SupplierVO>> options() {
        return Result.ok(supplierService.options());
    }

    /** M1-2-2 新增 */
    @PostMapping
    @RequirePermission("m1:supplier:create")
    public Result<Long> create(@RequestBody SupplierSaveRequest request) {
        return Result.ok(supplierService.create(request));
    }

    /** M1-2-2 编辑 */
    @PutMapping("/{id}")
    @RequirePermission("m1:supplier:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody SupplierSaveRequest request) {
        supplierService.update(id, request);
        return Result.ok();
    }

    /** M1-2-3 启用停用 */
    @PatchMapping("/{id}/status")
    @RequirePermission("m1:supplier:toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        supplierService.toggleStatus(id);
        return Result.ok();
    }

    /** M1-2-4 联系人列表 */
    @GetMapping("/{id}/contacts")
    @RequirePermission("m1:supplier:view")
    public Result<List<ContactVO>> contacts(@PathVariable Long id) {
        return Result.ok(supplierService.contacts(id));
    }

    /** M1-2-4 联系人整体保存 */
    @PutMapping("/{id}/contacts")
    @RequirePermission("m1:supplier_contact:manage")
    public Result<Void> saveContacts(@PathVariable Long id, @RequestBody ContactSaveRequest request) {
        supplierService.saveContacts(id, request);
        return Result.ok();
    }

    /** M1-2-6 供货范围列表 */
    @GetMapping("/{id}/scopes")
    @RequirePermission("m1:supplier:view")
    public Result<List<ScopeVO>> scopes(@PathVariable Long id) {
        return Result.ok(supplierService.scopes(id));
    }

    /** M1-2-6 供货范围整体保存 */
    @PutMapping("/{id}/scopes")
    @RequirePermission("m1:supplier_scope:manage")
    public Result<Void> saveScopes(@PathVariable Long id, @RequestBody ScopeSaveRequest request) {
        supplierService.saveScopes(id, request);
        return Result.ok();
    }

    /** M1-2-5 资质附件列表 */
    @GetMapping("/{id}/attachments")
    @RequirePermission("m1:supplier:view")
    public Result<List<AttachmentVO>> attachments(@PathVariable Long id) {
        return Result.ok(supplierService.attachments(id));
    }

    /** M1-2-5 上传资质附件，校验类型与大小后落盘 */
    @PostMapping("/{id}/attachments")
    @RequirePermission("m1:supplier_attachment:upload")
    public Result<Long> uploadAttachment(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "expireDate", required = false) LocalDate expireDate,
                                         @RequestParam(value = "remark", required = false) String remark) {
        return Result.ok(supplierService.uploadAttachment(id, file, expireDate, remark));
    }

    /** M1-2-5 下载资质附件 */
    @GetMapping("/attachments/{attachmentId}/download")
    @RequirePermission("m1:supplier:view")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        SupplierService.DownloadedAttachment downloaded = supplierService.downloadAttachment(attachmentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentTypeOf(downloaded.getFileType())));
        headers.setContentDispositionFormData("attachment",
                FileStorageService.encodeFileName(downloaded.getFileName()));
        return ResponseEntity.ok().headers(headers).body(downloaded.getResource());
    }

    /** M1-2-5 删除附件记录，文件保留作审计依据 */
    @DeleteMapping("/attachments/{attachmentId}")
    @RequirePermission("m1:supplier_attachment:delete")
    public Result<Void> deleteAttachment(@PathVariable Long attachmentId) {
        supplierService.deleteAttachment(attachmentId);
        return Result.ok();
    }

    private String contentTypeOf(String fileType) {
        if (fileType == null) {
            return "application/octet-stream";
        }
        return switch (fileType.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
