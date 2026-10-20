package com.zufe.procurement.m1.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.CsvWriter;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.material.dto.MaterialQuery;
import com.zufe.procurement.m1.material.dto.MaterialSaveRequest;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.entity.MaterialCategory;
import com.zufe.procurement.m1.material.entity.MaterialExtAttr;
import com.zufe.procurement.m1.material.mapper.MaterialCategoryMapper;
import com.zufe.procurement.m1.material.mapper.MaterialExtAttrMapper;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.material.vo.MaterialImportResultVO;
import com.zufe.procurement.m1.material.vo.MaterialVO;
import com.zufe.procurement.m11.system.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物料服务。覆盖功能点：
 * M1-1-1 列表与检索、M1-1-2 新增、M1-1-3 编辑、M1-1-4 启停、
 * M1-1-5 批量导入导出、M1-1-6 扩展属性维护。
 * 口径：编码唯一性连带已删除记录查；编码创建后不可改；
 * 停用只影响新单据选择，存量单据不受影响；扩展属性随主档整体替换。
 */
@Slf4j
@Service
public class MaterialService {

    private static final int EXPORT_LIMIT = 5000;
    private static final String[] IMPORT_HEADERS =
            {"物料编码", "物料名称", "分类编码", "规格", "品牌", "单位编码", "物料类型", "预警下限", "备注"};

    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final MaterialExtAttrMapper extAttrMapper;
    private final DictItemMapper dictItemMapper;
    private final OperationLogService operationLogService;

    public MaterialService(MaterialMapper materialMapper,
                           MaterialCategoryMapper categoryMapper,
                           MaterialExtAttrMapper extAttrMapper,
                           DictItemMapper dictItemMapper,
                           OperationLogService operationLogService) {
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.extAttrMapper = extAttrMapper;
        this.dictItemMapper = dictItemMapper;
        this.operationLogService = operationLogService;
    }

    // ---------------- M1-1-1 列表与检索 ----------------

    public PageResult<MaterialVO> page(MaterialQuery query) {
        Page<Material> page = new Page<>(Math.max(query.getPageNo(), 1),
                Math.min(Math.max(query.getPageSize(), 1), 100));
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .and(keyword != null, w -> w.like(Material::getMaterialNo, keyword)
                        .or().like(Material::getMaterialName, keyword))
                .eq(query.getCategoryId() != null, Material::getCategoryId, query.getCategoryId())
                .eq(query.getStatus() != null, Material::getStatus, query.getStatus())
                .eq(query.getMaterialType() != null, Material::getMaterialType, query.getMaterialType())
                .orderByDesc(Material::getId);
        Page<Material> result = materialMapper.selectPage(page, wrapper);
        return PageResult.of(enrich(result.getRecords()), result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    /** M1-1-1 详情，含扩展属性（M1-1-6） */
    public MaterialVO detail(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物料不存在");
        }
        return enrich(List.of(material)).get(0);
    }

    /** M1-1-1 可选物料，仅启用，供新单据下拉选择（M1-1-4 的停用语义在此落地） */
    public List<MaterialVO> options() {
        List<Material> list = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getStatus, 1)
                .orderByAsc(Material::getMaterialNo));
        return enrich(list);
    }

    // ---------------- M1-1-2 新增 ----------------

    @Transactional
    public Long create(MaterialSaveRequest request) {
        Material material = new Material();
        applyRequest(material, request);
        material.setStatus(1);
        materialMapper.insert(material);
        replaceExtAttrs(material.getId(), request.getExtAttrs());
        operationLogService.record("M1", "create", "material", material.getId(), material.getMaterialNo(),
                "新增物料 " + material.getMaterialNo());
        return material.getId();
    }

    // ---------------- M1-1-3 编辑 ----------------

    @Transactional
    public void update(Long id, MaterialSaveRequest request) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物料不存在");
        }
        if (request.getMaterialNo() != null && !request.getMaterialNo().trim().equals(material.getMaterialNo())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "物料编码创建后不可修改");
        }
        List<String> changes = new ArrayList<>();
        applyRequest(material, request, changes);
        materialMapper.updateById(material);
        replaceExtAttrs(id, request.getExtAttrs());
        operationLogService.record("M1", "update", "material", id, material.getMaterialNo(),
                "编辑物料 " + material.getMaterialNo()
                        + (changes.isEmpty() ? "" : "；变更：" + String.join("，", changes)));
    }

    // ---------------- M1-1-4 启用停用 ----------------

    @Transactional
    public void toggleStatus(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物料不存在");
        }
        int next = material.getStatus() != null && material.getStatus() == 1 ? 2 : 1;
        material.setStatus(next);
        materialMapper.updateById(material);
        String detail = next == 2
                ? "停用物料 " + material.getMaterialNo() + "，停用后不可在新单据选择，存量单据不受影响"
                : "启用物料 " + material.getMaterialNo();
        operationLogService.record("M1", "toggle", "material", id, material.getMaterialNo(), detail);
    }

    // ---------------- M1-1-5 导入导出 ----------------

    /**
     * M1-1-5 批量导入。逐行校验、逐行落库，返回成功失败明细。
     * 不做单事务：部分成功是设计语义，失败行不影响成功行。
     */
    public MaterialImportResultVO importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择要导入的文件");
        }
        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "文件内容为空");
        }
        MaterialImportResultVO result = new MaterialImportResultVO();
        List<MaterialImportResultVO.Row> rows = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        Map<String, Long> categoryIdByNo = categoryIdByNo();
        Map<String, Long> unitIdByCode = unitIdByCode();
        Set<Integer> materialTypeCodes = materialTypeCodes();

        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = parseCsvLine(lines.get(i));
            if (cells.stream().allMatch(cell -> !StringUtils.hasText(cell))) {
                continue;
            }
            int lineNo = i + 1;
            result.setTotalCount(result.getTotalCount() + 1);
            String materialNo = cell(cells, 0);
            MaterialImportResultVO.Row row = new MaterialImportResultVO.Row();
            row.setLineNo(lineNo);
            row.setMaterialNo(materialNo);
            rows.add(row);
            try {
                if (!StringUtils.hasText(materialNo)) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "物料编码不能为空");
                }
                materialNo = materialNo.trim();
                if (materialNo.length() > 50) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "物料编码超长（50 字以内）");
                }
                if (seenCodes.contains(materialNo)) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "文件内物料编码重复");
                }
                seenCodes.add(materialNo);

                MaterialSaveRequest request = buildRequestFromRow(cells, materialNo,
                        categoryIdByNo, unitIdByCode, materialTypeCodes);
                boolean exists = materialMapper.countByMaterialNoIncludeDeleted(materialNo) > 0;
                if (exists) {
                    Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                            .eq(Material::getMaterialNo, materialNo));
                    if (material == null) {
                        throw new BizException(ResultCode.INVALID_PARAMETER, "该编码已被删除占用，不能更新");
                    }
                    applyRequest(material, request);
                    materialMapper.updateById(material);
                    row.setAction("更新");
                } else {
                    Material material = new Material();
                    applyRequest(material, request);
                    material.setStatus(1);
                    materialMapper.insert(material);
                    row.setAction("新建");
                }
                row.setSuccess(true);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (BizException e) {
                row.setSuccess(false);
                row.setMessage(e.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            } catch (Exception e) {
                row.setSuccess(false);
                row.setMessage("系统错误：" + e.getMessage());
                result.setFailCount(result.getFailCount() + 1);
                log.error("物料导入第 {} 行失败", lineNo, e);
            }
        }
        result.setRows(rows);
        operationLogService.record("M1", "import", "material", null, null,
                "物料导入：共 " + result.getTotalCount() + " 行，成功 " + result.getSuccessCount()
                        + " 行，失败 " + result.getFailCount() + " 行");
        return result;
    }

    /** M1-1-5 按当前筛选条件导出 CSV，带 BOM */
    public byte[] exportCsv(MaterialQuery query) {
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        List<Material> list = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .and(keyword != null, w -> w.like(Material::getMaterialNo, keyword)
                        .or().like(Material::getMaterialName, keyword))
                .eq(query.getCategoryId() != null, Material::getCategoryId, query.getCategoryId())
                .eq(query.getStatus() != null, Material::getStatus, query.getStatus())
                .eq(query.getMaterialType() != null, Material::getMaterialType, query.getMaterialType())
                .orderByAsc(Material::getMaterialNo)
                .last("LIMIT " + EXPORT_LIMIT));
        List<MaterialVO> vos = enrich(list);
        String[] headers = {"物料编码", "物料名称", "分类编码", "分类名称", "规格", "品牌",
                "单位编码", "单位名称", "物料类型", "预警下限", "状态", "备注"};
        List<String[]> data = new ArrayList<>();
        for (MaterialVO vo : vos) {
            data.add(new String[]{
                    vo.getMaterialNo(), vo.getMaterialName(), vo.getCategoryNo(), vo.getCategoryName(),
                    str(vo.getSpec()), str(vo.getBrand()), vo.getUnitCode(), vo.getUnitName(),
                    vo.getMaterialTypeName(), str(vo.getSafetyStock()),
                    vo.getStatus() != null && vo.getStatus() == 1 ? "启用" : "停用", str(vo.getRemark())
            });
        }
        operationLogService.record("M1", "export", "material", null, null,
                "物料导出 " + vos.size() + " 条");
        return CsvWriter.toCsv(headers, data);
    }

    /** M1-1-5 导入模板，仅表头加一行示例 */
    public byte[] template() {
        List<String[]> data = new ArrayList<>();
        data.add(new String[]{"WL-0001", "示例物料", "FL-01", "规格型号", "品牌",
                "PCS", "1", "0", "示例行，导入前请删除"});
        return CsvWriter.toCsv(IMPORT_HEADERS, data);
    }

    // ---------------- 内部方法 ----------------

    /** 校验并填充保存字段；changes 非空时记录变更前后值 */
    private void applyRequest(Material material, MaterialSaveRequest request) {
        applyRequest(material, request, null);
    }

    private void applyRequest(Material material, MaterialSaveRequest request, List<String> changes) {
        String materialNo = requireText(request.getMaterialNo(), "物料编码");
        if (materialNo.length() > 50) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "物料编码超长（50 字以内）");
        }
        if (material.getId() == null && materialMapper.countByMaterialNoIncludeDeleted(materialNo) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "物料编码已存在：" + materialNo);
        }
        String materialName = requireText(request.getMaterialName(), "物料名称");
        if (request.getCategoryId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择物料分类");
        }
        MaterialCategory category = categoryMapper.selectById(request.getCategoryId());
        if (category == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物料分类不存在");
        }
        if (request.getUnitId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择计量单位");
        }
        if (dictItemMapper.countByTypeCodeAndId("unit", request.getUnitId()) == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "计量单位不存在或已停用");
        }
        if (request.getMaterialType() == null || !materialTypeCodes().contains(request.getMaterialType())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "物料类型不合法（1 原材料、2 辅料、3 外购件、4 备件）");
        }
        BigDecimal safetyStock = request.getSafetyStock() == null ? BigDecimal.ZERO : request.getSafetyStock();
        if (safetyStock.signum() < 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "预警下限不能为负");
        }

        if (changes != null) {
            track(changes, "名称", material.getMaterialName(), materialName);
            track(changes, "分类", nameOfCategory(material.getCategoryId()), category.getCategoryName());
            track(changes, "规格", material.getSpec(), request.getSpec());
            track(changes, "品牌", material.getBrand(), request.getBrand());
            track(changes, "单位", nameOfUnit(material.getUnitId()), nameOfUnit(request.getUnitId()));
            track(changes, "类型", nameOfMaterialType(material.getMaterialType()),
                    nameOfMaterialType(request.getMaterialType()));
            track(changes, "预警下限", material.getSafetyStock(), safetyStock);
            track(changes, "备注", material.getRemark(), request.getRemark());
        }
        material.setMaterialNo(materialNo);
        material.setMaterialName(materialName);
        material.setCategoryId(request.getCategoryId());
        material.setSpec(request.getSpec());
        material.setBrand(request.getBrand());
        material.setUnitId(request.getUnitId());
        material.setMaterialType(request.getMaterialType());
        material.setSafetyStock(safetyStock);
        material.setRemark(request.getRemark());
    }

    /** M1-1-6 扩展属性整体替换：唯一键不含 is_deleted，物理删除后重建 */
    private void replaceExtAttrs(Long materialId, List<MaterialSaveRequest.ExtAttrItem> items) {
        extAttrMapper.physicalDeleteByMaterialId(materialId);
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> names = new HashSet<>();
        int sort = 0;
        for (MaterialSaveRequest.ExtAttrItem item : items) {
            if (!StringUtils.hasText(item.getAttrName())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "扩展属性名不能为空");
            }
            String attrName = item.getAttrName().trim();
            if (attrName.length() > 50) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "扩展属性名超长（50 字以内）");
            }
            if (!names.add(attrName)) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "扩展属性名重复：" + attrName);
            }
            MaterialExtAttr attr = new MaterialExtAttr();
            attr.setMaterialId(materialId);
            attr.setAttrName(attrName);
            attr.setAttrValue(StringUtils.hasText(item.getAttrValue()) ? item.getAttrValue().trim() : null);
            attr.setSortNo(item.getSortNo() == null ? sort++ : item.getSortNo());
            extAttrMapper.insert(attr);
        }
    }

    private MaterialSaveRequest buildRequestFromRow(List<String> cells, String materialNo,
                                                    Map<String, Long> categoryIdByNo,
                                                    Map<String, Long> unitIdByCode,
                                                    Set<Integer> materialTypeCodes) {
        MaterialSaveRequest request = new MaterialSaveRequest();
        request.setMaterialNo(materialNo);
        request.setMaterialName(requireText(cell(cells, 1), "物料名称"));
        String categoryNo = cell(cells, 2);
        if (!StringUtils.hasText(categoryNo) || !categoryIdByNo.containsKey(categoryNo.trim())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "分类编码不存在：" + categoryNo);
        }
        request.setCategoryId(categoryIdByNo.get(categoryNo.trim()));
        request.setSpec(blankToNull(cell(cells, 3)));
        request.setBrand(blankToNull(cell(cells, 4)));
        String unitCode = cell(cells, 5);
        if (!StringUtils.hasText(unitCode) || !unitIdByCode.containsKey(unitCode.trim().toUpperCase())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单位编码不存在：" + unitCode);
        }
        request.setUnitId(unitIdByCode.get(unitCode.trim().toUpperCase()));
        String typeText = cell(cells, 6);
        Integer materialType = parseInt(typeText);
        if (materialType == null || !materialTypeCodes.contains(materialType)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "物料类型不合法：" + typeText);
        }
        request.setMaterialType(materialType);
        String safetyText = cell(cells, 7);
        if (StringUtils.hasText(safetyText)) {
            try {
                BigDecimal safetyStock = new BigDecimal(safetyText.trim());
                if (safetyStock.signum() < 0) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "预警下限不能为负");
                }
                request.setSafetyStock(safetyStock);
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "预警下限不是数字：" + safetyText);
            }
        }
        request.setRemark(blankToNull(cell(cells, 8)));
        return request;
    }

    private List<MaterialVO> enrich(List<Material> materials) {
        if (materials.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> categoryNames = new HashMap<>();
        Map<Long, String> categoryNos = new HashMap<>();
        for (MaterialCategory category : categoryMapper.selectAllForTree()) {
            categoryNames.put(category.getId(), category.getCategoryName());
            categoryNos.put(category.getId(), category.getCategoryNo());
        }
        Map<Long, DictItem> unitById = new HashMap<>();
        Map<Long, String> unitCodes = new HashMap<>();
        for (DictItem item : dictItemMapper.selectAllByTypeCode("unit")) {
            unitById.put(item.getId(), item);
            unitCodes.put(item.getId(), item.getItemCode());
        }
        Map<String, String> materialTypeNames = new HashMap<>();
        for (DictItem item : dictItemMapper.selectAllByTypeCode("material_type")) {
            materialTypeNames.put(item.getItemCode(), item.getItemName());
        }
        List<Long> ids = new ArrayList<>();
        for (Material material : materials) {
            ids.add(material.getId());
        }
        Map<Long, List<MaterialExtAttr>> attrsByMaterial = new HashMap<>();
        for (MaterialExtAttr attr : extAttrMapper.selectList(new LambdaQueryWrapper<MaterialExtAttr>()
                .in(MaterialExtAttr::getMaterialId, ids)
                .orderByAsc(MaterialExtAttr::getSortNo))) {
            attrsByMaterial.computeIfAbsent(attr.getMaterialId(), key -> new ArrayList<>()).add(attr);
        }

        List<MaterialVO> result = new ArrayList<>();
        for (Material material : materials) {
            MaterialVO vo = new MaterialVO();
            vo.setId(material.getId());
            vo.setMaterialNo(material.getMaterialNo());
            vo.setMaterialName(material.getMaterialName());
            vo.setCategoryId(material.getCategoryId());
            vo.setCategoryName(categoryNames.get(material.getCategoryId()));
            vo.setCategoryNo(categoryNos.get(material.getCategoryId()));
            vo.setSpec(material.getSpec());
            vo.setBrand(material.getBrand());
            vo.setUnitId(material.getUnitId());
            DictItem unit = unitById.get(material.getUnitId());
            vo.setUnitName(unit == null ? null : unit.getItemName());
            vo.setUnitCode(unit == null ? null : unit.getItemCode());
            vo.setMaterialType(material.getMaterialType());
            vo.setMaterialTypeName(materialTypeNames.get(String.valueOf(material.getMaterialType())));
            vo.setSafetyStock(material.getSafetyStock());
            vo.setStatus(material.getStatus());
            vo.setRemark(material.getRemark());
            List<MaterialVO.ExtAttrVO> extVOs = new ArrayList<>();
            for (MaterialExtAttr attr : attrsByMaterial.getOrDefault(material.getId(), List.of())) {
                MaterialVO.ExtAttrVO extVO = new MaterialVO.ExtAttrVO();
                extVO.setId(attr.getId());
                extVO.setAttrName(attr.getAttrName());
                extVO.setAttrValue(attr.getAttrValue());
                extVO.setSortNo(attr.getSortNo());
                extVOs.add(extVO);
            }
            vo.setExtAttrs(extVOs);
            vo.setCreatedAt(material.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    private Map<String, Long> categoryIdByNo() {
        Map<String, Long> map = new HashMap<>();
        for (MaterialCategory category : categoryMapper.selectAllForTree()) {
            map.put(category.getCategoryNo(), category.getId());
        }
        return map;
    }

    private Map<String, Long> unitIdByCode() {
        Map<String, Long> map = new HashMap<>();
        for (DictItem item : dictItemMapper.selectAllByTypeCode("unit")) {
            if (item.getStatus() != null && item.getStatus() == 1) {
                map.put(item.getItemCode().toUpperCase(), item.getId());
            }
        }
        return map;
    }

    private Set<Integer> materialTypeCodes() {
        Set<Integer> codes = new HashSet<>();
        for (DictItem item : dictItemMapper.selectAllByTypeCode("material_type")) {
            try {
                codes.add(Integer.parseInt(item.getItemCode()));
            } catch (NumberFormatException ignored) {
                // 非数字编码的类型项不参与物料类型校验
            }
        }
        return codes;
    }

    private List<String> readLines(MultipartFile file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                lines.add(line);
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "文件读取失败：" + e.getMessage());
        }
        return lines;
    }

    /** 解析一行 CSV，支持双引号包裹与内部双引号翻倍 */
    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private String cell(List<String> cells, int index) {
        return index < cells.size() ? cells.get(index).trim() : "";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private Integer parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void track(List<String> changes, String field, Object before, Object after) {
        String beforeText = before == null ? "" : String.valueOf(before);
        String afterText = after == null ? "" : String.valueOf(after);
        if (!beforeText.equals(afterText)) {
            changes.add(field + " " + (beforeText.isEmpty() ? "空" : beforeText)
                    + " -> " + (afterText.isEmpty() ? "空" : afterText));
        }
    }

    private String nameOfCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        MaterialCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getCategoryName();
    }

    private String nameOfUnit(Long unitId) {
        if (unitId == null) {
            return null;
        }
        DictItem item = dictItemMapper.selectById(unitId);
        return item == null ? null : item.getItemName();
    }

    private String nameOfMaterialType(Integer materialType) {
        if (materialType == null) {
            return null;
        }
        for (DictItem item : dictItemMapper.selectAllByTypeCode("material_type")) {
            if (item.getItemCode().equals(String.valueOf(materialType))) {
                return item.getItemName();
            }
        }
        return String.valueOf(materialType);
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, field + "不能为空");
        }
        return value.trim();
    }
}
