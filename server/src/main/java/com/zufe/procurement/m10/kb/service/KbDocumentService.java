package com.zufe.procurement.m10.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.common.storage.FileStorageService;
import com.zufe.procurement.m10.kb.dto.DocumentQuery;
import com.zufe.procurement.m10.kb.dto.DocumentSaveRequest;
import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbDocumentMapper;
import com.zufe.procurement.m10.kb.mapper.KbSparseIndexMapper;
import com.zufe.procurement.m10.kb.parse.DocumentParser;
import com.zufe.procurement.m10.kb.parse.ParsedDoc;
import com.zufe.procurement.m10.kb.parse.TextChunker;
import com.zufe.procurement.m10.kb.service.KbIndexService.IndexBuildResult;
import com.zufe.procurement.m10.kb.vo.ChunkVO;
import com.zufe.procurement.m10.kb.vo.DocumentVO;
import com.zufe.procurement.m11.param.service.SysParamService;
import com.zufe.procurement.m11.system.service.OperationLogService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识文档管理（M10-1 上传解析、M10-4 文档与切块管理）。
 *
 * 口径：
 * - 上传（M10-1-1）只做类型与大小校验后存储并落待解析记录；md/docx 上传后
 *   系统自动解析（M10-1-2），pdf 记解析失败并提示走正文补录；
 * - 正文补录（M10-1-3）：粘贴文本整体替换正文后重新切块建索引，是人工校对入口，
 *   也是 pdf 的唯一正文来源；
 * - 新版本（M10-4-2）= 同文档名新建一行（版本号与生效日期前进），旧版保留，
 *   检索侧按生效日期取现行有效版本优先；不带文件时复制来源版本切块；
 * - 删除（M10-4-5）走物理删除并同步清理切块与倒排索引（规划书 5.1 第 10 条例外）；
 * - 停用（M10-4-3）后不参与默认召回，两路检索的候选集都以文档启用为前提。
 */
@Service
public class KbDocumentService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = new HashSet<>(Arrays.asList("pdf", "docx", "md"));

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final KbSparseIndexMapper sparseIndexMapper;
    private final KbIndexService indexService;
    private final DocumentParser documentParser;
    private final FileStorageService fileStorageService;
    private final SysParamService sysParamService;
    private final OperationLogService operationLogService;

    public KbDocumentService(KbDocumentMapper documentMapper,
                             KbChunkMapper chunkMapper,
                             KbSparseIndexMapper sparseIndexMapper,
                             KbIndexService indexService,
                             DocumentParser documentParser,
                             FileStorageService fileStorageService,
                             SysParamService sysParamService,
                             OperationLogService operationLogService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.sparseIndexMapper = sparseIndexMapper;
        this.indexService = indexService;
        this.documentParser = documentParser;
        this.fileStorageService = fileStorageService;
        this.sysParamService = sysParamService;
        this.operationLogService = operationLogService;
    }

    // ================= M10-4-1 文档列表与检索 =================

    public PageResult<DocumentVO> page(DocumentQuery query) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getIsDeleted, 0)
                .like(query.getDocName() != null && !query.getDocName().isBlank(),
                        KbDocument::getDocName, query.getDocName())
                .eq(query.getDocType() != null, KbDocument::getDocType, query.getDocType())
                .eq(query.getStatus() != null, KbDocument::getStatus, query.getStatus())
                .eq(query.getParseStatus() != null, KbDocument::getParseStatus, query.getParseStatus())
                .orderByDesc(KbDocument::getId);
        List<KbDocument> rows = documentMapper.selectList(wrapper);
        List<DocumentVO> list = new ArrayList<>();
        for (KbDocument doc : rows) {
            list.add(toVO(doc));
        }
        return PageResult.of(list, (long) rows.size(), query.getPageNo(), query.getPageSize());
    }

    public DocumentVO detail(Long id) {
        return toVO(getDoc(id));
    }

    public List<ChunkVO> chunks(Long docId) {
        getDoc(docId);
        List<KbChunk> rows = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, docId)
                .eq(KbChunk::getIsDeleted, 0)
                .orderByAsc(KbChunk::getChunkIndex));
        List<ChunkVO> list = new ArrayList<>();
        for (KbChunk chunk : rows) {
            ChunkVO vo = new ChunkVO();
            vo.setId(chunk.getId());
            vo.setChunkIndex(chunk.getChunkIndex());
            vo.setSectionNo(chunk.getSectionNo());
            vo.setSectionTitle(chunk.getSectionTitle());
            vo.setCharCount(chunk.getCharCount());
            vo.setChunkText(chunk.getChunkText());
            vo.setHasVector(chunk.getEmbedding() != null);
            list.add(vo);
        }
        return list;
    }

    // ================= M10-1-1 文档上传 =================

    @Transactional
    public Long upload(MultipartFile file, String docName, Integer docType,
                       String tags, String remark, String version, LocalDate effectiveDate) {
        String fileType = validateFile(file);
        KbDocument doc = new KbDocument();
        doc.setDocName(docName);
        doc.setDocType(docType);
        doc.setVersion(version == null || version.isBlank() ? "1.0" : version);
        doc.setEffectiveDate(effectiveDate);
        doc.setStatus(1);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setParseStatus(1);
        doc.setChunkCount(0);
        doc.setTags(tags);
        doc.setRemark(remark);
        doc.setUploadBy(currentUserId());
        doc.setUploadAt(LocalDateTime.now());
        doc.setIsDeleted(0);
        documentMapper.insert(doc);

        FileStorageService.StoredFile stored = store(file, doc.getId());
        doc.setFilePath(stored.getRelativePath());
        doc.setFileSize(stored.getSize());
        documentMapper.updateById(doc);

        autoParse(doc);
        operationLogService.record("M10", "upload", "kb_document", doc.getId(), doc.getDocName(),
                "上传 " + fileType + " 文件并解析，parse_status=" + doc.getParseStatus());
        return doc.getId();
    }

    // ================= M10-1-2 重新解析 =================

    @Transactional
    public void reparse(Long id) {
        KbDocument doc = getDoc(id);
        if (doc.getFilePath() == null) {
            throw new BizException(ResultCode.NO_DATA, "该文档无上传文件（正文补录创建），请通过正文补录更新");
        }
        doc.setParseStatus(2);
        documentMapper.updateById(doc);
        try (InputStream input = fileStorageService.load(doc.getFilePath()).getInputStream()) {
            parseChunkIndex(doc, documentParser.parse(doc.getFileType(), input));
            doc.setParseStatus(3);
        } catch (BizException e) {
            doc.setParseStatus(4);
            doc.setRemark(appendRemark(doc.getRemark(), e.getMessage()));
            documentMapper.updateById(doc);
            throw e;
        } catch (IOException e) {
            doc.setParseStatus(4);
            doc.setRemark(appendRemark(doc.getRemark(), "文件读取失败"));
            documentMapper.updateById(doc);
            throw new BizException(ResultCode.UNSUPPORTED, "文件读取失败：" + e.getMessage());
        }
        documentMapper.updateById(doc);
        operationLogService.record("M10", "parse", "kb_document", doc.getId(), doc.getDocName(),
                "重新解析并重建索引，切块 " + doc.getChunkCount() + " 个");
    }

    // ================= M10-1-3 解析结果校对（正文补录） =================

    @Transactional
    public void saveText(Long id, String text) {
        KbDocument doc = getDoc(id);
        parseChunkIndex(doc, ParsedDoc.fromPlainText(text));
        doc.setParseStatus(3);
        documentMapper.updateById(doc);
        operationLogService.record("M10", "text-fix", "kb_document", doc.getId(), doc.getDocName(),
                "正文补录/校对后重建索引，切块 " + doc.getChunkCount() + " 个");
    }

    // ================= M10-4-2 版本与生效日期维护 =================

    @Transactional
    public void update(Long id, DocumentSaveRequest request) {
        KbDocument doc = getDoc(id);
        doc.setDocName(request.getDocName());
        doc.setDocType(request.getDocType());
        doc.setTags(request.getTags());
        doc.setVersion(request.getVersion());
        doc.setEffectiveDate(request.getEffectiveDate());
        doc.setExpireDate(request.getExpireDate());
        doc.setRemark(request.getRemark());
        documentMapper.updateById(doc);
        operationLogService.record("M10", "update", "kb_document", doc.getId(), doc.getDocName(),
                "维护文档元数据（版本 " + doc.getVersion() + "，生效日期 " + doc.getEffectiveDate() + "）");
    }

    /** 新版本：同文档名新建一行；带文件则解析新文件，否则复制来源版本切块 */
    @Transactional
    public Long newVersion(Long sourceId, String version, LocalDate effectiveDate,
                           String remark, MultipartFile file) {
        KbDocument source = getDoc(sourceId);
        KbDocument doc = new KbDocument();
        doc.setDocName(source.getDocName());
        doc.setDocType(source.getDocType());
        doc.setVersion(version);
        doc.setEffectiveDate(effectiveDate);
        doc.setStatus(1);
        doc.setTags(source.getTags());
        doc.setRemark(remark);
        doc.setUploadBy(currentUserId());
        doc.setUploadAt(LocalDateTime.now());
        doc.setChunkCount(0);
        doc.setIsDeleted(0);
        documentMapper.insert(doc);

        if (file != null && !file.isEmpty()) {
            String fileType = validateFile(file);
            FileStorageService.StoredFile stored = store(file, doc.getId());
            doc.setFilePath(stored.getRelativePath());
            doc.setFileSize(stored.getSize());
            doc.setFileType(fileType);
            doc.setParseStatus(1);
            documentMapper.updateById(doc);
            autoParse(doc);
        } else {
            copyChunks(source, doc);
            doc.setParseStatus(3);
            documentMapper.updateById(doc);
        }
        operationLogService.record("M10", "new-version", "kb_document", doc.getId(), doc.getDocName(),
                "由版本 " + source.getVersion() + " 升版至 " + version + "，生效日期 " + effectiveDate);
        return doc.getId();
    }

    // ================= M10-4-3 启用 / 停用 =================

    @Transactional
    public void toggle(Long id) {
        KbDocument doc = getDoc(id);
        int next = doc.getStatus() != null && doc.getStatus() == 1 ? 2 : 1;
        doc.setStatus(next);
        documentMapper.updateById(doc);
        operationLogService.record("M10", "toggle", "kb_document", doc.getId(), doc.getDocName(),
                next == 1 ? "启用，恢复参与召回" : "停用，不再参与默认召回");
    }

    // ================= M10-4-5 删除与索引清理 =================

    @Transactional
    public void delete(Long id) {
        KbDocument doc = getDoc(id);
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, id));
        for (KbChunk chunk : chunks) {
            sparseIndexMapper.deleteByChunkIdPhysical(chunk.getId());
        }
        chunkMapper.deleteByDocIdPhysical(id);
        documentMapper.deleteByIdPhysical(id);
        operationLogService.record("M10", "delete", "kb_document", id, doc.getDocName(),
                "物理删除文档并清理切块 " + chunks.size() + " 个与倒排索引");
    }

    // ================= 下载 =================

    public Resource loadFile(Long id) {
        KbDocument doc = getDoc(id);
        if (doc.getFilePath() == null) {
            throw new BizException(ResultCode.NO_DATA, "该文档无上传文件（正文补录创建）");
        }
        try {
            return fileStorageService.load(doc.getFilePath());
        } catch (IOException e) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在：" + doc.getFilePath());
        }
    }

    public String fileNameOf(Long id) {
        KbDocument doc = getDoc(id);
        String extension = doc.getFileType() == null ? "" : "." + doc.getFileType();
        return doc.getDocName() + extension;
    }

    // ================= 内部：解析切块建索引 =================

    /** 上传后自动解析：md/docx 走解析器，pdf 记失败并提示补录 */
    private void autoParse(KbDocument doc) {
        if (!"md".equals(doc.getFileType()) && !"docx".equals(doc.getFileType())) {
            doc.setParseStatus(4);
            doc.setRemark(appendRemark(doc.getRemark(),
                    "暂不支持自动解析 pdf 正文，请通过「正文补录」粘贴文本后重建索引"));
            documentMapper.updateById(doc);
            return;
        }
        try (InputStream input = fileStorageService.load(doc.getFilePath()).getInputStream()) {
            parseChunkIndex(doc, documentParser.parse(doc.getFileType(), input));
            doc.setParseStatus(3);
        } catch (BizException e) {
            doc.setParseStatus(4);
            doc.setRemark(appendRemark(doc.getRemark(), e.getMessage()));
        } catch (IOException e) {
            doc.setParseStatus(4);
            doc.setRemark(appendRemark(doc.getRemark(), "文件读取失败"));
        }
        documentMapper.updateById(doc);
    }

    /** 解析 → 按参数切块 → 替换旧切块 → 重建该文档索引（M10-3-3 增量单位是文档） */
    private void parseChunkIndex(KbDocument doc, ParsedDoc parsed) {
        TextChunker chunker = new TextChunker(
                sysParamService.getInt("kb_chunk_size", 500),
                sysParamService.getInt("kb_chunk_overlap", 50));
        List<TextChunker.ChunkDraft> drafts = chunker.chunk(parsed);

        // 旧切块物理删除（连带其倒排）——切块是文档派生数据，唯一键不含 is_deleted，
        // 走 BaseMapper.delete 会被全局 logic-delete-field 降级成 UPDATE 撞唯一键
        List<KbChunk> old = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, doc.getId()));
        for (KbChunk chunk : old) {
            sparseIndexMapper.deleteByChunkIdPhysical(chunk.getId());
        }
        chunkMapper.deleteByDocIdPhysical(doc.getId());

        for (TextChunker.ChunkDraft draft : drafts) {
            KbChunk chunk = new KbChunk();
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(draft.getIndex());
            chunk.setChunkText(draft.getText());
            chunk.setSectionNo(draft.getSectionNo());
            chunk.setSectionTitle(draft.getSectionTitle());
            chunk.setCharCount(draft.getText().length());
            chunk.setIsDeleted(0);
            chunkMapper.insert(chunk);
        }
        indexService.rebuildDoc(doc.getId());
        doc.setChunkCount(drafts.size());
    }

    /** 不带文件的新版本：复制来源版本切块文本后重建索引 */
    private void copyChunks(KbDocument source, KbDocument target) {
        List<KbChunk> rows = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, source.getId())
                .eq(KbChunk::getIsDeleted, 0)
                .orderByAsc(KbChunk::getChunkIndex));
        for (KbChunk src : rows) {
            KbChunk chunk = new KbChunk();
            chunk.setDocId(target.getId());
            chunk.setChunkIndex(src.getChunkIndex());
            chunk.setChunkText(src.getChunkText());
            chunk.setSectionNo(src.getSectionNo());
            chunk.setSectionTitle(src.getSectionTitle());
            chunk.setCharCount(src.getCharCount());
            chunk.setIsDeleted(0);
            chunkMapper.insert(chunk);
        }
        indexService.rebuildDoc(target.getId());
        target.setChunkCount(rows.size());
    }

    // ================= 内部：工具 =================

    private KbDocument getDoc(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null || doc.getIsDeleted() != null && doc.getIsDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "知识文档不存在");
        }
        return doc;
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "文件超过 10MB 上限");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = FileStorageService.extensionOf(original).replace(".", "").toLowerCase();
        if (!ALLOWED_TYPES.contains(extension)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "仅支持 pdf / docx / md 文件");
        }
        return extension;
    }

    private FileStorageService.StoredFile store(MultipartFile file, Long docId) {
        try {
            return fileStorageService.store(file, "kb/" + docId);
        } catch (IOException e) {
            throw new BizException(ResultCode.UNSUPPORTED, "文件存储失败：" + e.getMessage());
        }
    }

    private Long currentUserId() {
        CurrentUser user = AuthContext.get();
        return user == null ? null : user.getUserId();
    }

    private static String appendRemark(String remark, String message) {
        String note = remark == null || remark.isBlank() ? message : remark + "；" + message;
        return note.length() > 500 ? note.substring(0, 500) : note;
    }

    private DocumentVO toVO(KbDocument doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setDocName(doc.getDocName());
        vo.setDocType(doc.getDocType());
        vo.setDocTypeName(docTypeName(doc.getDocType()));
        vo.setVersion(doc.getVersion());
        vo.setEffectiveDate(doc.getEffectiveDate());
        vo.setExpireDate(doc.getExpireDate());
        vo.setStatus(doc.getStatus());
        vo.setStatusName(statusName(doc.getStatus()));
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setHasFile(doc.getFilePath() != null);
        vo.setParseStatus(doc.getParseStatus());
        vo.setParseStatusName(parseStatusName(doc.getParseStatus()));
        vo.setChunkCount(doc.getChunkCount());
        vo.setTags(doc.getTags());
        vo.setRemark(doc.getRemark());
        vo.setUploadAt(doc.getUploadAt());
        return vo;
    }

    static String docTypeName(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case 1 -> "制度";
            case 2 -> "规范";
            case 3 -> "操作手册";
            case 4 -> "流程说明";
            default -> "";
        };
    }

    static String statusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "启用";
            case 2 -> "停用";
            case 3 -> "失效";
            default -> "";
        };
    }

    static String parseStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "待解析";
            case 2 -> "解析中";
            case 3 -> "解析成功";
            case 4 -> "解析失败";
            default -> "";
        };
    }
}
