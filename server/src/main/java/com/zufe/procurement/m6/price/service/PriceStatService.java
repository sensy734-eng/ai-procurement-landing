package com.zufe.procurement.m6.price.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m11.param.entity.SysParam;
import com.zufe.procurement.m11.param.mapper.SysParamMapper;
import com.zufe.procurement.m11.system.service.OperationLogService;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import com.zufe.procurement.m3.order.entity.PurchaseOrderItem;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderItemMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m6.price.dto.StatQuery;
import com.zufe.procurement.m6.price.entity.PriceReference;
import com.zufe.procurement.m6.price.entity.PriceStatSnapshot;
import com.zufe.procurement.m6.price.mapper.PriceReferenceMapper;
import com.zufe.procurement.m6.price.mapper.PriceStatSnapshotMapper;
import com.zufe.procurement.m6.price.vo.PriceHistoryVO;
import com.zufe.procurement.m6.price.vo.PriceStatVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 成交价统计快照（M6-1-2、M6-1-3）。
 *
 * 口径（E 域约束 + sys_param 固化）：
 * - 查询链路只读快照，刷新由运营动作显式触发（refresh），不实时计算；
 * - 窗口：price_stat_window_type（LAST_N_DEALS 按笔数 / LAST_N_DAYS 按天数）
 *   + price_stat_window_value；窗口描述串形如 LAST_5_DEALS / LAST_180_DAYS；
 * - 历史成交 = 已审核通过（lifecycle 3/4 且 audit_status=2）的订单行，
 *   草稿/审批中/已作废不计入；
 * - 均价 = 窗口内 Σ金额 / Σ数量（金额加权），4 位；
 * - 最近一次成交价 = 窗口内按订单时间最新的第一笔，来源单号溯源（M6-4-2）；
 * - 无样本的物料不写快照（不伪造）；supplier_id=0 为全部供应商口径。
 */
@Service
public class PriceStatService {

    private static final String PARAM_WINDOW_TYPE = "price_stat_window_type";
    private static final String PARAM_WINDOW_VALUE = "price_stat_window_value";
    private static final int STAT_AVG = PriceStatSnapshot.STAT_AVG;
    private static final int STAT_LAST = PriceStatSnapshot.STAT_LAST;

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PriceStatSnapshotMapper snapshotMapper;
    private final PriceReferenceMapper priceReferenceMapper;
    private final SysParamMapper sysParamMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final OperationLogService operationLogService;

    public PriceStatService(PurchaseOrderMapper orderMapper,
                            PurchaseOrderItemMapper orderItemMapper,
                            PriceStatSnapshotMapper snapshotMapper,
                            PriceReferenceMapper priceReferenceMapper,
                            SysParamMapper sysParamMapper,
                            MaterialMapper materialMapper,
                            SupplierMapper supplierMapper,
                            OperationLogService operationLogService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.snapshotMapper = snapshotMapper;
        this.priceReferenceMapper = priceReferenceMapper;
        this.sysParamMapper = sysParamMapper;
        this.materialMapper = materialMapper;
        this.supplierMapper = supplierMapper;
        this.operationLogService = operationLogService;
    }

    /** 查询成交统计（只读快照），无快照的物料返回空值行（不伪造） */
    public List<PriceStatVO> list(StatQuery query) {
        Map<Long, Material> materials = new HashMap<>();
        for (Material material : materialMapper.selectList(
                new LambdaQueryWrapper<Material>().isNotNull(Material::getId))) {
            materials.put(material.getId(), material);
        }
        List<PriceStatVO> result = new ArrayList<>();
        if (query.getMaterialId() != null) {
            result.add(assemble(query.getMaterialId(), query.getSupplierId(), materials));
            return result;
        }
        // 全部物料：有快照的 + 有参考价的（便于对比展示）
        java.util.LinkedHashSet<Long> materialIds = new java.util.LinkedHashSet<>();
        materialIds.addAll(snapshotMapper.selectDistinctMaterialIds());
        for (PriceReference ref : priceReferenceMapper.selectList(
                new LambdaQueryWrapper<PriceReference>()
                        .eq(PriceReference::getStatus, 1)
                        .eq(PriceReference::getIsDeleted, 0))) {
            materialIds.add(ref.getMaterialId());
        }
        for (Long materialId : materialIds) {
            result.add(assemble(materialId, query.getSupplierId(), materials));
        }
        return result;
    }

    private PriceStatVO assemble(Long materialId, Long supplierId, Map<Long, Material> materials) {
        Material material = materials.get(materialId);
        PriceStatVO vo = new PriceStatVO();
        vo.setMaterialId(materialId);
        vo.setMaterialNo(material == null ? null : material.getMaterialNo());
        vo.setMaterialName(material == null ? null : material.getMaterialName());
        List<PriceStatSnapshot> snapshots = snapshotMapper.selectByMaterialSupplier(materialId,
                supplierId == null ? 0L : supplierId);
        for (PriceStatSnapshot snapshot : snapshots) {
            if (Objects.equals(snapshot.getStatType(), STAT_AVG)) {
                vo.setAvgPrice(snapshot.getStatValue());
                vo.setAvgSampleCount(snapshot.getSampleCount());
                vo.setStatWindow(snapshot.getStatWindow());
                vo.setCalcTime(snapshot.getCalcTime());
            } else if (Objects.equals(snapshot.getStatType(), STAT_LAST)) {
                vo.setLastPrice(snapshot.getStatValue());
                vo.setLastBillNo(snapshot.getSourceBillNo());
                vo.setLastSampleCount(snapshot.getSampleCount());
            }
        }
        // 当前生效参考价（与历史成交严格分离展示，R6）
        PriceReference ref = priceReferenceMapper.selectEffective(materialId, LocalDate.now());
        if (ref != null) {
            vo.setRefPrice(ref.getRefPrice());
            vo.setRefEffectiveDate(ref.getEffectiveDate().atStartOfDay());
            if (vo.getAvgPrice() != null && ref.getRefPrice().signum() > 0) {
                vo.setAvgDeviationRate(vo.getAvgPrice().subtract(ref.getRefPrice())
                        .divide(ref.getRefPrice(), 4, RoundingMode.HALF_UP));
            }
        }
        return vo;
    }

    /**
     * 区间成交汇总（M9-4-5）：按用户指定的起止日期实时汇总，供智能查询回答
     * 「近半年这个物料多少钱」这类问句。
     *
     * 成交定义与快照完全一致（lifecycle 3/4 且 audit_status=2 的订单行），
     * 差别只在统计范围由调用方给定；均价金额加权；无样本返回空值不伪造。
     */
    public PriceHistoryVO rangeStat(Long materialId, Long supplierId, LocalDate from, LocalDate to) {
        if (materialId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请指定物料后再查询成交价");
        }
        PriceHistoryVO vo = new PriceHistoryVO();
        vo.setMaterialId(materialId);
        vo.setSupplierId(supplierId);
        vo.setDateFrom(from == null ? null : from.toString());
        vo.setDateTo(to == null ? null : to.toString());
        Material material = materialMapper.selectById(materialId);
        if (material != null) {
            vo.setMaterialNo(material.getMaterialNo());
            vo.setMaterialName(material.getMaterialName());
        }
        if (supplierId != null) {
            Supplier supplier = supplierMapper.selectById(supplierId);
            vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());
        }

        List<PurchaseOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .in(PurchaseOrder::getLifecycleStatus, List.of(3, 4))
                .eq(PurchaseOrder::getAuditStatus, 2)
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .ge(from != null, PurchaseOrder::getOrderDate, from)
                .le(to != null, PurchaseOrder::getOrderDate, to)
                .orderByDesc(PurchaseOrder::getOrderDate)
                .orderByDesc(PurchaseOrder::getId));
        if (orders.isEmpty()) {
            vo.setSampleCount(0);
            return vo;
        }
        Map<Long, PurchaseOrder> orderById = new LinkedHashMap<>();
        for (PurchaseOrder order : orders) {
            orderById.put(order.getId(), order);
        }
        Map<Long, String> supplierNames = new HashMap<>();
        if (supplierId == null) {
            for (PurchaseOrder order : orders) {
                supplierNames.computeIfAbsent(order.getSupplierId(), id -> {
                    Supplier supplier = supplierMapper.selectById(id);
                    return supplier == null ? "供应商 " + id : supplier.getSupplierName();
                });
            }
        }
        List<PurchaseOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .in(PurchaseOrderItem::getOrderId, orderById.keySet())
                .eq(PurchaseOrderItem::getMaterialId, materialId));

        BigDecimal sumAmount = BigDecimal.ZERO;
        BigDecimal sumQty = BigDecimal.ZERO;
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        int dealCount = 0;
        for (PurchaseOrderItem item : items) {
            PurchaseOrder order = orderById.get(item.getOrderId());
            if (order == null) {
                continue;
            }
            BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
            BigDecimal qty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
            sumAmount = sumAmount.add(amount);
            sumQty = sumQty.add(qty);
            if (item.getUnitPrice() != null) {
                minPrice = minPrice == null ? item.getUnitPrice() : minPrice.min(item.getUnitPrice());
                maxPrice = maxPrice == null ? item.getUnitPrice() : maxPrice.max(item.getUnitPrice());
            }
            if (dealCount < 10) {
                PriceHistoryVO.DealRow row = new PriceHistoryVO.DealRow();
                row.setOrderNo(order.getOrderNo());
                row.setOrderDate(order.getOrderDate() == null ? null : order.getOrderDate().toString());
                row.setSupplierName(supplierId != null
                        ? vo.getSupplierName()
                        : supplierNames.getOrDefault(order.getSupplierId(), "供应商 " + order.getSupplierId()));
                row.setUnitPrice(item.getUnitPrice());
                row.setQuantity(item.getQuantity());
                row.setAmount(item.getAmount());
                vo.getDeals().add(row);
                dealCount++;
            }
        }
        vo.setSampleCount(items.size());
        vo.setTotalAmount(sumAmount.setScale(2, RoundingMode.HALF_UP));
        vo.setTotalQty(sumQty);
        vo.setMinUnitPrice(minPrice);
        vo.setMaxUnitPrice(maxPrice);
        if (sumQty.signum() != 0) {
            vo.setAvgPrice(sumAmount.divide(sumQty, 4, RoundingMode.HALF_UP));
        }
        return vo;
    }

    /**
     * 刷新统计快照（运营动作，挂 m6:param:config）。
     * 入参 materialId 可空（空 = 全部有成交的物料）。返回更新的物料数。
     */
    @Transactional
    public int refresh(Long materialId) {
        Long operatorId = AuthContext.getOrThrow().getUserId();
        String windowType = readParam(PARAM_WINDOW_TYPE, "LAST_N_DEALS");
        int windowValue = Integer.parseInt(readParam(PARAM_WINDOW_VALUE, "5"));
        String statWindow = buildWindowDesc(windowType, windowValue);

        List<PurchaseOrder> dealOrders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .in(PurchaseOrder::getLifecycleStatus, List.of(3, 4))
                .eq(PurchaseOrder::getAuditStatus, 2));
        if (dealOrders.isEmpty()) {
            operationLogService.record("M6", "refresh", "price_stat_snapshot", null, "刷新成交统计",
                    "无已审核订单，跳过");
            return 0;
        }
        Map<Long, PurchaseOrder> orderById = new HashMap<>();
        for (PurchaseOrder order : dealOrders) {
            orderById.put(order.getId(), order);
        }
        List<PurchaseOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .in(PurchaseOrderItem::getOrderId, orderById.keySet())
                .eq(materialId != null, PurchaseOrderItem::getMaterialId, materialId));
        // 按物料分组，组内按订单时间倒序（新单在前）
        Map<Long, List<PurchaseOrderItem>> byMaterial = new LinkedHashMap<>();
        for (PurchaseOrderItem item : items) {
            byMaterial.computeIfAbsent(item.getMaterialId(), k -> new ArrayList<>()).add(item);
        }
        for (List<PurchaseOrderItem> group : byMaterial.values()) {
            group.sort(Comparator.comparing(
                    (PurchaseOrderItem i) -> orderById.get(i.getOrderId()).getCreatedAt()).reversed());
        }

        int touchedMaterials = 0;
        for (Map.Entry<Long, List<PurchaseOrderItem>> entry : byMaterial.entrySet()) {
            List<PurchaseOrderItem> window = applyWindow(entry.getValue(), orderById, windowType, windowValue);
            if (window.isEmpty()) {
                continue; // 无样本不伪造
            }
            BigDecimal sumAmount = BigDecimal.ZERO;
            BigDecimal sumQty = BigDecimal.ZERO;
            for (PurchaseOrderItem item : window) {
                sumAmount = sumAmount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
                sumQty = sumQty.add(item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity());
            }
            BigDecimal avg = sumQty.signum() == 0 ? null
                    : sumAmount.divide(sumQty, 4, RoundingMode.HALF_UP);
            PurchaseOrderItem latest = window.get(0);
            String latestBillNo = orderById.get(latest.getOrderId()).getOrderNo();
            Long mid = entry.getKey();
            if (avg != null) {
                snapshotMapper.upsert(mid, 0L, STAT_AVG, avg, statWindow, window.size(), null, operatorId);
            }
            snapshotMapper.upsert(mid, 0L, STAT_LAST, latest.getUnitPrice(), statWindow,
                    window.size(), latestBillNo, operatorId);
            touchedMaterials++;
        }
        operationLogService.record("M6", "refresh", "price_stat_snapshot", null, "刷新成交统计",
                "窗口 " + statWindow + "，更新 " + touchedMaterials + " 个物料"
                        + (materialId != null ? "（限定物料 " + materialId + "）" : ""));
        return touchedMaterials;
    }

    private List<PurchaseOrderItem> applyWindow(List<PurchaseOrderItem> group,
                                                Map<Long, PurchaseOrder> orderById,
                                                String windowType, int windowValue) {
        if ("LAST_N_DAYS".equalsIgnoreCase(windowType)) {
            LocalDateTime since = LocalDate.now().minusDays(Math.max(1, windowValue)).atStartOfDay();
            List<PurchaseOrderItem> result = new ArrayList<>();
            for (PurchaseOrderItem item : group) {
                if (!orderById.get(item.getOrderId()).getCreatedAt().isBefore(since)) {
                    result.add(item);
                }
            }
            return result;
        }
        // LAST_N_DEALS：最近 n 笔（已按订单时间倒序）
        return group.size() <= windowValue ? group : new ArrayList<>(group.subList(0, windowValue));
    }

    private String buildWindowDesc(String windowType, int windowValue) {
        if ("LAST_N_DAYS".equalsIgnoreCase(windowType)) {
            return "LAST_" + windowValue + "_DAYS";
        }
        return "LAST_" + windowValue + "_DEALS";
    }

    private String readParam(String code, String fallback) {
        SysParam param = sysParamMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamCode, code)
                .eq(SysParam::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (param == null || param.getParamValue() == null || param.getParamValue().isBlank()) {
            return fallback;
        }
        return param.getParamValue().trim();
    }
}
