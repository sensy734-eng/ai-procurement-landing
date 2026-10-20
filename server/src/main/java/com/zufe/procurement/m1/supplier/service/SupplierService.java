package com.zufe.procurement.m1.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.common.storage.FileStorageService;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.entity.MaterialCategory;
import com.zufe.procurement.m1.material.mapper.MaterialCategoryMapper;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.supplier.dto.ContactSaveRequest;
import com.zufe.procurement.m1.supplier.dto.ScopeSaveRequest;
import com.zufe.procurement.m1.supplier.dto.SupplierQuery;
import com.zufe.procurement.m1.supplier.dto.SupplierSaveRequest;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.entity.SupplierAttachment;
import com.zufe.procurement.m1.supplier.entity.SupplierContact;
import com.zufe.procurement.m1.supplier.entity.SupplierScope;
import com.zufe.procurement.m1.supplier.mapper.SupplierAttachmentMapper;
import com.zufe.procurement.m1.supplier.mapper.SupplierContactMapper;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m1.supplier.mapper.SupplierScopeMapper;
import com.zufe.procurement.m1.supplier.vo.AttachmentVO;
import com.zufe.procurement.m1.supplier.vo.ContactVO;
import com.zufe.procurement.m1.supplier.vo.ScopeVO;
import com.zufe.procurement.m1.supplier.vo.SupplierVO;
import com.zufe.procurement.m11.system.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供应商服务。覆盖功能点：
 * M1-2-1 列表与检索、M1-2-2 新增编辑、M1-2-3 启停、
 * M1-2-4 联系人维护、M1-2-5 资质附件、M1-2-6 供货范围。
 * 口径：编码唯一性连带已删除记录查；编码创建后不可改；
 * 停用后不可用于新订单（M1-2-3，由 options 与后续 M3 落地）；
 * 主联系人同一供应商至多一个。
 */
@Slf4j
@Service
public class SupplierService {

    /** 资质附件大小上限。参数表未设附件大小项，取常量并在此说明 */
    private static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "docx");

    private final SupplierMapper supplierMapper;
    private final SupplierContactMapper contactMapper;
    private final SupplierAttachmentMapper attachmentMapper;
    private final SupplierScopeMapper scopeMapper;
    private final DictItemMapper dictItemMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final FileStorageService fileStorageService;
    private final OperationLogService operationLogService;

    public SupplierService(SupplierMapper supplierMapper,
                           SupplierContactMapper contactMapper,
                           SupplierAttachmentMapper attachmentMapper,
                           SupplierScopeMapper scopeMapper,
                           DictItemMapper dictItemMapper,
                           MaterialMapper materialMapper,
                           MaterialCategoryMapper categoryMapper,
                           FileStorageService fileStorageService,
                           OperationLogService operationLogService) {
        this.supplierMapper = supplierMapper;
        this.contactMapper = contactMapper;
        this.attachmentMapper = attachmentMapper;
        this.scopeMapper = scopeMapper;
        this.dictItemMapper = dictItemMapper;
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.fileStorageService = fileStorageService;
        this.operationLogService = operationLogService;
    }

    // ---------------- M1-2-1 列表与检索 ----------------

    public PageResult<SupplierVO> page(SupplierQuery query) {
        Page<Supplier> page = new Page<>(Math.max(query.getPageNo(), 1),
                Math.min(Math.max(query.getPageSize(), 1), 100));
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        Page<Supplier> result = supplierMapper.selectPage(page, new LambdaQueryWrapper<Supplier>()
                .and(keyword != null, w -> w.like(Supplier::getSupplierNo, keyword)
                        .or().like(Supplier::getSupplierName, keyword))
                .eq(query.getSupplierType() != null, Supplier::getSupplierType, query.getSupplierType())
                .eq(query.getStatus() != null, Supplier::getStatus, query.getStatus())
                .orderByDesc(Supplier::getId));
        List<Supplier> suppliers = result.getRecords();
        List<SupplierVO> records = enrich(suppliers);
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** M1-2-1 详情，含联系人、供货范围与附件 */
    public SupplierVO detail(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        return enrich(List.of(supplier)).get(0);
    }

    /** M1-2-1 可选供应商，仅启用，供新订单下拉（M1-2-3 的停用语义在此落地） */
    public List<SupplierVO> options() {
        List<Supplier> list = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getStatus, 1)
                .orderByAsc(Supplier::getSupplierNo));
        return enrich(list);
    }

    // ---------------- M1-2-2 新增与编辑 ----------------

    @Transactional
    public Long create(SupplierSaveRequest request) {
        Supplier supplier = new Supplier();
        applyRequest(supplier, request);
        supplier.setStatus(1);
        supplierMapper.insert(supplier);
        operationLogService.record("M1", "create", "supplier", supplier.getId(), supplier.getSupplierNo(),
                "新增供应商 " + supplier.getSupplierNo());
        return supplier.getId();
    }

    @Transactional
    public void update(Long id, SupplierSaveRequest request) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        if (request.getSupplierNo() != null && !request.getSupplierNo().trim().equals(supplier.getSupplierNo())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "供应商编码创建后不可修改");
        }
        applyRequest(supplier, request);
        supplierMapper.updateById(supplier);
        operationLogService.record("M1", "update", "supplier", id, supplier.getSupplierNo(),
                "编辑供应商 " + supplier.getSupplierNo());
    }

    // ---------------- M1-2-3 启用停用 ----------------

    @Transactional
    public void toggleStatus(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        int next = supplier.getStatus() != null && supplier.getStatus() == 1 ? 2 : 1;
        supplier.setStatus(next);
        supplierMapper.updateById(supplier);
        String detail = next == 2
                ? "停用供应商 " + supplier.getSupplierNo() + "，停用后不可用于新订单，存量单据不受影响"
                : "启用供应商 " + supplier.getSupplierNo();
        operationLogService.record("M1", "toggle", "supplier", id, supplier.getSupplierNo(), detail);
    }

    // ---------------- M1-2-4 联系人 ----------------

    public List<ContactVO> contacts(Long supplierId) {
        requireSupplier(supplierId);
        List<ContactVO> result = new ArrayList<>();
        for (SupplierContact contact : contactMapper.selectBySupplierId(supplierId)) {
            ContactVO vo = new ContactVO();
            vo.setId(contact.getId());
            vo.setContactName(contact.getContactName());
            vo.setPosition(contact.getPosition());
            vo.setPhone(contact.getPhone());
            vo.setEmail(contact.getEmail());
            vo.setIsDefault(contact.getIsDefault());
            vo.setStatus(contact.getStatus());
            vo.setRemark(contact.getRemark());
            result.add(vo);
        }
        return result;
    }

    /** M1-2-4 联系人整体替换。无唯一键约束，逻辑删除后重建 */
    @Transactional
    public void saveContacts(Long supplierId, ContactSaveRequest request) {
        requireSupplier(supplierId);
        List<ContactSaveRequest.Item> items = request.getContacts() == null ? List.of() : request.getContacts();
        long defaultCount = items.stream()
                .filter(item -> item.getIsDefault() != null && item.getIsDefault() == 1).count();
        if (defaultCount > 1) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "主联系人只能有一个");
        }
        for (ContactSaveRequest.Item item : items) {
            if (!StringUtils.hasText(item.getContactName())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "联系人姓名不能为空");
            }
        }
        contactMapper.delete(new LambdaQueryWrapper<SupplierContact>()
                .eq(SupplierContact::getSupplierId, supplierId));
        for (ContactSaveRequest.Item item : items) {
            SupplierContact contact = new SupplierContact();
            contact.setSupplierId(supplierId);
            contact.setContactName(item.getContactName().trim());
            contact.setPosition(item.getPosition());
            contact.setPhone(item.getPhone());
            contact.setEmail(item.getEmail());
            contact.setIsDefault(item.getIsDefault() == null ? 0 : item.getIsDefault());
            contact.setStatus(item.getStatus() == null ? 1 : item.getStatus());
            contact.setRemark(item.getRemark());
            contactMapper.insert(contact);
        }
        operationLogService.record("M1", "update", "supplier_contact", supplierId, null,
                "保存供应商联系人，共 " + items.size() + " 条");
    }

    // ---------------- M1-2-6 供货范围 ----------------

    public List<ScopeVO> scopes(Long supplierId) {
        requireSupplier(supplierId);
        List<ScopeVO> result = new ArrayList<>();
        for (SupplierScope scope : scopeMapper.selectBySupplierId(supplierId)) {
            ScopeVO vo = new ScopeVO();
            vo.setId(scope.getId());
            vo.setScopeType(scope.getScopeType());
            vo.setScopeId(scope.getScopeId());
            vo.setScopeName(resolveScopeName(scope.getScopeType(), scope.getScopeId()));
            vo.setRemark(scope.getRemark());
            result.add(vo);
        }
        return result;
    }

    /** M1-2-6 供货范围整体替换。唯一键不含 is_deleted，物理删除后重建 */
    @Transactional
    public void saveScopes(Long supplierId, ScopeSaveRequest request) {
        requireSupplier(supplierId);
        List<ScopeSaveRequest.Item> items = request.getScopes() == null ? List.of() : request.getScopes();
        Set<String> keys = new HashSet<>();
        for (ScopeSaveRequest.Item item : items) {
            if (item.getScopeType() == null || (item.getScopeType() != 1 && item.getScopeType() != 2)) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "供货范围类型不合法（1 按物料、2 按分类）");
            }
            if (item.getScopeId() == null) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "供货范围对象不能为空");
            }
            if (resolveScopeName(item.getScopeType(), item.getScopeId()) == null) {
                throw new BizException(ResultCode.NOT_FOUND, "供货范围对象不存在");
            }
            if (!keys.add(item.getScopeType() + ":" + item.getScopeId())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "供货范围重复");
            }
        }
        scopeMapper.physicalDeleteBySupplierId(supplierId);
        for (ScopeSaveRequest.Item item : items) {
            SupplierScope scope = new SupplierScope();
            scope.setSupplierId(supplierId);
            scope.setScopeType(item.getScopeType());
            scope.setScopeId(item.getScopeId());
            scope.setStatus(1);
            scope.setRemark(item.getRemark());
            scopeMapper.insert(scope);
        }
        operationLogService.record("M1", "update", "supplier_scope", supplierId, null,
                "保存供应商供货范围，共 " + items.size() + " 条");
    }

    // ---------------- M1-2-5 资质附件 ----------------

    public List<AttachmentVO> attachments(Long supplierId) {
        requireSupplier(supplierId);
        List<AttachmentVO> result = new ArrayList<>();
        for (SupplierAttachment attachment : attachmentMapper.selectBySupplierId(supplierId)) {
            AttachmentVO vo = new AttachmentVO();
            vo.setId(attachment.getId());
            vo.setFileName(attachment.getFileName());
            vo.setFileType(attachment.getFileType());
            vo.setFileSize(attachment.getFileSize());
            vo.setExpireDate(attachment.getExpireDate());
            vo.setRemark(attachment.getRemark());
            vo.setCreatedAt(attachment.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public Long uploadAttachment(Long supplierId, MultipartFile file, LocalDate expireDate, String remark) {
        requireSupplier(supplierId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择要上传的文件");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = FileStorageService.extensionOf(originalName).replace(".", "");
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "仅支持 pdf、jpg、jpeg、png、docx 格式，当前为 " + (extension.isEmpty() ? "无扩展名" : extension));
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "文件大小超过 10MB 上限");
        }
        FileStorageService.StoredFile stored;
        try {
            stored = fileStorageService.store(file, "supplier-attachments/" + supplierId);
        } catch (IOException e) {
            log.error("资质附件落盘失败 supplierId={}", supplierId, e);
            throw new BizException(ResultCode.INVALID_PARAMETER, "文件保存失败：" + e.getMessage());
        }
        SupplierAttachment attachment = new SupplierAttachment();
        attachment.setSupplierId(supplierId);
        attachment.setFileName(originalName);
        attachment.setFileType(extension.toLowerCase());
        attachment.setFileSize(stored.getSize());
        attachment.setFilePath(stored.getRelativePath());
        attachment.setExpireDate(expireDate);
        attachment.setRemark(remark);
        attachmentMapper.insert(attachment);
        operationLogService.record("M1", "upload", "supplier_attachment", attachment.getId(), originalName,
                "上传供应商资质附件 " + originalName);
        return attachment.getId();
    }

    /** 下载附件：返回文件资源与原始文件名，由控制器拼响应头 */
    public DownloadedAttachment downloadAttachment(Long attachmentId) {
        SupplierAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "附件不存在");
        }
        if (!fileStorageService.exists(attachment.getFilePath())) {
            throw new BizException(ResultCode.NOT_FOUND, "附件文件已丢失");
        }
        try {
            DownloadedAttachment downloaded = new DownloadedAttachment();
            downloaded.setResource(fileStorageService.load(attachment.getFilePath()));
            downloaded.setFileName(attachment.getFileName());
            downloaded.setFileType(attachment.getFileType());
            return downloaded;
        } catch (IOException e) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "附件读取失败：" + e.getMessage());
        }
    }

    /** M1-2-5 删除附件记录，文件保留在盘上作为审计依据 */
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        SupplierAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "附件不存在");
        }
        attachmentMapper.deleteById(attachmentId);
        operationLogService.record("M1", "delete", "supplier_attachment", attachmentId,
                attachment.getFileName(), "删除供应商资质附件记录，文件保留");
    }

    // ---------------- 内部方法 ----------------

    private void applyRequest(Supplier supplier, SupplierSaveRequest request) {
        String supplierNo = requireText(request.getSupplierNo(), "供应商编码");
        if (supplierNo.length() > 50) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "供应商编码超长（50 字以内）");
        }
        if (supplier.getId() == null && supplierMapper.countBySupplierNoIncludeDeleted(supplierNo) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "供应商编码已存在：" + supplierNo);
        }
        String supplierName = requireText(request.getSupplierName(), "供应商名称");
        if (request.getSupplierType() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择供应商类型");
        }
        // supplier_type 字段存的是字典项编码（1、2、3），不是字典项 id，按编码校验
        if (dictItemMapper.countByTypeCodeAndItemCode("supplier_type",
                String.valueOf(request.getSupplierType())) == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商类型不存在");
        }
        if (StringUtils.hasText(request.getTaxNo()) && !request.getTaxNo().trim().matches("[0-9A-Z]{15,20}")) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "统一社会信用代码格式不正确");
        }
        supplier.setSupplierNo(supplierNo);
        supplier.setSupplierName(supplierName);
        supplier.setSupplierType(request.getSupplierType());
        supplier.setTaxNo(blankToNull(request.getTaxNo()));
        supplier.setAddress(blankToNull(request.getAddress()));
        supplier.setPhone(blankToNull(request.getPhone()));
        supplier.setEmail(blankToNull(request.getEmail()));
        supplier.setBankName(blankToNull(request.getBankName()));
        supplier.setBankAccount(blankToNull(request.getBankAccount()));
        supplier.setRemark(request.getRemark());
    }

    private List<SupplierVO> enrich(List<Supplier> suppliers) {
        if (suppliers.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> typeNames = new HashMap<>();
        for (DictItem item : dictItemMapper.selectAllByTypeCode("supplier_type")) {
            typeNames.put(item.getItemCode(), item.getItemName());
        }
        List<Long> ids = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            ids.add(supplier.getId());
        }
        Map<Long, List<SupplierContact>> contactsBySupplier = groupContacts(ids);
        Map<Long, Long> scopeCounts = countBySupplier(ids, scopeMapper);
        Map<Long, Long> attachmentCounts = countAttachments(ids);

        List<SupplierVO> result = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            SupplierVO vo = new SupplierVO();
            vo.setId(supplier.getId());
            vo.setSupplierNo(supplier.getSupplierNo());
            vo.setSupplierName(supplier.getSupplierName());
            vo.setSupplierType(supplier.getSupplierType());
            vo.setSupplierTypeName(typeNames.get(String.valueOf(supplier.getSupplierType())));
            vo.setTaxNo(supplier.getTaxNo());
            vo.setAddress(supplier.getAddress());
            vo.setPhone(supplier.getPhone());
            vo.setEmail(supplier.getEmail());
            vo.setBankName(supplier.getBankName());
            vo.setBankAccount(supplier.getBankAccount());
            vo.setStatus(supplier.getStatus());
            vo.setRemark(supplier.getRemark());
            List<SupplierContact> contacts = contactsBySupplier.getOrDefault(supplier.getId(), List.of());
            vo.setContactCount(contacts.size());
            vo.setDefaultContactName(contacts.stream()
                    .filter(contact -> contact.getIsDefault() != null && contact.getIsDefault() == 1)
                    .map(SupplierContact::getContactName)
                    .findFirst().orElse(contacts.isEmpty() ? null : contacts.get(0).getContactName()));
            vo.setScopeCount(scopeCounts.getOrDefault(supplier.getId(), 0L).intValue());
            vo.setAttachmentCount(attachmentCounts.getOrDefault(supplier.getId(), 0L).intValue());
            vo.setCreatedAt(supplier.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    private Map<Long, List<SupplierContact>> groupContacts(List<Long> supplierIds) {
        List<SupplierContact> all = contactMapper.selectList(new LambdaQueryWrapper<SupplierContact>()
                .in(SupplierContact::getSupplierId, supplierIds)
                .orderByAsc(SupplierContact::getId));
        Map<Long, List<SupplierContact>> map = new HashMap<>();
        for (SupplierContact contact : all) {
            map.computeIfAbsent(contact.getSupplierId(), key -> new ArrayList<>()).add(contact);
        }
        return map;
    }

    private Map<Long, Long> countBySupplier(List<Long> supplierIds, SupplierScopeMapper mapper) {
        List<SupplierScope> all = mapper.selectList(new LambdaQueryWrapper<SupplierScope>()
                .in(SupplierScope::getSupplierId, supplierIds));
        Map<Long, Long> map = new HashMap<>();
        for (SupplierScope scope : all) {
            map.merge(scope.getSupplierId(), 1L, Long::sum);
        }
        return map;
    }

    private Map<Long, Long> countAttachments(List<Long> supplierIds) {
        List<SupplierAttachment> all = attachmentMapper.selectList(new LambdaQueryWrapper<SupplierAttachment>()
                .in(SupplierAttachment::getSupplierId, supplierIds));
        Map<Long, Long> map = new HashMap<>();
        for (SupplierAttachment attachment : all) {
            map.merge(attachment.getSupplierId(), 1L, Long::sum);
        }
        return map;
    }

    private String resolveScopeName(Integer scopeType, Long scopeId) {
        if (scopeType == null || scopeId == null) {
            return null;
        }
        if (scopeType == 1) {
            Material material = materialMapper.selectById(scopeId);
            return material == null ? null : material.getMaterialName();
        }
        MaterialCategory category = categoryMapper.selectById(scopeId);
        return category == null ? null : category.getCategoryName();
    }

    private void requireSupplier(Long supplierId) {
        if (supplierMapper.selectById(supplierId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商不存在");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, field + "不能为空");
        }
        return value.trim();
    }

    /** 下载结果载体，控制器只负责写响应头 */
    public static class DownloadedAttachment {
        private Resource resource;
        private String fileName;
        private String fileType;

        public Resource getResource() {
            return resource;
        }

        public void setResource(Resource resource) {
            this.resource = resource;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }
    }
}
