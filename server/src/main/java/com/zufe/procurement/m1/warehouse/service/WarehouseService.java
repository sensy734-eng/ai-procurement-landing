package com.zufe.procurement.m1.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.warehouse.dto.LocationSaveRequest;
import com.zufe.procurement.m1.warehouse.dto.WarehouseSaveRequest;
import com.zufe.procurement.m1.warehouse.dto.WarehouseUserSaveRequest;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.entity.WarehouseLocation;
import com.zufe.procurement.m1.warehouse.entity.WarehouseUser;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseLocationMapper;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseUserMapper;
import com.zufe.procurement.m1.warehouse.vo.LocationVO;
import com.zufe.procurement.m1.warehouse.vo.WarehouseUserVO;
import com.zufe.procurement.m1.warehouse.vo.WarehouseVO;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m11.system.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仓库服务。覆盖功能点：
 * M1-3-1 仓库档案维护、M1-3-2 库位维护、M1-3-3 仓库与使用人对应关系。
 * 口径：编码唯一性连带已删除记录查；编码创建后不可改；
 * warehouse_user 是数据范围授权依据，与 sys_data_scope 仓库维度配合；
 * 无独立停用权限点，状态随更新接口修改。
 */
@Service
public class WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper locationMapper;
    private final WarehouseUserMapper warehouseUserMapper;
    private final SysUserMapper userMapper;
    private final DictItemMapper dictItemMapper;
    private final OperationLogService operationLogService;

    public WarehouseService(WarehouseMapper warehouseMapper,
                            WarehouseLocationMapper locationMapper,
                            WarehouseUserMapper warehouseUserMapper,
                            SysUserMapper userMapper,
                            DictItemMapper dictItemMapper,
                            OperationLogService operationLogService) {
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.warehouseUserMapper = warehouseUserMapper;
        this.userMapper = userMapper;
        this.dictItemMapper = dictItemMapper;
        this.operationLogService = operationLogService;
    }

    // ---------------- M1-3-1 仓库档案 ----------------

    public PageResult<WarehouseVO> page(long pageNo, long pageSize, String keyword, Integer status) {
        Page<Warehouse> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100));
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Warehouse> result = warehouseMapper.selectPage(page, new LambdaQueryWrapper<Warehouse>()
                .and(kw != null, w -> w.like(Warehouse::getWarehouseNo, kw)
                        .or().like(Warehouse::getWarehouseName, kw))
                .eq(status != null, Warehouse::getStatus, status)
                .orderByDesc(Warehouse::getId));
        List<Warehouse> warehouses = result.getRecords();
        List<WarehouseVO> records = enrich(warehouses);
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public WarehouseVO detail(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BizException(ResultCode.NOT_FOUND, "仓库不存在");
        }
        return enrich(List.of(warehouse)).get(0);
    }

    /** M1-3-1 可选仓库，仅启用，供入库等业务下拉 */
    public List<WarehouseVO> options() {
        List<Warehouse> list = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, 1)
                .orderByAsc(Warehouse::getWarehouseNo));
        return enrich(list);
    }

    @Transactional
    public Long create(WarehouseSaveRequest request) {
        Warehouse warehouse = new Warehouse();
        applyRequest(warehouse, request);
        warehouse.setStatus(1);
        warehouseMapper.insert(warehouse);
        operationLogService.record("M1", "create", "warehouse", warehouse.getId(),
                warehouse.getWarehouseNo(), "新增仓库 " + warehouse.getWarehouseNo());
        return warehouse.getId();
    }

    @Transactional
    public void update(Long id, WarehouseSaveRequest request) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BizException(ResultCode.NOT_FOUND, "仓库不存在");
        }
        if (request.getWarehouseNo() != null
                && !request.getWarehouseNo().trim().equals(warehouse.getWarehouseNo())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "仓库编码创建后不可修改");
        }
        applyRequest(warehouse, request);
        warehouseMapper.updateById(warehouse);
        operationLogService.record("M1", "update", "warehouse", id, warehouse.getWarehouseNo(),
                "编辑仓库 " + warehouse.getWarehouseNo());
    }

    // ---------------- M1-3-2 库位 ----------------

    public List<LocationVO> locations(Long warehouseId) {
        requireWarehouse(warehouseId);
        List<LocationVO> result = new ArrayList<>();
        for (WarehouseLocation location : locationMapper.selectByWarehouseId(warehouseId)) {
            LocationVO vo = new LocationVO();
            vo.setId(location.getId());
            vo.setWarehouseId(location.getWarehouseId());
            vo.setLocationNo(location.getLocationNo());
            vo.setLocationName(location.getLocationName());
            vo.setStatus(location.getStatus());
            vo.setRemark(location.getRemark());
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public Long createLocation(Long warehouseId, LocationSaveRequest request) {
        requireWarehouse(warehouseId);
        String locationNo = requireText(request.getLocationNo(), "库位编码");
        if (locationNo.length() > 50) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "库位编码超长（50 字以内）");
        }
        if (locationMapper.countByWarehouseAndNoIncludeDeleted(warehouseId, locationNo) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "库位编码在该仓库下已存在：" + locationNo);
        }
        WarehouseLocation location = new WarehouseLocation();
        location.setWarehouseId(warehouseId);
        location.setLocationNo(locationNo);
        location.setLocationName(StringUtils.hasText(request.getLocationName())
                ? request.getLocationName().trim() : locationNo);
        location.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        location.setRemark(request.getRemark());
        locationMapper.insert(location);
        operationLogService.record("M1", "create", "warehouse_location", location.getId(), locationNo,
                "新增库位 " + locationNo);
        return location.getId();
    }

    @Transactional
    public void updateLocation(Long locationId, LocationSaveRequest request) {
        WarehouseLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BizException(ResultCode.NOT_FOUND, "库位不存在");
        }
        if (request.getLocationNo() != null
                && !request.getLocationNo().trim().equals(location.getLocationNo())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "库位编码创建后不可修改");
        }
        location.setLocationName(StringUtils.hasText(request.getLocationName())
                ? request.getLocationName().trim() : location.getLocationName());
        location.setStatus(request.getStatus() == null ? location.getStatus() : request.getStatus());
        location.setRemark(request.getRemark());
        locationMapper.updateById(location);
        operationLogService.record("M1", "update", "warehouse_location", locationId,
                location.getLocationNo(), "编辑库位 " + location.getLocationNo());
    }

    // ---------------- M1-3-3 仓库使用人 ----------------

    public List<WarehouseUserVO> warehouseUsers(Long warehouseId) {
        requireWarehouse(warehouseId);
        List<WarehouseUser> relations = warehouseUserMapper.selectByWarehouseId(warehouseId);
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> userIds = new ArrayList<>();
        for (WarehouseUser relation : relations) {
            userIds.add(relation.getUserId());
        }
        Map<Long, SysUser> userById = new HashMap<>();
        for (SysUser user : userMapper.selectBatchIds(userIds)) {
            userById.put(user.getId(), user);
        }
        List<WarehouseUserVO> result = new ArrayList<>();
        for (WarehouseUser relation : relations) {
            SysUser user = userById.get(relation.getUserId());
            if (user == null) {
                continue;
            }
            WarehouseUserVO vo = new WarehouseUserVO();
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setDeptName(deptName(user.getDeptId()));
            vo.setIsDefault(relation.getIsDefault());
            result.add(vo);
        }
        return result;
    }

    /** M1-3-3 使用人整体替换。唯一键不含 is_deleted，物理删除后重建 */
    @Transactional
    public void saveWarehouseUsers(Long warehouseId, WarehouseUserSaveRequest request) {
        requireWarehouse(warehouseId);
        List<Long> userIds = request.getUserIds() == null ? List.of() : request.getUserIds();
        if (userIds.size() != new HashSet<>(userIds).size()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "使用人列表存在重复用户");
        }
        if (request.getDefaultUserId() != null && !userIds.contains(request.getDefaultUserId())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "默认仓库使用人必须在列表中");
        }
        if (!userIds.isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(userIds);
            if (users.size() != userIds.size()) {
                throw new BizException(ResultCode.NOT_FOUND, "使用人列表中存在不存在的用户");
            }
        }
        warehouseUserMapper.physicalDeleteByWarehouseId(warehouseId);
        for (Long userId : userIds) {
            WarehouseUser relation = new WarehouseUser();
            relation.setWarehouseId(warehouseId);
            relation.setUserId(userId);
            relation.setIsDefault(userId.equals(request.getDefaultUserId()) ? 1 : 0);
            warehouseUserMapper.insert(relation);
        }
        operationLogService.record("M1", "update", "warehouse_user", warehouseId, null,
                "保存仓库使用人，共 " + userIds.size() + " 人");
    }

    // ---------------- 内部方法 ----------------

    private List<WarehouseVO> enrich(List<Warehouse> warehouses) {
        if (warehouses.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> managerIds = new HashSet<>();
        for (Warehouse warehouse : warehouses) {
            if (warehouse.getManagerId() != null) {
                managerIds.add(warehouse.getManagerId());
            }
        }
        Map<Long, String> managerNames = new HashMap<>();
        if (!managerIds.isEmpty()) {
            for (SysUser user : userMapper.selectBatchIds(managerIds)) {
                managerNames.put(user.getId(), user.getRealName());
            }
        }
        List<Long> ids = new ArrayList<>();
        for (Warehouse warehouse : warehouses) {
            ids.add(warehouse.getId());
        }
        Map<Long, Long> locationCounts = new HashMap<>();
        for (WarehouseLocation location : locationMapper.selectList(new LambdaQueryWrapper<WarehouseLocation>()
                .in(WarehouseLocation::getWarehouseId, ids))) {
            locationCounts.merge(location.getWarehouseId(), 1L, Long::sum);
        }
        Map<Long, Long> userCounts = new HashMap<>();
        for (WarehouseUser relation : warehouseUserMapper.selectList(new LambdaQueryWrapper<WarehouseUser>()
                .in(WarehouseUser::getWarehouseId, ids))) {
            userCounts.merge(relation.getWarehouseId(), 1L, Long::sum);
        }

        List<WarehouseVO> result = new ArrayList<>();
        for (Warehouse warehouse : warehouses) {
            WarehouseVO vo = new WarehouseVO();
            vo.setId(warehouse.getId());
            vo.setWarehouseNo(warehouse.getWarehouseNo());
            vo.setWarehouseName(warehouse.getWarehouseName());
            vo.setManagerId(warehouse.getManagerId());
            vo.setManagerName(managerNames.get(warehouse.getManagerId()));
            vo.setAddress(warehouse.getAddress());
            vo.setPhone(warehouse.getPhone());
            vo.setStatus(warehouse.getStatus());
            vo.setRemark(warehouse.getRemark());
            vo.setLocationCount(locationCounts.getOrDefault(warehouse.getId(), 0L).intValue());
            vo.setUserCount(userCounts.getOrDefault(warehouse.getId(), 0L).intValue());
            result.add(vo);
        }
        return result;
    }

    private void applyRequest(Warehouse warehouse, WarehouseSaveRequest request) {
        String warehouseNo = requireText(request.getWarehouseNo(), "仓库编码");
        if (warehouseNo.length() > 50) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "仓库编码超长（50 字以内）");
        }
        if (warehouse.getId() == null) {
            Warehouse existing = warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>()
                    .eq(Warehouse::getWarehouseNo, warehouseNo));
            if (existing != null) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "仓库编码已存在：" + warehouseNo);
            }
        }
        String warehouseName = requireText(request.getWarehouseName(), "仓库名称");
        if (request.getManagerId() != null && userMapper.selectById(request.getManagerId()) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "仓库负责人不存在");
        }
        warehouse.setWarehouseNo(warehouseNo);
        warehouse.setWarehouseName(warehouseName);
        warehouse.setManagerId(request.getManagerId());
        warehouse.setAddress(request.getAddress());
        warehouse.setPhone(request.getPhone());
        warehouse.setRemark(request.getRemark());
    }

    private void requireWarehouse(Long warehouseId) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "仓库不存在");
        }
    }

    private String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        DictItem dept = dictItemMapper.selectById(deptId);
        return dept == null ? null : dept.getItemName();
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, field + "不能为空");
        }
        return value.trim();
    }
}
