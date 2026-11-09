package com.zufe.procurement.m5.stock.service;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.CsvWriter;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.entity.MaterialCategory;
import com.zufe.procurement.m1.material.mapper.MaterialCategoryMapper;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.owner.entity.OwnerObject;
import com.zufe.procurement.m1.owner.mapper.OwnerObjectMapper;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m5.stock.dto.FlowQuery;
import com.zufe.procurement.m5.stock.dto.StockAlertQuery;
import com.zufe.procurement.m5.stock.dto.StockQuery;
import com.zufe.procurement.m5.stock.entity.StockBalance;
import com.zufe.procurement.m5.stock.entity.StockFlow;
import com.zufe.procurement.m5.stock.mapper.StockBalanceMapper;
import com.zufe.procurement.m5.stock.mapper.StockFlowMapper;
import com.zufe.procurement.m5.stock.vo.BatchTraceVO;
import com.zufe.procurement.m5.stock.vo.FlowVO;
import com.zufe.procurement.m5.stock.vo.ReconcileVO;
import com.zufe.procurement.m5.stock.vo.StockAlertVO;
import com.zufe.procurement.m5.stock.vo.StockBalanceVO;
import com.zufe.procurement.m5.inbound.entity.InboundItem;
import com.zufe.procurement.m5.inbound.mapper.InboundItemMapper;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存查询服务，覆盖功能点 M5-2-3 批次追溯、M5-3 库存余额与可用量
 * （M5-3-1 至 M5-3-4）、M5-4 流水查询与对账（M5-4-2 至 M5-4-4）、
 * M5-5 库存预警与一致性（M5-5-1、M5-5-2、M5-5-4 对账面）。
 *
 * 业务口径集中在本层：
 * - **可用量不落库（R3）**：可用量 = 数量 − 冻结 − 预留，查询时现算；
 * - 数据范围按仓库维度注入：仅全量管理员免过滤；其他用户须显式拥有仓库范围。
 *   部门权限不隐式授予全仓库访问，未配置仓库范围时失败关闭；
 * - 预警判定基准是物料 safety_stock（M5-5-1）：可用量 < 下限即不足，
 *   下限为 0 的物料不参与预警（没有下限就没有不足）；
 * - 批次追溯（M5-2-3）返回该批次全部入库明细，作废单也返回并标注
 *   状态——批次历史是事实，让用户看到完整流转；
 * - 对账（M5-4-4）：指定时间点前流水累计 vs 当前余额逐键比对，
 *   双端差集都算差异（只有流水没有余额、或两者不一致）。
 */
@Service
public class StockService {

    private final StockBalanceMapper stockBalanceMapper;
    private final StockFlowMapper stockFlowMapper;
    private final InboundItemMapper inboundItemMapper;
    private final com.zufe.procurement.m5.inbound.mapper.InboundOrderMapper inboundOrderMapper;
    private final com.zufe.procurement.m1.supplier.mapper.SupplierMapper supplierMapper;
    private final com.zufe.procurement.m11.system.mapper.SysUserMapper userMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final DictItemMapper dictItemMapper;
    private final OwnerObjectMapper ownerObjectMapper;
    private final DataScopeService dataScopeService;

    public StockService(StockBalanceMapper stockBalanceMapper,
                        StockFlowMapper stockFlowMapper,
                        InboundItemMapper inboundItemMapper,
                        com.zufe.procurement.m5.inbound.mapper.InboundOrderMapper inboundOrderMapper,
                        com.zufe.procurement.m1.supplier.mapper.SupplierMapper supplierMapper,
                        com.zufe.procurement.m11.system.mapper.SysUserMapper userMapper,
                        MaterialMapper materialMapper,
                        MaterialCategoryMapper categoryMapper,
                        WarehouseMapper warehouseMapper,
                        DictItemMapper dictItemMapper,
                        OwnerObjectMapper ownerObjectMapper,
                        DataScopeService dataScopeService) {
        this.stockBalanceMapper = stockBalanceMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.inboundItemMapper = inboundItemMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.supplierMapper = supplierMapper;
        this.userMapper = userMapper;
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.warehouseMapper = warehouseMapper;
        this.dictItemMapper = dictItemMapper;
        this.ownerObjectMapper = ownerObjectMapper;
        this.dataScopeService = dataScopeService;
    }

    // ================= M5-3-1 库存余额查询 =================

    /**
     * 库存余额列表（M5-3-1 / M5-3-2）。默认按物料+仓库聚合；
     * groupByOwner=true 按归属对象拆行（M5-3-3）；
     * available=true 只返回有库存的行（可用量视图）。
     */
    public PageResult<StockBalanceVO> page(StockQuery query) {
        Set<Long> warehouseScope = warehouseScopeIds();
        Set<Long> materialIds = resolveMaterialIds(query.getMaterialId(), query.getMaterialKeyword());
        boolean groupByOwner = Boolean.TRUE.equals(query.getGroupByOwner());
        List<StockBalanceVO> rows = groupByOwner
                ? assembleOwnerRows(warehouseScope, materialIds)
                : assembleMaterialWarehouseRows(warehouseScope, materialIds);
        if (Boolean.TRUE.equals(query.getOnlyPositive()) || Boolean.TRUE.equals(query.getAvailable())) {
            rows.removeIf(row -> row.getAvailableQty() == null || row.getAvailableQty().signum() <= 0);
        }
        if (query.getWarehouseId() != null) {
            rows.removeIf(row -> !Objects.equals(row.getWarehouseId(), query.getWarehouseId()));
        }
        rows.sort((a, b) -> {
            int byWarehouse = Long.compare(
                    a.getWarehouseId() == null ? 0 : a.getWarehouseId(),
                    b.getWarehouseId() == null ? 0 : b.getWarehouseId());
            return byWarehouse != 0 ? byWarehouse
                    : Long.compare(a.getMaterialId() == null ? 0 : a.getMaterialId(),
                    b.getMaterialId() == null ? 0 : b.getMaterialId());
        });
        long total = rows.size();
        int from = (int) Math.min((Math.max(query.getPageNo(), 1) - 1) * Math.max(query.getPageSize(), 1), total);
        int to = (int) Math.min(from + Math.max(query.getPageSize(), 1), total);
        return PageResult.of(new ArrayList<>(rows.subList(from, to)), total,
                Math.max(query.getPageNo(), 1), Math.max(query.getPageSize(), 1));
    }

    /**
     * 可用量查询（M5-3-2）：物料 + 仓库的单行口径，
     * 可用量 = 数量 − 冻结 − 预留（R3，后端现算）。
     */
    public StockBalanceVO available(Long materialId, Long warehouseId) {
        if (materialId == null || warehouseId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择物料与仓库");
        }
        Set<Long> warehouseScope = warehouseScopeIds();
        if (!warehouseScope.isEmpty() && !warehouseScope.contains(warehouseId)) {
            throw new BizException(ResultCode.NO_PERMISSION, "仓库不在您的数据范围内");
        }
        List<StockBalanceMapper.MaterialWarehouseQtyRow> rows =
                stockBalanceMapper.sumByMaterialWarehouse(List.of(warehouseId), List.of(materialId));
        StockBalanceVO vo = new StockBalanceVO();
        vo.setMaterialId(materialId);
        vo.setWarehouseId(warehouseId);
        Material material = materialMapper.selectById(materialId);
        if (material != null) {
            vo.setMaterialNo(material.getMaterialNo());
            vo.setMaterialName(material.getMaterialName());
            vo.setSpec(material.getSpec());
            vo.setCategoryName(categoryName(material.getCategoryId()));
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse != null) {
            vo.setWarehouseName(warehouse.getWarehouseName());
        }
        BigDecimal qty = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).getQuantity();
        BigDecimal frozen = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).getFrozenQty();
        BigDecimal reserved = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).getReservedQty();
        vo.setQuantity(qty);
        vo.setFrozenQty(frozen);
        vo.setReservedQty(reserved);
        vo.setAvailableQty(qty.subtract(frozen).subtract(reserved).setScale(4, java.math.RoundingMode.HALF_UP));
        return vo;
    }

    // ================= M5-3-4 库存余额导出 =================

    /** 库存余额导出（CSV，UTF-8 BOM，零依赖口径与各模块一致）。 */
    public byte[] exportCsv(StockQuery query) {
        query.setPageNo(1);
        query.setPageSize(Integer.MAX_VALUE / 2);
        List<StockBalanceVO> rows = page(query).getRecords();
        String[] headers = {"物料编号", "物料名称", "规格", "分类", "仓库", "库存数量",
                "冻结量", "预留量", "可用量"};
        List<String[]> lines = new ArrayList<>();
        for (StockBalanceVO row : rows) {
            lines.add(new String[]{
                    nullToEmpty(row.getMaterialNo()), nullToEmpty(row.getMaterialName()),
                    nullToEmpty(row.getSpec()), nullToEmpty(row.getCategoryName()),
                    nullToEmpty(row.getWarehouseName()),
                    plain(row.getQuantity()), plain(row.getFrozenQty()),
                    plain(row.getReservedQty()), plain(row.getAvailableQty())
            });
        }
        return CsvWriter.toCsv(headers, lines);
    }

    // ================= M5-5-1 / M5-5-2 库存预警 =================

    /**
     * 库存不足清单（M5-5-1）：可用量 < 物料预警下限的物料行，
     * 缺口 = 下限 − 可用量。groupBy=warehouse 按仓库汇总（M5-5-2），
     * groupBy=category 按分类汇总；缺省按物料明细列行。
     */
    public List<StockAlertVO> alert(StockAlertQuery query) {
        Set<Long> warehouseScope = warehouseScopeIds();
        List<Material> materials = materialMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<Material>().eq(Material::getStatus, 1));
        List<StockBalanceMapper.MaterialQtyRow> sums =
                stockBalanceMapper.sumByMaterial(warehouseScope.isEmpty() ? null : warehouseScope, null);
        Map<Long, StockBalanceMapper.MaterialQtyRow> sumByMaterial = sums.stream()
                .collect(Collectors.toMap(StockBalanceMapper.MaterialQtyRow::getMaterialId,
                        Function.identity(), (a, b) -> a));
        Map<Long, String> categoryNames = new HashMap<>();
        for (MaterialCategory category : categoryMapper.selectAllForTree()) {
            categoryNames.put(category.getId(), category.getCategoryName());
        }
        List<StockAlertVO> rows = new ArrayList<>();
        for (Material material : materials) {
            StockBalanceMapper.MaterialQtyRow sum = sumByMaterial.get(material.getId());
            BigDecimal qty = sum == null || sum.getQuantity() == null ? BigDecimal.ZERO : sum.getQuantity();
            BigDecimal frozen = sum == null || sum.getFrozenQty() == null ? BigDecimal.ZERO : sum.getFrozenQty();
            BigDecimal reserved = sum == null || sum.getReservedQty() == null
                    ? BigDecimal.ZERO : sum.getReservedQty();
            BigDecimal available = qty.subtract(frozen).subtract(reserved);
            BigDecimal safety = material.getSafetyStock() == null ? BigDecimal.ZERO : material.getSafetyStock();
            if (safety.signum() <= 0 || available.compareTo(safety) >= 0) {
                continue;
            }
            StockAlertVO vo = new StockAlertVO();
            vo.setMaterialId(material.getId());
            vo.setMaterialNo(material.getMaterialNo());
            vo.setMaterialName(material.getMaterialName());
            vo.setSpec(material.getSpec());
            vo.setCategoryName(categoryNames.get(material.getCategoryId()));
            vo.setQuantity(qty);
            vo.setAvailableQty(available.setScale(4, java.math.RoundingMode.HALF_UP));
            vo.setSafetyStock(safety);
            vo.setGapQty(safety.subtract(available).setScale(4, java.math.RoundingMode.HALF_UP));
            rows.add(vo);
        }
        rows.sort((a, b) -> b.getGapQty().compareTo(a.getGapQty()));
        return rows;
    }

    /**
     * 短缺物料统计（M5-5-2）：按仓库或物料分类汇总缺口。
     * 汇总口径：把 alert 的明细行按维度分组合并缺口与物料行数。
     */
    public List<StockAlertVO> alertStat(StockAlertQuery query) {
        List<StockAlertVO> details = alert(query);
        String groupBy = query.getGroupBy();
        if (!StringUtils.hasText(groupBy) || (!"warehouse".equals(groupBy) && !"category".equals(groupBy))) {
            return details;
        }
        // 明细行是物料维度（跨仓库合计），按维度汇总时把物料行按维度拆：
        // 按仓库汇总 = 逐仓库重算缺口；按分类汇总 = 明细行按分类合并。
        if ("category".equals(groupBy)) {
            Map<String, List<StockAlertVO>> grouped = details.stream()
                    .collect(Collectors.groupingBy(r -> r.getCategoryName() == null ? "" : r.getCategoryName(),
                            LinkedHashMap::new, Collectors.toList()));
            List<StockAlertVO> result = new ArrayList<>();
            grouped.forEach((category, list) -> {
                StockAlertVO vo = new StockAlertVO();
                vo.setCategoryName(category.isEmpty() ? null : category);
                vo.setMaterialCount(list.size());
                vo.setGapQty(list.stream().map(StockAlertVO::getGapQty).reduce(BigDecimal.ZERO, BigDecimal::add));
                result.add(vo);
            });
            result.sort((a, b) -> b.getGapQty().compareTo(a.getGapQty()));
            return result;
        }
        // 按仓库汇总：逐仓库算「低于下限的缺口」，阈值仍是物料下限（跨仓库合计口径）
        Set<Long> warehouseScope = warehouseScopeIds();
        List<Material> materials = materialMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<Material>().eq(Material::getStatus, 1));
        List<Long> materialIds = materials.stream().map(Material::getId).toList();
        List<StockBalanceMapper.GroupQtyRow> rows = materialIds.isEmpty() ? List.of()
                : stockBalanceMapper.sumByMaterialWarehouseOwner(
                        warehouseScope.isEmpty() ? null : warehouseScope, materialIds);
        Map<Long, String> warehouseNames = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getWarehouseName, (a, b) -> a));
        Map<Long, Material> materialById = materials.stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<StockBalanceMapper.GroupQtyRow>> byWarehouse = rows.stream()
                .filter(r -> r.getOwnerType() == null || r.getOwnerType() == 1)
                .collect(Collectors.groupingBy(StockBalanceMapper.GroupQtyRow::getWarehouseId));
        List<StockAlertVO> result = new ArrayList<>();
        byWarehouse.forEach((warehouseId, list) -> {
            BigDecimal gap = BigDecimal.ZERO;
            int count = 0;
            for (Material material : materials) {
                BigDecimal available = list.stream()
                        .filter(r -> Objects.equals(r.getMaterialId(), material.getId()))
                        .map(r -> nz(r.getQuantity()).subtract(nz(r.getFrozenQty())).subtract(nz(r.getReservedQty())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal safety = nz(material.getSafetyStock());
                if (safety.signum() > 0 && available.compareTo(safety) < 0) {
                    gap = gap.add(safety.subtract(available));
                    count++;
                }
            }
            if (count > 0) {
                StockAlertVO vo = new StockAlertVO();
                vo.setWarehouseId(warehouseId);
                vo.setWarehouseName(warehouseNames.get(warehouseId));
                vo.setMaterialCount(count);
                vo.setGapQty(gap.setScale(4, java.math.RoundingMode.HALF_UP));
                result.add(vo);
            }
        });
        result.sort((a, b) -> b.getGapQty().compareTo(a.getGapQty()));
        return result;
    }

    // ================= M5-2-3 批次追溯 =================

    /**
     * 批次追溯：批次号 → 入库明细 → 入库单 → 订单 → 供应商。
     * 作废单的明细也返回（批次历史是事实，标注入库单状态）。
     */
    public BatchTraceVO batchTrace(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请输入批次号");
        }
        String key = batchNo.trim();
        List<InboundItem> items = inboundItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InboundItem>()
                        .eq(InboundItem::getBatchNo, key)
                        .orderByAsc(InboundItem::getId));
        BatchTraceVO vo = new BatchTraceVO();
        vo.setBatchNo(key);
        if (items.isEmpty()) {
            vo.setMaterialCount(0);
            vo.setInboundCount(0);
            vo.setRows(List.of());
            return vo;
        }
        Set<Long> inboundIds = items.stream().map(InboundItem::getInboundId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, com.zufe.procurement.m5.inbound.entity.InboundOrder> inbounds =
                inboundOrderMapper.selectBatchIds(inboundIds).stream()
                        .collect(Collectors.toMap(
                                com.zufe.procurement.m5.inbound.entity.InboundOrder::getId,
                                Function.identity(), (a, b) -> a));
        Map<Long, String> materialNames = lookupNames(items.stream()
                        .map(InboundItem::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialName);
        Map<Long, String> materialNos = lookupNames(items.stream()
                        .map(InboundItem::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialNo);
        Map<Long, String> warehouseNames = lookupNames(inbounds.values().stream()
                        .map(com.zufe.procurement.m5.inbound.entity.InboundOrder::getWarehouseId)
                        .filter(Objects::nonNull).distinct().toList(),
                warehouseMapper::selectBatchIds, Warehouse::getId, Warehouse::getWarehouseName);
        Map<Long, String> supplierNames = lookupNames(inbounds.values().stream()
                        .map(com.zufe.procurement.m5.inbound.entity.InboundOrder::getSupplierId)
                        .filter(Objects::nonNull).distinct().toList(),
                supplierMapper::selectBatchIds,
                com.zufe.procurement.m1.supplier.entity.Supplier::getId,
                com.zufe.procurement.m1.supplier.entity.Supplier::getSupplierName);
        List<BatchTraceVO.Row> rows = new ArrayList<>();
        for (InboundItem item : items) {
            com.zufe.procurement.m5.inbound.entity.InboundOrder inbound = inbounds.get(item.getInboundId());
            if (inbound == null) {
                continue;
            }
            BatchTraceVO.Row row = new BatchTraceVO.Row();
            row.setInboundItemId(item.getId());
            row.setInboundId(inbound.getId());
            row.setInboundNo(inbound.getInboundNo());
            row.setInboundStatus(inbound.getStatus());
            row.setInboundStatusName(inboundStatusName(inbound.getStatus()));
            row.setInboundDate(inbound.getInboundDate());
            row.setMaterialId(item.getMaterialId());
            row.setMaterialNo(materialNos.get(item.getMaterialId()));
            row.setMaterialName(materialNames.get(item.getMaterialId()));
            row.setQuantity(item.getQuantity());
            row.setWarehouseId(inbound.getWarehouseId());
            row.setWarehouseName(warehouseNames.get(inbound.getWarehouseId()));
            row.setOrderId(inbound.getOrderId());
            row.setOrderNo(inbound.getOrderNo());
            row.setSupplierId(inbound.getSupplierId());
            row.setSupplierName(supplierNames.get(inbound.getSupplierId()));
            row.setInspectionId(inbound.getInspectionId());
            row.setInspectionNo(inbound.getInspectionNo());
            rows.add(row);
        }
        vo.setRows(rows);
        vo.setMaterialCount((int) rows.stream().map(BatchTraceVO.Row::getMaterialId).distinct().count());
        vo.setInboundCount((int) rows.stream().map(BatchTraceVO.Row::getInboundId).distinct().count());
        return vo;
    }

    // ================= M5-4-2 流水查询 =================

    /** 流水分页（M5-4-2）：物料/仓库/时间/单据类型/批次筛选 + 仓库范围注入。 */
    public PageResult<FlowVO> flowPage(FlowQuery query) {
        Set<Long> warehouseScope = warehouseScopeIds();
        Set<Long> materialIds = resolveMaterialIds(query.getMaterialId(), query.getMaterialKeyword());
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockFlow> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockFlow>()
                        .eq(query.getSourceType() != null, StockFlow::getSourceType, query.getSourceType())
                        .eq(query.getSourceId() != null, StockFlow::getSourceId, query.getSourceId())
                        .eq(query.getChangeType() != null, StockFlow::getChangeType, query.getChangeType())
                        .eq(StringUtils.hasText(query.getBatchNo()), StockFlow::getBatchNo,
                                query.getBatchNo() == null ? null : query.getBatchNo().trim())
                        .ge(query.getDateFrom() != null, StockFlow::getFlowTime,
                                query.getDateFrom() == null ? null
                                        : LocalDate.parse(query.getDateFrom()).atStartOfDay())
                        .lt(query.getDateTo() != null, StockFlow::getFlowTime,
                                query.getDateTo() == null ? null
                                        : LocalDate.parse(query.getDateTo()).plusDays(1).atStartOfDay())
                        .orderByDesc(StockFlow::getId);
        if (query.getWarehouseId() != null) {
            wrapper.eq(StockFlow::getWarehouseId, query.getWarehouseId());
        } else if (!warehouseScope.isEmpty()) {
            wrapper.in(StockFlow::getWarehouseId, warehouseScope);
        }
        if (materialIds != null) {
            if (materialIds.isEmpty()) {
                return PageResult.of(List.of(), 0, 1, 10);
            }
            wrapper.in(StockFlow::getMaterialId, materialIds);
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<StockFlow> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        Math.max(query.getPageNo(), 1), Math.min(Math.max(query.getPageSize(), 1), 200));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<StockFlow> result =
                stockFlowMapper.selectPage(page, wrapper);
        List<StockFlow> flows = result.getRecords();
        if (flows.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        Map<Long, String> materialNames = lookupNames(flows.stream()
                        .map(StockFlow::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialName);
        Map<Long, String> materialNos = lookupNames(flows.stream()
                        .map(StockFlow::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialNo);
        Map<Long, String> specs = lookupNames(flows.stream()
                        .map(StockFlow::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getSpec);
        Map<Long, String> warehouseNames = lookupNames(flows.stream()
                        .map(StockFlow::getWarehouseId).filter(Objects::nonNull).distinct().toList(),
                warehouseMapper::selectBatchIds, Warehouse::getId, Warehouse::getWarehouseName);
        Map<Long, String> operatorNames = lookupNames(flows.stream()
                        .map(StockFlow::getOperatorId).filter(Objects::nonNull).distinct().toList(),
                userMapper::selectBatchIds,
                com.zufe.procurement.m11.system.entity.SysUser::getId,
                com.zufe.procurement.m11.system.entity.SysUser::getRealName);
        Set<Long> sourceIds = flows.stream().map(StockFlow::getSourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, com.zufe.procurement.m5.inbound.entity.InboundOrder> inbounds =
                inboundOrderMapper.selectBatchIds(sourceIds).stream()
                        .collect(Collectors.toMap(
                                com.zufe.procurement.m5.inbound.entity.InboundOrder::getId,
                                Function.identity(), (a, b) -> a));
        List<FlowVO> result2 = new ArrayList<>();
        for (StockFlow flow : flows) {
            FlowVO vo = new FlowVO();
            vo.setId(flow.getId());
            vo.setMaterialId(flow.getMaterialId());
            vo.setMaterialNo(materialNos.get(flow.getMaterialId()));
            vo.setMaterialName(materialNames.get(flow.getMaterialId()));
            vo.setSpec(specs.get(flow.getMaterialId()));
            vo.setWarehouseId(flow.getWarehouseId());
            vo.setWarehouseName(warehouseNames.get(flow.getWarehouseId()));
            vo.setBatchNo(flow.getBatchNo());
            vo.setOwnerType(flow.getOwnerType());
            vo.setOwnerTypeName(ownerTypeName(flow.getOwnerType()));
            vo.setOwnerId(flow.getOwnerId());
            vo.setOwnerNo(flow.getOwnerNo());
            vo.setChangeType(flow.getChangeType());
            vo.setChangeTypeName(flow.getChangeType() != null && flow.getChangeType() == 2
                    ? "作废冲减" : "入库增加");
            vo.setChangeQty(flow.getChangeQty());
            vo.setBeforeQty(flow.getBeforeQty());
            vo.setAfterQty(flow.getAfterQty());
            vo.setSourceType(flow.getSourceType());
            vo.setSourceTypeName("采购入库单");
            vo.setSourceId(flow.getSourceId());
            vo.setSourceNo(flow.getSourceNo());
            vo.setOperatorName(operatorNames.get(flow.getOperatorId()));
            vo.setFlowTime(flow.getFlowTime());
            com.zufe.procurement.m5.inbound.entity.InboundOrder inbound = inbounds.get(flow.getSourceId());
            if (inbound != null) {
                FlowVO.SourceBrief brief = new FlowVO.SourceBrief();
                brief.setInboundNo(inbound.getInboundNo());
                brief.setOrderId(inbound.getOrderId());
                brief.setOrderNo(inbound.getOrderNo());
                brief.setInspectionId(inbound.getInspectionId());
                brief.setInspectionNo(inbound.getInspectionNo());
                brief.setInboundStatus(inbound.getStatus());
                brief.setInboundStatusName(inboundStatusName(inbound.getStatus()));
                vo.setSource(brief);
            }
            result2.add(vo);
        }
        return PageResult.of(result2, result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ================= M5-4-4 余额与流水对账 =================

    /**
     * 对账（M5-4-4）：指定时间点前流水累计 vs 当前余额，逐键
     * （物料+仓库+批次+归属）比对。双端差集都算差异——
     * 只有流水没有余额、或余额与流水不一致，都报出来。
     */
    public ReconcileVO reconcile(String asOfText, Long warehouseId) {
        LocalDateTime asOf;
        try {
            asOf = StringUtils.hasText(asOfText)
                    ? LocalDate.parse(asOfText.trim()).atTime(LocalTime.MAX)
                    : LocalDateTime.now();
        } catch (Exception e) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "对账时间点格式应为 yyyy-MM-dd");
        }
        Set<Long> warehouseScope = warehouseScopeIds();
        List<Long> warehouseFilter = new ArrayList<>();
        if (warehouseId != null) {
            warehouseFilter.add(warehouseId);
        } else if (!warehouseScope.isEmpty()) {
            warehouseFilter.addAll(warehouseScope);
        }
        List<StockFlowMapper.FlowSumRow> flowRows = stockFlowMapper.sumChangeQtyByKey(
                asOf, warehouseFilter.isEmpty() ? null : warehouseFilter, null);
        List<StockBalance> balances = stockBalanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockBalance>()
                        .in(!warehouseFilter.isEmpty(), StockBalance::getWarehouseId, warehouseFilter));
        Map<Long, String> materialNames = lookupNames(flowRows.stream()
                        .map(StockFlowMapper.FlowSumRow::getMaterialId).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialName);
        materialNames.putAll(lookupNames(balances.stream()
                        .map(StockBalance::getMaterialId).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialName));
        Map<Long, String> materialNos = lookupNames(flowRows.stream()
                        .map(StockFlowMapper.FlowSumRow::getMaterialId).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialNo);
        materialNos.putAll(lookupNames(balances.stream()
                        .map(StockBalance::getMaterialId).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialNo));
        Map<Long, String> warehouseNames = lookupNames(flowRows.stream()
                        .map(StockFlowMapper.FlowSumRow::getWarehouseId).distinct().toList(),
                warehouseMapper::selectBatchIds, Warehouse::getId, Warehouse::getWarehouseName);
        warehouseNames.putAll(lookupNames(balances.stream()
                        .map(StockBalance::getWarehouseId).distinct().toList(),
                warehouseMapper::selectBatchIds, Warehouse::getId, Warehouse::getWarehouseName));

        // 键：物料|仓库|批次|归属类型|归属id
        Map<String, ReconcileVO.Row> merged = new java.util.LinkedHashMap<>();
        for (StockBalance balance : balances) {
            String key = keyOf(balance.getMaterialId(), balance.getWarehouseId(), balance.getBatchNo(),
                    balance.getOwnerType(), balance.getOwnerId());
            ReconcileVO.Row row = merged.computeIfAbsent(key, k -> newRow(materialNos, materialNames,
                    warehouseNames, balance.getMaterialId(), balance.getWarehouseId(),
                    balance.getBatchNo(), balance.getOwnerNo()));
            row.setBalanceQty(nz(balance.getQuantity()));
        }
        for (StockFlowMapper.FlowSumRow flow : flowRows) {
            String key = keyOf(flow.getMaterialId(), flow.getWarehouseId(), flow.getBatchNo(),
                    flow.getOwnerType(), flow.getOwnerId());
            ReconcileVO.Row row = merged.computeIfAbsent(key, k -> newRow(materialNos, materialNames,
                    warehouseNames, flow.getMaterialId(), flow.getWarehouseId(),
                    flow.getBatchNo(), flow.getOwnerNo()));
            row.setFlowQty(nz(flow.getFlowQty()));
        }
        ReconcileVO vo = new ReconcileVO();
        vo.setAsOf(asOf.toLocalDate().toString());
        vo.setTotalKeys(merged.size());
        List<ReconcileVO.Row> rows = new ArrayList<>(merged.values());
        int matched = 0;
        BigDecimal flowTotal = BigDecimal.ZERO;
        BigDecimal balanceTotal = BigDecimal.ZERO;
        for (ReconcileVO.Row row : rows) {
            BigDecimal flowQty = nz(row.getFlowQty());
            BigDecimal balanceQty = nz(row.getBalanceQty());
            row.setDiffQty(flowQty.subtract(balanceQty).setScale(4, java.math.RoundingMode.HALF_UP));
            row.setMatched(row.getDiffQty().signum() == 0);
            if (row.getMatched()) {
                matched++;
            }
            flowTotal = flowTotal.add(flowQty);
            balanceTotal = balanceTotal.add(balanceQty);
        }
        vo.setRows(rows);
        vo.setMatchedKeys(matched);
        vo.setDiffKeys(rows.size() - matched);
        vo.setFlowTotal(flowTotal.setScale(4, java.math.RoundingMode.HALF_UP));
        vo.setBalanceTotal(balanceTotal.setScale(4, java.math.RoundingMode.HALF_UP));
        return vo;
    }

    // ================= 私有 =================

    private List<StockBalanceVO> assembleMaterialWarehouseRows(Set<Long> warehouseScope, Set<Long> materialIds) {
        List<StockBalanceMapper.MaterialWarehouseQtyRow> rows = stockBalanceMapper.sumByMaterialWarehouse(
                warehouseScope.isEmpty() ? null : warehouseScope,
                materialIds == null ? null : materialIds);
        List<Long> materialIds2 = rows.stream().map(StockBalanceMapper.MaterialWarehouseQtyRow::getMaterialId)
                .distinct().toList();
        Map<Long, String> materialNames = lookupNames(materialIds2,
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialName);
        Map<Long, String> materialNos = lookupNames(materialIds2,
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialNo);
        Map<Long, String> specs = lookupNames(materialIds2,
                materialMapper::selectBatchIds, Material::getId, Material::getSpec);
        Map<Long, Long> categoryIds = new HashMap<>();
        // 空集合不能直接进 selectBatchIds：MP 会拼出 IN () 触发 SQL 语法错误（命中零行时必现）
        if (!materialIds2.isEmpty()) {
            for (Material material : materialMapper.selectBatchIds(materialIds2)) {
                categoryIds.put(material.getId(), material.getCategoryId());
            }
        }
        Map<Long, String> categoryNames = new HashMap<>();
        for (MaterialCategory category : categoryMapper.selectAllForTree()) {
            categoryNames.put(category.getId(), category.getCategoryName());
        }
        Map<Long, String> warehouseNames = lookupNames(rows.stream()
                        .map(StockBalanceMapper.MaterialWarehouseQtyRow::getWarehouseId).distinct().toList(),
                warehouseMapper::selectBatchIds, Warehouse::getId, Warehouse::getWarehouseName);
        List<StockBalanceVO> result = new ArrayList<>();
        for (StockBalanceMapper.MaterialWarehouseQtyRow row : rows) {
            StockBalanceVO vo = new StockBalanceVO();
            vo.setMaterialId(row.getMaterialId());
            vo.setMaterialNo(materialNos.get(row.getMaterialId()));
            vo.setMaterialName(materialNames.get(row.getMaterialId()));
            vo.setSpec(specs.get(row.getMaterialId()));
            vo.setCategoryName(categoryNames.get(categoryIds.get(row.getMaterialId())));
            vo.setWarehouseId(row.getWarehouseId());
            vo.setWarehouseName(warehouseNames.get(row.getWarehouseId()));
            vo.setQuantity(nz(row.getQuantity()));
            vo.setFrozenQty(nz(row.getFrozenQty()));
            vo.setReservedQty(nz(row.getReservedQty()));
            vo.setAvailableQty(vo.getQuantity().subtract(vo.getFrozenQty()).subtract(vo.getReservedQty())
                    .setScale(4, java.math.RoundingMode.HALF_UP));
            result.add(vo);
        }
        attachBatchRows(result, warehouseScope, materialIds);
        return result;
    }

    /**
     * 批次展开（M5-3-1）：给物料+仓库汇总行挂上该维度下的批次构成，
     * 展开合计与行数量必然一致（同范围参数、同口径汇总）；
     * 空串批次（无批次管理）排在最后，避免混在正常批次号中间。
     */
    private void attachBatchRows(List<StockBalanceVO> rows, Set<Long> warehouseScope, Set<Long> materialIds) {
        if (rows.isEmpty()) {
            return;
        }
        List<StockBalanceMapper.BatchQtyRow> batchRows = stockBalanceMapper.sumByMaterialWarehouseBatch(
                warehouseScope.isEmpty() ? null : warehouseScope,
                materialIds == null ? null : materialIds);
        Map<String, List<StockBalanceMapper.BatchQtyRow>> byKey = batchRows.stream()
                .collect(Collectors.groupingBy(r -> r.getMaterialId() + "|" + r.getWarehouseId()));
        for (StockBalanceVO vo : rows) {
            List<StockBalanceMapper.BatchQtyRow> list = byKey.getOrDefault(
                    vo.getMaterialId() + "|" + vo.getWarehouseId(), List.of());
            List<StockBalanceVO.BatchRow> batches = new ArrayList<>();
            for (StockBalanceMapper.BatchQtyRow row : list) {
                StockBalanceVO.BatchRow batch = new StockBalanceVO.BatchRow();
                batch.setBatchNo(row.getBatchNo());
                batch.setQuantity(nz(row.getQuantity()));
                batch.setFrozenQty(nz(row.getFrozenQty()));
                batch.setReservedQty(nz(row.getReservedQty()));
                batch.setAvailableQty(nz(row.getQuantity()).subtract(nz(row.getFrozenQty()))
                        .subtract(nz(row.getReservedQty())).setScale(4, java.math.RoundingMode.HALF_UP));
                batches.add(batch);
            }
            batches.sort((a, b) -> {
                String x = a.getBatchNo() == null ? "" : a.getBatchNo();
                String y = b.getBatchNo() == null ? "" : b.getBatchNo();
                if (x.isEmpty() != y.isEmpty()) {
                    return x.isEmpty() ? 1 : -1;
                }
                return x.compareTo(y);
            });
            vo.setBatches(batches);
        }
    }

    private List<StockBalanceVO> assembleOwnerRows(Set<Long> warehouseScope, Set<Long> materialIds) {
        List<StockBalanceMapper.GroupQtyRow> rows = stockBalanceMapper.sumByMaterialWarehouseOwner(
                warehouseScope.isEmpty() ? null : warehouseScope,
                materialIds == null ? null : materialIds);
        List<Long> materialIds2 = rows.stream().map(StockBalanceMapper.GroupQtyRow::getMaterialId)
                .distinct().toList();
        Map<Long, String> materialNames = lookupNames(materialIds2,
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialName);
        Map<Long, String> materialNos = lookupNames(materialIds2,
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialNo);
        Map<Long, String> warehouseNames = lookupNames(rows.stream()
                        .map(StockBalanceMapper.GroupQtyRow::getWarehouseId).distinct().toList(),
                warehouseMapper::selectBatchIds, Warehouse::getId, Warehouse::getWarehouseName);
        Map<Long, String> ownerNames = ownerNames(rows.stream()
                .map(StockBalanceMapper.GroupQtyRow::getOwnerId).filter(Objects::nonNull).distinct().toList());
        List<StockBalanceVO> result = new ArrayList<>();
        for (StockBalanceMapper.GroupQtyRow row : rows) {
            StockBalanceVO vo = new StockBalanceVO();
            vo.setMaterialId(row.getMaterialId());
            vo.setMaterialNo(materialNos.get(row.getMaterialId()));
            vo.setMaterialName(materialNames.get(row.getMaterialId()));
            vo.setWarehouseId(row.getWarehouseId());
            vo.setWarehouseName(warehouseNames.get(row.getWarehouseId()));
            vo.setOwnerType(row.getOwnerType());
            vo.setOwnerTypeName(ownerTypeName(row.getOwnerType()));
            vo.setOwnerId(row.getOwnerId());
            vo.setOwnerNo(row.getOwnerNo());
            vo.setOwnerName(ownerNames.get(row.getOwnerId()));
            vo.setQuantity(nz(row.getQuantity()));
            vo.setFrozenQty(nz(row.getFrozenQty()));
            vo.setReservedQty(nz(row.getReservedQty()));
            vo.setAvailableQty(vo.getQuantity().subtract(vo.getFrozenQty()).subtract(vo.getReservedQty())
                    .setScale(4, java.math.RoundingMode.HALF_UP));
            result.add(vo);
        }
        return result;
    }

    private Map<Long, String> ownerNames(List<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return ownerObjectMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(OwnerObject::getId, OwnerObject::getOwnerName, (a, b) -> a));
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        for (MaterialCategory category : categoryMapper.selectAllForTree()) {
            if (Objects.equals(category.getId(), categoryId)) {
                return category.getCategoryName();
            }
        }
        return null;
    }

    /** 关键字解析成物料 id 集合；无关键字返回 null（不加条件）。 */
    private Set<Long> resolveMaterialIds(Long materialId, String keyword) {
        if (materialId != null) {
            return Set.of(materialId);
        }
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String kw = keyword.trim();
        List<Material> materials = materialMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Material>()
                        .and(w -> w.like(Material::getMaterialNo, kw).or().like(Material::getMaterialName, kw)));
        return materials.stream().map(Material::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> warehouseScopeIds() {
        ScopeCondition scope = currentScope();
        if (scope.isAll()) {
            return Set.of();
        }
        if (!scope.hasWarehouse()) {
            throw new BizException(ResultCode.NO_PERMISSION, "未配置仓库数据范围，不能查询库存");
        }
        return scope.getWarehouseIds();
    }

    private ScopeCondition currentScope() {
        CurrentUser user = AuthContext.get();
        return user == null ? new ScopeCondition() : user.getScope();
    }

    private String ownerTypeName(Integer ownerType) {
        if (ownerType == null) {
            return null;
        }
        return switch (ownerType) {
            case 1 -> "公共";
            case 2 -> "项目";
            case 3 -> "工单";
            default -> "";
        };
    }

    private String inboundStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "草稿";
            case 2 -> "已确认";
            case 3 -> "已作废";
            default -> "";
        };
    }

    private ReconcileVO.Row newRow(Map<Long, String> materialNos, Map<Long, String> materialNames,
                                   Map<Long, String> warehouseNames, Long materialId, Long warehouseId,
                                   String batchNo, String ownerNo) {
        ReconcileVO.Row row = new ReconcileVO.Row();
        row.setMaterialId(materialId);
        row.setMaterialNo(materialNos.get(materialId));
        row.setMaterialName(materialNames.get(materialId));
        row.setWarehouseId(warehouseId);
        row.setWarehouseName(warehouseNames.get(warehouseId));
        row.setBatchNo(batchNo);
        row.setOwnerNo(ownerNo);
        row.setFlowQty(BigDecimal.ZERO);
        row.setBalanceQty(BigDecimal.ZERO);
        return row;
    }

    private String keyOf(Long materialId, Long warehouseId, String batchNo, Integer ownerType, Long ownerId) {
        return materialId + "|" + warehouseId + "|" + (batchNo == null ? "" : batchNo)
                + "|" + ownerType + "|" + ownerId;
    }

    private <E> Map<Long, String> lookupNames(List<Long> ids, Function<Collection<Long>, List<E>> loader,
                                              Function<E, Long> idGetter, Function<E, String> nameGetter) {
        // 空结果用 HashMap：Map.of().get(null) 会 NPE，而 ownerId 等字段可空
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return loader.apply(ids).stream()
                .collect(Collectors.toMap(idGetter, nameGetter, (a, b) -> a));
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
