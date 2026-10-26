package com.zufe.procurement.m6.price.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m11.system.service.OperationLogService;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m6.price.dto.PriceReferenceQuery;
import com.zufe.procurement.m6.price.dto.PriceReferenceSaveRequest;
import com.zufe.procurement.m6.price.entity.PriceReference;
import com.zufe.procurement.m6.price.mapper.PriceReferenceMapper;
import com.zufe.procurement.m6.price.vo.PriceReferenceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 价格参考维护（M6-1-1）。
 *
 * 版本策略（本轮裁定）：
 * - 同一物料 price_version 递增，历史版本永久保留，支持追溯；
 * - 「改价格/生效日」= 生成新版本（不是改旧值），旧版本留痕；
 *   新版本创建时，停用生效日不晚于新版本的旧生效版
 *   （未来生效日的预售版可与旧版并存，到期自动接任）；
 * - 「改备注/停用启用」= 原地更新当前版本；
 * - 参考价是手工治理基准，与当前供应商报价严格分离（R6）。
 */
@Service
public class PriceReferenceService {

    private final PriceReferenceMapper priceReferenceMapper;
    private final MaterialMapper materialMapper;
    private final OperationLogService operationLogService;

    public PriceReferenceService(PriceReferenceMapper priceReferenceMapper,
                                 MaterialMapper materialMapper,
                                 OperationLogService operationLogService) {
        this.priceReferenceMapper = priceReferenceMapper;
        this.materialMapper = materialMapper;
        this.operationLogService = operationLogService;
    }

    /** 分页查询（可按物料/状态过滤），带是否当前生效版标记 */
    public PageResult page(PriceReferenceQuery query) {
        LambdaQueryWrapper<PriceReference> wrapper = new LambdaQueryWrapper<PriceReference>()
                .eq(query.getMaterialId() != null, PriceReference::getMaterialId, query.getMaterialId())
                .eq(query.getStatus() != null, PriceReference::getStatus, query.getStatus())
                .eq(PriceReference::getIsDeleted, 0)
                .orderByDesc(PriceReference::getMaterialId)
                .orderByDesc(PriceReference::getPriceVersion);
        List<PriceReference> rows = priceReferenceMapper.selectList(wrapper);
        long total = rows.size();
        int from = Math.max(0, (query.getPageNo() - 1) * query.getPageSize());
        int to = Math.min(rows.size(), from + query.getPageSize());
        List<PriceReference> pageRows = from >= rows.size() ? List.of() : rows.subList(from, to);

        Map<Long, Material> materials = new HashMap<>();
        for (Material material : materialMapper.selectList(
                new LambdaQueryWrapper<Material>().isNotNull(Material::getId))) {
            materials.put(material.getId(), material);
        }
        // 当前生效版：生效日 <= 今天的最新一版
        Map<Long, PriceReference> currentByMaterial = new HashMap<>();
        for (PriceReference row : rows) {
            if (row.getStatus() != null && row.getStatus() == 1
                    && !row.getEffectiveDate().isAfter(LocalDate.now())) {
                PriceReference cur = currentByMaterial.get(row.getMaterialId());
                if (cur == null || row.getEffectiveDate().isAfter(cur.getEffectiveDate())
                        || (row.getEffectiveDate().equals(cur.getEffectiveDate())
                                && row.getPriceVersion() > cur.getPriceVersion())) {
                    currentByMaterial.put(row.getMaterialId(), row);
                }
            }
        }

        List<PriceReferenceVO> records = new ArrayList<>();
        for (PriceReference row : pageRows) {
            records.add(toVO(row, materials.get(row.getMaterialId()),
                    Objects.equals(currentByMaterial.get(row.getMaterialId()) == null ? null
                            : currentByMaterial.get(row.getMaterialId()).getId(), row.getId())));
        }
        return new PageResult(total, records);
    }

    /** 详情（含版本历史） */
    public Map<String, Object> detail(Long id) {
        PriceReference row = priceReferenceMapper.selectById(id);
        if (row == null || Objects.equals(row.getIsDeleted(), 1)) {
            throw new BizException(ResultCode.NOT_FOUND, "价格参考不存在");
        }
        Material material = materialMapper.selectById(row.getMaterialId());
        Map<String, Object> result = new HashMap<>();
        result.put("reference", toVO(row, material, isCurrent(row)));
        result.put("versions", priceReferenceMapper.selectVersions(row.getMaterialId()).stream()
                .map(v -> toVO(v, material, Objects.equals(v.getId(), row.getId())
                        ? isCurrent(v) : false)).toList());
        return result;
    }

    /** 某物料的版本历史 */
    public List<PriceReferenceVO> versions(Long materialId) {
        Material material = materialMapper.selectById(materialId);
        List<PriceReferenceVO> result = new ArrayList<>();
        for (PriceReference row : priceReferenceMapper.selectVersions(materialId)) {
            result.add(toVO(row, material, isCurrent(row)));
        }
        return result;
    }

    /**
     * 新建参考价：生成新版本。同物料 price_version = max+1；
     * 停用生效日不晚于新版本的旧生效版。
     */
    @Transactional
    public Long create(PriceReferenceSaveRequest request) {
        validateSave(request);
        Long operatorId = AuthContext.getOrThrow().getUserId();
        PriceReference row = new PriceReference();
        row.setMaterialId(request.getMaterialId());
        row.setRefPrice(request.getRefPrice().setScale(4, java.math.RoundingMode.HALF_UP));
        row.setEffectiveDate(request.getEffectiveDate());
        row.setPriceVersion(priceReferenceMapper.maxVersionByMaterial(request.getMaterialId()) + 1);
        row.setSourceType(1);
        row.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        row.setRemark(trimToNull(request.getRemark()));
        row.setCreatedBy(operatorId);
        priceReferenceMapper.insert(row);
        if (Objects.equals(row.getStatus(), 1) && !row.getEffectiveDate().isAfter(LocalDate.now())) {
            // 只有「立即生效」的新版本才停用旧生效版；未来生效的预售版与旧版并存，到期自动接任
            priceReferenceMapper.retireOlderVersions(row.getMaterialId(), row.getId(),
                    row.getEffectiveDate(), operatorId);
        }
        operationLogService.record("M6", "create", "price_reference", row.getId(),
                "物料 " + row.getMaterialId() + " V" + row.getPriceVersion(),
                "参考价 " + row.getRefPrice().toPlainString() + "，生效日 " + row.getEffectiveDate());
        return row.getId();
    }

    /**
     * 更新：改价格或生效日 → 生成新版本（等价 create 的版本化写入）；
     * 仅改备注或状态 → 原地更新。
     */
    @Transactional
    public Long update(PriceReferenceSaveRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "更新必须带参考价 id");
        }
        PriceReference exists = priceReferenceMapper.selectById(request.getId());
        if (exists == null || Objects.equals(exists.getIsDeleted(), 1)) {
            throw new BizException(ResultCode.NOT_FOUND, "价格参考不存在");
        }
        Long operatorId = AuthContext.getOrThrow().getUserId();
        boolean priceChanged = request.getRefPrice() != null
                && request.getRefPrice().setScale(4, java.math.RoundingMode.HALF_UP)
                        .compareTo(exists.getRefPrice()) != 0;
        boolean dateChanged = request.getEffectiveDate() != null
                && !request.getEffectiveDate().equals(exists.getEffectiveDate());
        if (priceChanged || dateChanged) {
            // 版本化写入：新版本接任，旧版本留痕
            validateSave(request);
            PriceReferenceSaveRequest asNew = new PriceReferenceSaveRequest();
            asNew.setMaterialId(exists.getMaterialId());
            asNew.setRefPrice(request.getRefPrice());
            asNew.setEffectiveDate(request.getEffectiveDate());
            asNew.setStatus(request.getStatus());
            asNew.setRemark(request.getRemark());
            return create(asNew);
        }
        if (request.getStatus() != null && request.getStatus() != 1 && request.getStatus() != 2) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "状态只能是 1 生效或 2 停用");
        }
        // 原地更新：备注 / 停用启用
        PriceReference update = new PriceReference();
        update.setId(exists.getId());
        update.setRemark(trimToNull(request.getRemark()));
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        update.setUpdatedBy(operatorId);
        update.setVersion(exists.getVersion());
        priceReferenceMapper.updateById(update);
        operationLogService.record("M6", "update", "price_reference", exists.getId(),
                "物料 " + exists.getMaterialId() + " V" + exists.getPriceVersion(),
                (request.getStatus() != null && request.getStatus() == 2 ? "停用" : "更新备注"));
        return exists.getId();
    }

    private void validateSave(PriceReferenceSaveRequest request) {
        if (request.getMaterialId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "物料不能为空");
        }
        if (request.getRefPrice() == null || request.getRefPrice().signum() < 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "参考价必须大于等于 0");
        }
        if (request.getEffectiveDate() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "生效日期不能为空");
        }
    }

    private boolean isCurrent(PriceReference row) {
        if (row.getStatus() == null || row.getStatus() != 1) {
            return false;
        }
        if (row.getEffectiveDate().isAfter(LocalDate.now())) {
            return false;
        }
        PriceReference latest = priceReferenceMapper.selectEffective(row.getMaterialId(), LocalDate.now());
        return latest != null && Objects.equals(latest.getId(), row.getId());
    }

    private PriceReferenceVO toVO(PriceReference row, Material material, Boolean current) {
        PriceReferenceVO vo = new PriceReferenceVO();
        vo.setId(row.getId());
        vo.setMaterialId(row.getMaterialId());
        vo.setMaterialNo(material == null ? null : material.getMaterialNo());
        vo.setMaterialName(material == null ? null : material.getMaterialName());
        vo.setSpec(material == null ? null : material.getSpec());
        vo.setRefPrice(row.getRefPrice());
        vo.setEffectiveDate(row.getEffectiveDate());
        vo.setPriceVersion(row.getPriceVersion());
        vo.setSourceType(row.getSourceType());
        vo.setSourceTypeName(row.getSourceType() != null && row.getSourceType() == 2 ? "批量导入" : "手工设定");
        vo.setStatus(row.getStatus());
        vo.setStatusName(row.getStatus() != null && row.getStatus() == 2 ? "停用" : "生效");
        vo.setCurrent(Boolean.TRUE.equals(current));
        vo.setRemark(row.getRemark());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class PageResult {
        private final long total;
        private final List<PriceReferenceVO> records;

        public PageResult(long total, List<PriceReferenceVO> records) {
            this.total = total;
            this.records = records;
        }

        public long getTotal() {
            return total;
        }

        public List<PriceReferenceVO> getRecords() {
            return records;
        }
    }
}
