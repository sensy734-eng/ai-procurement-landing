package com.zufe.procurement.m1.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.dto.DictItemSaveRequest;
import com.zufe.procurement.m1.dict.dto.DictTypeSaveRequest;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.entity.DictType;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.dict.mapper.DictTypeMapper;
import com.zufe.procurement.m1.dict.vo.DictItemVO;
import com.zufe.procurement.m1.dict.vo.DictTypeVO;
import com.zufe.procurement.m11.system.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 字典服务。覆盖功能点：
 * M1-4-1 字典类型与字典项维护、M1-4-2 单据类型与供应商类型配置。
 * 口径：类型编码与项编码创建后不可改，停用替代删除；
 * bill_type 的 ext_value 是单据编号前缀；supplier_type 与 material_type
 * 的项编码与业务字段取值保持一致；unit 项编码为大写字母数字。
 * 状态字典（lifecycle_status 等）由系统预置，维护时只允许改名称与排序，
 * 编码约束不在此处收紧，前端对状态类字典给出醒目提示。
 */
@Service
public class DictService {

    private static final Pattern TYPE_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,49}$");
    private static final Pattern BILL_PREFIX_PATTERN = Pattern.compile("^[A-Z][A-Z0-9-]{1,9}$");
    private static final Pattern UNIT_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{1,10}$");

    private final DictItemMapper dictItemMapper;
    private final DictTypeMapper dictTypeMapper;
    private final OperationLogService operationLogService;

    public DictService(DictItemMapper dictItemMapper,
                       DictTypeMapper dictTypeMapper,
                       OperationLogService operationLogService) {
        this.dictItemMapper = dictItemMapper;
        this.dictTypeMapper = dictTypeMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * 下拉选项：仅返回启用项，供全系统下拉框引用。
     * 四个角色均持 m1:dict:list，认证链路与部门树都用它。
     */
    public List<DictItemVO> items(String typeCode) {
        return dictItemMapper.selectByTypeCode(typeCode).stream().map(item -> {
            DictItemVO vo = new DictItemVO();
            vo.setId(item.getId());
            vo.setTypeId(item.getTypeId());
            vo.setItemCode(item.getItemCode());
            vo.setItemName(item.getItemName());
            vo.setParentId(item.getParentId());
            vo.setSortNo(item.getSortNo());
            vo.setExtValue(item.getExtValue());
            return vo;
        }).toList();
    }

    // ---------------- M1-4-1 字典类型 ----------------

    public List<DictTypeVO> listTypes() {
        List<DictType> types = dictTypeMapper.selectList(new LambdaQueryWrapper<DictType>()
                .orderByAsc(DictType::getId));
        List<DictTypeVO> result = new ArrayList<>();
        for (DictType type : types) {
            DictTypeVO vo = new DictTypeVO();
            vo.setId(type.getId());
            vo.setTypeCode(type.getTypeCode());
            vo.setTypeName(type.getTypeName());
            vo.setStatus(type.getStatus());
            vo.setRemark(type.getRemark());
            vo.setItemCount(dictItemMapper.countByTypeId(type.getId()));
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public Long createType(DictTypeSaveRequest request) {
        String typeCode = requireText(request.getTypeCode(), "类型编码").toLowerCase();
        if (!TYPE_CODE_PATTERN.matcher(typeCode).matches()) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "类型编码须以小写字母开头，可含小写字母、数字与下划线，2 至 50 位");
        }
        if (dictTypeMapper.countByTypeCodeIncludeDeleted(typeCode) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "类型编码已存在：" + typeCode);
        }
        DictType type = new DictType();
        type.setTypeCode(typeCode);
        type.setTypeName(requireText(request.getTypeName(), "类型名称"));
        type.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        type.setRemark(request.getRemark());
        dictTypeMapper.insert(type);
        operationLogService.record("M1", "create", "dict_type", type.getId(), typeCode,
                "新增字典类型 " + typeCode);
        return type.getId();
    }

    @Transactional
    public void updateType(Long id, DictTypeSaveRequest request) {
        DictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        if (request.getTypeCode() != null && !request.getTypeCode().trim().equals(type.getTypeCode())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "类型编码创建后不可修改");
        }
        type.setTypeName(requireText(request.getTypeName(), "类型名称"));
        type.setStatus(request.getStatus() == null ? type.getStatus() : request.getStatus());
        type.setRemark(request.getRemark());
        dictTypeMapper.updateById(type);
        operationLogService.record("M1", "update", "dict_type", id, type.getTypeCode(),
                "编辑字典类型 " + type.getTypeCode());
    }

    // ---------------- M1-4-1 字典项 ----------------

    /** 管理端全量字典项，含停用项 */
    public List<DictItemVO> itemsAdmin(String typeCode) {
        List<DictItemVO> result = new ArrayList<>();
        for (DictItem item : dictItemMapper.selectAllByTypeCode(typeCode)) {
            DictItemVO vo = new DictItemVO();
            vo.setId(item.getId());
            vo.setTypeId(item.getTypeId());
            vo.setItemCode(item.getItemCode());
            vo.setItemName(item.getItemName());
            vo.setParentId(item.getParentId());
            vo.setSortNo(item.getSortNo());
            vo.setStatus(item.getStatus());
            vo.setExtValue(item.getExtValue());
            vo.setRemark(item.getRemark());
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public Long createItem(DictItemSaveRequest request) {
        DictType type = requireType(request.getTypeId());
        String itemCode = requireText(request.getItemCode(), "项编码");
        validateItemConstraints(type, itemCode, request.getExtValue());
        if (dictItemMapper.countByTypeIdAndItemCodeIncludeDeleted(type.getId(), itemCode) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "项编码在该类型下已存在：" + itemCode);
        }
        long parentId = request.getParentId() == null ? 0 : request.getParentId();
        if (parentId != 0) {
            DictItem parent = dictItemMapper.selectById(parentId);
            if (parent == null || !parent.getTypeId().equals(type.getId())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "上级字典项不存在或不属于该类型");
            }
        }
        DictItem item = new DictItem();
        item.setTypeId(type.getId());
        item.setItemCode(itemCode);
        item.setItemName(requireText(request.getItemName(), "项名称"));
        item.setParentId(parentId);
        item.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        item.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        item.setExtValue(request.getExtValue());
        item.setRemark(request.getRemark());
        dictItemMapper.insert(item);
        operationLogService.record("M1", "create", "dict_item", item.getId(), itemCode,
                "新增字典项 " + type.getTypeCode() + "/" + itemCode);
        return item.getId();
    }

    @Transactional
    public void updateItem(Long id, DictItemSaveRequest request) {
        DictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        DictType type = dictTypeMapper.selectById(item.getTypeId());
        if (type == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        if (request.getItemCode() != null && !request.getItemCode().trim().equals(item.getItemCode())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "项编码创建后不可修改");
        }
        validateItemConstraints(type, item.getItemCode(), request.getExtValue());
        long parentId = request.getParentId() == null ? item.getParentId() : request.getParentId();
        if (parentId != 0) {
            DictItem parent = dictItemMapper.selectById(parentId);
            if (parent == null || !parent.getTypeId().equals(type.getId())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "上级字典项不存在或不属于该类型");
            }
        }
        item.setItemName(requireText(request.getItemName(), "项名称"));
        item.setParentId(parentId);
        item.setSortNo(request.getSortNo() == null ? item.getSortNo() : request.getSortNo());
        item.setStatus(request.getStatus() == null ? item.getStatus() : request.getStatus());
        item.setExtValue(request.getExtValue());
        item.setRemark(request.getRemark());
        dictItemMapper.updateById(item);
        operationLogService.record("M1", "update", "dict_item", id, item.getItemCode(),
                "编辑字典项 " + type.getTypeCode() + "/" + item.getItemCode());
    }

    /**
     * M1-4-2 按字典类型收紧的约束。只约束与本轮功能点直接相关的类型，
     * 其余类型不设编码规则，避免把通用字典管死。
     */
    private void validateItemConstraints(DictType type, String itemCode, String extValue) {
        switch (type.getTypeCode()) {
            case "bill_type" -> {
                // 单据编号前缀，M1-4-2：决定单据编号前缀与统计分组
                if (!StringUtils.hasText(extValue)) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "单据类型必须填写编号前缀");
                }
                if (!BILL_PREFIX_PATTERN.matcher(extValue.trim()).matches()) {
                    throw new BizException(ResultCode.INVALID_PARAMETER,
                            "编号前缀须为 2 至 10 位大写字母、数字或连字符，如 CG-SQ");
                }
            }
            case "supplier_type" -> {
                // 与 supplier.supplier_type 取值保持一致：1 生产厂家、2 经销商、3 服务商
                if (!itemCode.matches("[1-3]")) {
                    throw new BizException(ResultCode.INVALID_PARAMETER,
                            "供应商类型项编码须为 1 至 3 的整数，与业务字段取值一致");
                }
            }
            case "material_type" -> {
                // 与 material.material_type 取值保持一致：1 原材料、2 辅料、3 外购件、4 备件
                if (!itemCode.matches("[1-4]")) {
                    throw new BizException(ResultCode.INVALID_PARAMETER,
                            "物料类型项编码须为 1 至 4 的整数，与业务字段取值一致");
                }
            }
            case "unit" -> {
                // M1-1-7 计量单位，导入按编码寻址，统一大写
                if (!UNIT_CODE_PATTERN.matcher(itemCode).matches()) {
                    throw new BizException(ResultCode.INVALID_PARAMETER,
                            "计量单位编码须为 1 至 10 位大写字母或数字，如 PCS、KG");
                }
            }
            default -> {
                // 其余类型不设编码规则
            }
        }
    }

    private DictType requireType(Long typeId) {
        if (typeId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择字典类型");
        }
        DictType type = dictTypeMapper.selectById(typeId);
        if (type == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, field + "不能为空");
        }
        return value.trim();
    }
}
