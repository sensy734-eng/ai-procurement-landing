package com.zufe.procurement.m10.kb.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.common.storage.FileStorageService;
import com.zufe.procurement.m10.kb.dto.DocumentQuery;
import com.zufe.procurement.m10.kb.dto.DocumentSaveRequest;
import com.zufe.procurement.m10.kb.dto.DocumentTextRequest;
import com.zufe.procurement.m10.kb.service.KbDocumentService;
import com.zufe.procurement.m10.kb.vo.ChunkVO;
import com.zufe.procurement.m10.kb.vo.DocumentVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * 知识文档接口（M10-1 上传解析、M10-4 文档与切块管理）。
 *
 * 上传后 md/docx 自动解析建索引；pdf 记解析失败，正文走「正文补录」。
 * 文档删除为物理删除并同步清理索引（规划书 5.1 第 10 条例外）。
 */
@RestController
@RequestMapping("/api/m10/documents")
public class KbDocumentController {

    private final KbDocumentService documentService;

    public KbDocumentController(KbDocumentService documentService) {
        this.documentService = documentService;
    }

    /** M10-4-1 文档列表与检索 */
    @GetMapping
    @RequirePermission("m10:document:list")
    public Result<PageResult<DocumentVO>> page(DocumentQuery query) {
        return Result.ok(documentService.page(query));
    }

    /** 文档详情 */
    @GetMapping("/{id}")
    @RequirePermission("m10:document:list")
    public Result<DocumentVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(id));
    }

    /** M10-1-1 文档上传（md/docx/pdf；md/docx 上传后自动解析） */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("m10:document:upload")
    public Result<Long> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam String docName,
                               @RequestParam Integer docType,
                               @RequestParam(required = false) String tags,
                               @RequestParam(required = false) String remark,
                               @RequestParam(required = false) String version,
                               @RequestParam(required = false) LocalDate effectiveDate) {
        return Result.ok(documentService.upload(file, docName, docType, tags, remark, version, effectiveDate));
    }

    /** M10-4-2 元数据维护（版本与生效日期） */
    @PutMapping("/{id}")
    @RequirePermission("m10:document:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody DocumentSaveRequest request) {
        documentService.update(id, request);
        return Result.ok();
    }

    /** M10-1-2 重新解析（从上传文件） */
    @PostMapping("/{id}/parse")
    @RequirePermission("m10:document:parse")
    public Result<Void> reparse(@PathVariable Long id) {
        documentService.reparse(id);
        return Result.ok();
    }

    /** M10-1-3 解析结果校对：正文补录/修正后重建切块与索引 */
    @PutMapping("/{id}/text")
    @RequirePermission("m10:document:parse")
    public Result<Void> saveText(@PathVariable Long id, @RequestBody DocumentTextRequest request) {
        documentService.saveText(id, request.getText());
        return Result.ok();
    }

    /** M10-4-2 新版本：同文档名新建一行，带文件则解析新文件，否则复制来源版本切块 */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("m10:document:upload")
    public Result<Long> newVersion(@PathVariable Long id,
                                   @RequestParam String version,
                                   @RequestParam LocalDate effectiveDate,
                                   @RequestParam(required = false) String remark,
                                   @RequestParam(value = "file", required = false) MultipartFile file) {
        return Result.ok(documentService.newVersion(id, version, effectiveDate, remark, file));
    }

    /** M10-4-3 启用/停用（停用后不参与默认召回） */
    @PostMapping("/{id}/toggle")
    @RequirePermission("m10:document:toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        documentService.toggle(id);
        return Result.ok();
    }

    /** M10-4-5 删除并清理索引（物理删除） */
    @DeleteMapping("/{id}")
    @RequirePermission("m10:document:delete")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }

    /** 下载上传文件（正文补录创建的文档无文件） */
    @GetMapping("/{id}/download")
    @RequirePermission("m10:document:list")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource resource = documentService.loadFile(id);
        String fileName = documentService.fileNameOf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + FileStorageService.encodeFileName(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /** M10-4-4 切块查看 */
    @GetMapping("/{id}/chunks")
    @RequirePermission("m10:chunk:list")
    public Result<List<ChunkVO>> chunks(@PathVariable Long id) {
        return Result.ok(documentService.chunks(id));
    }
}
