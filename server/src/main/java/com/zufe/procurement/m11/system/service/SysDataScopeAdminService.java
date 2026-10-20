package com.zufe.procurement.m11.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.owner.entity.OwnerObject;
import com.zufe.procurement.m1.owner.mapper.OwnerObjectMapper;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m11.system.dto.ScopeGrantRequest;
import com.zufe.procurement.m11.system.entity.SysDataScope;
import com.zufe.procurement.m11.system.entity.SysRole;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysDataScopeMapper;
import com.zufe.procurement.m11.system.mapper.SysRoleMapper;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m11.system.vo.ScopeOptionsVO;
import com.zufe.procurement.m11.system.vo.ScopeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据范围授权服务，功能点 M11-3-3，变更留痕对应 M11-3-5。
 * 授权主体为用户或角色，三个维度可组合；保存按主体整体重建，
 * 范围值在写入前逐项校验，避免脏值导致查询条件失效后放大可见数据。
 */
@Service
public class SysDataScopeAdminService {

    private static final int DIM_WAREHOUSE = 1;
    private static final int DIM_DEPT = 2;
    private static final int DIM_OWNER = 3;

    private static final int SUBJECT_USER = 1;
    private static final int SUBJECT_ROLE = 2;

    private final SysDataScopeMapper dataScopeMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final WarehouseMapper warehouseMapper;
    private final DictItemMapper dictItemMapper;
    private final OwnerObjectMapper ownerObjectMapper;
    private final OperationLogService operationLogService;

    public SysDataScopeAdminService(SysDataScopeMapper dataScopeMapper, SysUserMapper userMapper,
                                    SysRoleMapper roleMapper, WarehouseMapper warehouseMapper,
                                    DictItemMapper dictItemMapper, OwnerObjectMapper ownerObjectMapper,
                                    OperationLogService operationLogService) {
        this.dataScopeMapper = dataScopeMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.warehouseMapper = warehouseMapper;
        this.dictItemMapper = dictItemMapper;
        this.ownerObjectMapper = ownerObjectMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * 查询某主体当前持有的数据范围，附可读名称。
     */
    public List<ScopeVO> list(Integer subjectType, Long subjectId) {
        requireSubject(subjectType, subjectId);
        List<SysDataScope> rows = dataScopeMapper.selectList(new QueryWrapper<SysDataScope>()
                .eq("subject_type", subjectType)
                .eq("subject_id", subjectId)
                .orderByAsc("scope_dimension"));

        Map<Long, String> warehouseNames = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>())
                .stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getWarehouseName, (a, b) -> a));
        Map<Long, String> deptNames = dictItemMapper.selectByTypeCode("dept").stream()
                .collect(Collectors.toMap(DictItem::getId, DictItem::getItemName, (a, b) -> a));

        return rows.stream().map(row -> {
            ScopeVO vo = new ScopeVO();
            vo.setId(row.getId());
            vo.setSubjectType(row.getSubjectType());
            vo.setSubjectId(row.getSubjectId());
            vo.setScopeDimension(row.getScopeDimension());
            vo.setScopeValue(row.getScopeValue());
            vo.setScopeValueName(resolveName(row, warehouseNames, deptNames));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 保存某主体的数据范围，整体替换。空列表表示收回全部范围，
     * 收回后该用户的相关查询按“无范围即无数据”处理，不会退化为全量。
     */
    @Transactional
    public void grant(ScopeGrantRequest request) {
        requireSubject(request.getSubjectType(), request.getSubjectId());
        List<ScopeGrantRequest.ScopeItem> items = request.getItems() == null ? List.of() : request.getItems();

        // 去重，避免同一维度同一值重复插入撞唯一键
        List<ScopeGrantRequest.ScopeItem> distinct = items.stream()
                .filter(item -> item.getScopeDimension() != null && item.getScopeValue() != null)
                .collect(Collectors.toMap(
                        item -> item.getScopeDimension() + ":" + item.getScopeValue(),
                        Function.identity(), (a, b) -> a))
                .values().stream().toList();

        for (ScopeGrantRequest.ScopeItem item : distinct) {
            validateScopeValue(item);
        }

        List<String> before = describeExisting(request.getSubjectType(), request.getSubjectId());
        dataScopeMapper.deletePhysicallyBySubject(request.getSubjectType(), request.getSubjectId());
        for (ScopeGrantRequest.ScopeItem item : distinct) {
            SysDataScope entity = new SysDataScope();
            entity.setSubjectType(request.getSubjectType());
            entity.setSubjectId(request.getSubjectId());
            entity.setScopeDimension(item.getScopeDimension());
            entity.setScopeValue(item.getScopeValue().trim());
            entity.setIsDeleted(0);
            entity.setVersion(0);
            dataScopeMapper.insert(entity);
        }
        List<String> after = distinct.stream()
                .map(item -> dimensionName(item.getScopeDimension()) + ":" + item.getScopeValue())
                .collect(Collectors.toList());

        String targetNo = request.getSubjectType() == SUBJECT_USER
                ? userMapper.selectById(request.getSubjectId()).getUsername()
                : roleMapper.selectById(request.getSubjectId()).getRoleCode();
        operationLogService.record("M11", "grant", "sys_data_scope",
                request.getSubjectId(), targetNo,
                "数据范围授权变更（主体类型 " + request.getSubjectType() + "）：" + before + " -> " + after);
    }

    /**
     * 三个维度的候选项，供授权界面下拉与穿梭框使用。
     */
    public ScopeOptionsVO options() {
        ScopeOptionsVO vo = new ScopeOptionsVO();
        vo.setWarehouses(warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getStatus, 1).orderByAsc(Warehouse::getId))
                .stream().map(w -> new ScopeOptionsVO.Option(String.valueOf(w.getId()),
                        w.getWarehouseNo() + " " + w.getWarehouseName()))
                .collect(Collectors.toList()));
        vo.setDepts(dictItemMapper.selectByTypeCode("dept").stream()
                .map(d -> new ScopeOptionsVO.Option(String.valueOf(d.getId()), d.getItemName()))
                .collect(Collectors.toList()));
        vo.setOwners(ownerObjectMapper.selectList(new LambdaQueryWrapper<OwnerObject>()
                        .eq(OwnerObject::getStatus, 1).orderByAsc(OwnerObject::getId))
                .stream().map(o -> new ScopeOptionsVO.Option(o.getOwnerNo(),
                        o.getOwnerNo() + " " + o.getOwnerName()))
                .collect(Collectors.toList()));
        return vo;
    }

    private void requireSubject(Integer subjectType, Long subjectId) {
        if (subjectType == null || subjectId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "授权主体类型与主体id不能为空");
        }
        if (subjectType == SUBJECT_USER) {
            if (userMapper.selectById(subjectId) == null) {
                throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
            }
        } else if (subjectType == SUBJECT_ROLE) {
            if (roleMapper.selectById(subjectId) == null) {
                throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
            }
        } else {
            throw new BizException(ResultCode.INVALID_PARAMETER, "授权主体类型只能为 1 用户或 2 角色");
        }
    }

    private void validateScopeValue(ScopeGrantRequest.ScopeItem item) {
        String value = item.getScopeValue().trim();
        if (value.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "范围值不能为空");
        }
        switch (item.getScopeDimension()) {
            case DIM_WAREHOUSE -> {
                Long id = parseId(value, "仓库");
                Warehouse warehouse = warehouseMapper.selectById(id);
                if (warehouse == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "仓库不存在：" + value);
                }
            }
            case DIM_DEPT -> {
                Long id = parseId(value, "部门");
                if (dictItemMapper.countByTypeCodeAndId("dept", id) == 0) {
                    throw new BizException(ResultCode.NOT_FOUND, "部门不存在：" + value);
                }
            }
            case DIM_OWNER -> {
                Long count = ownerObjectMapper.selectCount(new LambdaQueryWrapper<OwnerObject>()
                        .eq(OwnerObject::getOwnerNo, value));
                if (count == 0) {
                    throw new BizException(ResultCode.NOT_FOUND, "归属对象不存在：" + value);
                }
            }
            default -> throw new BizException(ResultCode.INVALID_PARAMETER, "范围维度只能为 1 至 3");
        }
    }

    private Long parseId(String value, String label) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.INVALID_PARAMETER, label + "范围值必须为数字id：" + value);
        }
    }

    private String resolveName(SysDataScope row, Map<Long, String> warehouseNames, Map<Long, String> deptNames) {
        if (row.getScopeDimension() == null) {
            return row.getScopeValue();
        }
        return switch (row.getScopeDimension()) {
            case DIM_WAREHOUSE -> warehouseNames.getOrDefault(parseQuietly(row.getScopeValue()), row.getScopeValue());
            case DIM_DEPT -> deptNames.getOrDefault(parseQuietly(row.getScopeValue()), row.getScopeValue());
            default -> row.getScopeValue();
        };
    }

    private Long parseQuietly(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private List<String> describeExisting(Integer subjectType, Long subjectId) {
        List<SysDataScope> rows = dataScopeMapper.selectList(new QueryWrapper<SysDataScope>()
                .eq("subject_type", subjectType).eq("subject_id", subjectId));
        List<String> result = new ArrayList<>();
        for (SysDataScope row : rows) {
            result.add(dimensionName(row.getScopeDimension()) + ":" + row.getScopeValue());
        }
        return result;
    }

    private String dimensionName(Integer dimension) {
        if (dimension == null) {
            return "未知";
        }
        return switch (dimension) {
            case DIM_WAREHOUSE -> "仓库";
            case DIM_DEPT -> "部门";
            case DIM_OWNER -> "归属对象";
            default -> "未知";
        };
    }
}
