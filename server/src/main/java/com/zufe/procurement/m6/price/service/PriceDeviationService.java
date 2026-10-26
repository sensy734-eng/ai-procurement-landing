package com.zufe.procurement.m6.price.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
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
import com.zufe.procurement.m6.price.dto.DeviationQuery;
import com.zufe.procurement.m6.price.dto.DeviationRecalcRequest;
import com.zufe.procurement.m6.price.entity.PriceReference;
import com.zufe.procurement.m6.price.mapper.PriceReferenceMapper;
import com.zufe.procurement.m6.price.vo.DeviationOrderVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 价格偏离计算与关注（M3-6）。
 *
 * 口径（与演示种子对齐）：
 * - 偏离率 =（单价 − 参考价）/ 参考价，4 位小数；等级按**绝对值**判定：
 *   |rate| >= 显著阈值 → 2 显著；>= 关注阈值 → 1 关注；否则 0 正常；
 * - 阈值由 sys_param 的 deviation_threshold_focus / deviation_threshold_significant 固化；
 * - 参考价选价基准日 = **订单创建日**（下单决策时点），整单统一、可复现；
 * - 保存订单行时按当时生效参考价计算并落库（快照判定），参考价事后变更
 *   不自动重算，由 recalc 端点主动同步（留操作日志）；
 * - 无生效参考价 → rate=null、level=0（不伪造判定）。
 */
@Service
public class PriceDeviationService {

    private static final String PARAM_FOCUS = "deviation_threshold_focus";
    private static final String PARAM_SIGNIFICANT = "deviation_threshold_significant";

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PriceReferenceMapper priceReferenceMapper;
    private final SysParamMapper sysParamMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final OperationLogService operationLogService;

    public PriceDeviationService(PurchaseOrderMapper orderMapper,
                                 PurchaseOrderItemMapper orderItemMapper,
                                 PriceReferenceMapper priceReferenceMapper,
                                 SysParamMapper sysParamMapper,
                                 MaterialMapper materialMapper,
                                 SupplierMapper supplierMapper,
                                 OperationLogService operationLogService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.priceReferenceMapper = priceReferenceMapper;
        this.sysParamMapper = sysParamMapper;
        this.materialMapper = materialMapper;
        this.supplierMapper = supplierMapper;
        this.operationLogService = operationLogService;
    }

    // ================= 计算原语 =================

    /** 偏离率 =（单价 − 参考价）/ 参考价，4 位；参考价 <= 0 返回 null */
    public BigDecimal calcRate(BigDecimal unitPrice, BigDecimal refPrice) {
        if (unitPrice == null || refPrice == null || refPrice.signum() <= 0) {
            return null;
        }
        return unitPrice.subtract(refPrice)
                .divide(refPrice, 4, RoundingMode.HALF_UP);
    }

    /** 等级：0 正常 / 1 关注 / 2 显著（按绝对值，低于参考价过多同样计入偏离） */
    public int levelOf(BigDecimal rate) {
        if (rate == null) {
            return 0;
        }
        BigDecimal abs = rate.abs();
        if (abs.compareTo(significantThreshold()) >= 0) {
            return 2;
        }
        if (abs.compareTo(focusThreshold()) >= 0) {
            return 1;
        }
        return 0;
    }

    public BigDecimal focusThreshold() {
        return readThreshold(PARAM_FOCUS, "0.05");
    }

    public BigDecimal significantThreshold() {
        return readThreshold(PARAM_SIGNIFICANT, "0.15");
    }

    private BigDecimal readThreshold(String code, String fallback) {
        try {
            com.zufe.procurement.m11.param.entity.SysParam param = sysParamMapper.selectOne(
                    new LambdaQueryWrapper<com.zufe.procurement.m11.param.entity.SysParam>()
                            .eq(com.zufe.procurement.m11.param.entity.SysParam::getParamCode, code)
                            .eq(com.zufe.procurement.m11.param.entity.SysParam::getIsDeleted, 0)
                            .last("LIMIT 1"));
            if (param != null && param.getParamValue() != null) {
                return new BigDecimal(param.getParamValue().trim());
            }
        } catch (Exception ignored) {
            // 参数缺失或损坏时回落默认阈值，不影响下单主链路
        }
        return new BigDecimal(fallback);
    }

    /** 选价：基准日当天生效的参考价（同物料生效日不超过基准日的最新一版） */
    public PriceReference refPriceFor(Long materialId, LocalDate onDate) {
        if (materialId == null || onDate == null) {
            return null;
        }
        return priceReferenceMapper.selectEffective(materialId, onDate);
    }

    /**
     * 给订单行计算并写偏离（不落库，由调用方 update/insert）。
     * 基准日 = 订单创建日；无生效参考价 → rate=null、level=0。
     */
    public void applyToItem(PurchaseOrderItem item, PurchaseOrder bill) {
        LocalDate baseDate = bill.getCreatedAt() == null
                ? LocalDate.now() : bill.getCreatedAt().toLocalDate();
        applyToItem(item, bill, baseDate);
    }

    /**
     * 按指定基准日计算偏离。重算场景传今天（当前生效参考价重判历史行），
     * 建单/编辑行场景传订单创建日（下单时点判定）。
     */
    public void applyToItem(PurchaseOrderItem item, PurchaseOrder bill, LocalDate baseDate) {
        PriceReference ref = refPriceFor(item.getMaterialId(), baseDate);
        if (ref == null) {
            item.setDeviationRate(null);
            item.setDeviationLevel(0);
            return;
        }
        BigDecimal rate = calcRate(item.getUnitPrice(), ref.getRefPrice());
        item.setDeviationRate(rate);
        item.setDeviationLevel(levelOf(rate));
    }

    // ================= 整单标记重算 =================

    /** 整单偏离标记 = 明细行最高等级（无任何行时为 0） */
    public void recomputeOrderFlag(Long orderId) {
        PurchaseOrder bill = orderMapper.selectById(orderId);
        if (bill == null) {
            return;
        }
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderId(orderId);
        int max = 0;
        for (PurchaseOrderItem item : items) {
            if (item.getDeviationLevel() != null && item.getDeviationLevel() > max) {
                max = item.getDeviationLevel();
            }
        }
        if (!Objects.equals(bill.getDeviationFlag(), max)) {
            PurchaseOrder update = new PurchaseOrder();
            update.setId(orderId);
            update.setDeviationFlag(max);
            update.setVersion(bill.getVersion());
            orderMapper.updateById(update);
        }
    }

    // ================= 重算（参考价变更后同步） =================

    /**
     * 重算偏离：materialId / orderId 都可空（空 = 全量）。
     * 只重写 rate/level 与整单 flag，不动价格与数量，留操作日志。
     */
    public int recalc(DeviationRecalcRequest request) {
        List<PurchaseOrderItem> items = loadRecalcScope(request);
        int touched = 0;
        Set<Long> orderIds = new LinkedHashSet<>();
        for (PurchaseOrderItem item : items) {
            PurchaseOrder bill = orderMapper.selectById(item.getOrderId());
            if (bill == null) {
                continue;
            }
            BigDecimal oldRate = item.getDeviationRate();
            int oldLevel = item.getDeviationLevel() == null ? 0 : item.getDeviationLevel();
            // 重算基准日 = 今天：用当前生效参考价重判历史行（与建单时点的判定分开）
            applyToItem(item, bill, LocalDate.now());
            if (!Objects.equals(oldRate, item.getDeviationRate()) || oldLevel != item.getDeviationLevel()) {
                // 显式 set：MP 的 updateById 对 null 字段不生成 SET，无法把 rate 清回 null
                int rows = orderItemMapper.update(null, new LambdaUpdateWrapper<PurchaseOrderItem>()
                        .eq(PurchaseOrderItem::getId, item.getId())
                        .set(PurchaseOrderItem::getDeviationRate, item.getDeviationRate())
                        .set(PurchaseOrderItem::getDeviationLevel, item.getDeviationLevel()));
                if (rows > 0) {
                    touched++;
                }
            }
            orderIds.add(item.getOrderId());
        }
        for (Long orderId : orderIds) {
            recomputeOrderFlag(orderId);
        }
        operationLogService.record("M6", "recalc", "purchase_order_item", null, "价格偏离重算",
                "范围：" + scopeDesc(request) + "，更新 " + touched + " 行，涉及 " + orderIds.size() + " 单");
        return touched;
    }

    private List<PurchaseOrderItem> loadRecalcScope(DeviationRecalcRequest request) {
        if (request == null) {
            return orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                    .isNotNull(PurchaseOrderItem::getId));
        }
        if (request.getOrderId() != null) {
            return orderItemMapper.selectByOrderId(request.getOrderId());
        }
        if (request.getMaterialId() != null) {
            return orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                    .eq(PurchaseOrderItem::getMaterialId, request.getMaterialId()));
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .isNotNull(PurchaseOrderItem::getId));
    }

    private String scopeDesc(DeviationRecalcRequest request) {
        if (request == null) {
            return "全量";
        }
        if (request.getOrderId() != null) {
            return "订单 " + request.getOrderId();
        }
        if (request.getMaterialId() != null) {
            return "物料 " + request.getMaterialId();
        }
        return "全量";
    }

    // ================= 关注清单（M3-6） =================

    /** 分页查询有偏离的订单（deviation_flag > 0），带明细行摘要 */
    public DeviationPage pageDeviations(DeviationQuery query) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .gt(PurchaseOrder::getDeviationFlag, 0)
                .eq(query.getDeviationLevel() != null,
                        PurchaseOrder::getDeviationFlag, query.getDeviationLevel())
                .like(org.springframework.util.StringUtils.hasText(query.getOrderNo()),
                        PurchaseOrder::getOrderNo, query.getOrderNo())
                .orderByDesc(PurchaseOrder::getDeviationFlag)
                .orderByDesc(PurchaseOrder::getId);
        List<PurchaseOrder> orders = orderMapper.selectList(wrapper);
        if (query.getMaterialId() != null) {
            // 按物料过滤：先找该物料有偏离的订单，再交订单层过滤
            List<PurchaseOrderItem> itemRows = orderItemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseOrderItem>()
                            .eq(PurchaseOrderItem::getMaterialId, query.getMaterialId())
                            .gt(PurchaseOrderItem::getDeviationLevel, 0));
            final List<Long> materialFiltered = itemRows.stream()
                    .map(PurchaseOrderItem::getOrderId).distinct().toList();
            if (materialFiltered.isEmpty()) {
                return new DeviationPage(0, Collections.emptyList());
            }
            orders = orders.stream()
                    .filter(o -> materialFiltered.contains(o.getId())).toList();
        }
        long total = orders.size();
        int from = Math.max(0, (query.getPageNo() - 1) * query.getPageSize());
        int to = Math.min(orders.size(), from + query.getPageSize());
        List<PurchaseOrder> page = from >= orders.size() ? Collections.emptyList()
                : orders.subList(from, to);

        List<Long> orderIds = page.stream().map(PurchaseOrder::getId).toList();
        List<PurchaseOrderItem> items = orderIds.isEmpty()
                ? Collections.emptyList() : orderItemMapper.selectByOrderIds(orderIds);
        Map<Long, List<PurchaseOrderItem>> byOrder = new HashMap<>();
        for (PurchaseOrderItem item : items) {
            byOrder.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }
        Map<Long, Material> materials = new HashMap<>();
        for (Material material : materialMapper.selectList(
                new LambdaQueryWrapper<Material>().isNotNull(Material::getId))) {
            materials.put(material.getId(), material);
        }
        Map<Long, String> supplierNames = new HashMap<>();
        for (Supplier supplier : supplierMapper.selectList(
                new LambdaQueryWrapper<Supplier>().isNotNull(Supplier::getId))) {
            supplierNames.put(supplier.getId(), supplier.getSupplierName());
        }

        List<DeviationOrderVO> records = new ArrayList<>();
        for (PurchaseOrder order : page) {
            DeviationOrderVO vo = new DeviationOrderVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setSupplierName(supplierNames.get(order.getSupplierId()));
            vo.setDeviationFlag(order.getDeviationFlag());
            vo.setDeviationFlagName(flagName(order.getDeviationFlag()));
            vo.setTotalAmount(order.getTotalAmount());
            vo.setCreatedAt(order.getCreatedAt());
            List<DeviationOrderVO.DeviationItemVO> rows = new ArrayList<>();
            BigDecimal maxRate = null;
            for (PurchaseOrderItem item : byOrder.getOrDefault(order.getId(), List.of())) {
                DeviationOrderVO.DeviationItemVO row = new DeviationOrderVO.DeviationItemVO();
                row.setItemId(item.getId());
                row.setMaterialId(item.getMaterialId());
                Material material = materials.get(item.getMaterialId());
                row.setMaterialNo(material == null ? null : material.getMaterialNo());
                row.setMaterialName(material == null ? null : material.getMaterialName());
                row.setUnitPrice(item.getUnitPrice());
                row.setDeviationRate(item.getDeviationRate());
                row.setDeviationLevel(item.getDeviationLevel());
                rows.add(row);
                if (item.getDeviationRate() != null
                        && (maxRate == null || item.getDeviationRate().abs().compareTo(maxRate.abs()) > 0)) {
                    maxRate = item.getDeviationRate();
                }
            }
            vo.setItems(rows);
            vo.setMaxDeviationRate(maxRate);
            records.add(vo);
        }
        return new DeviationPage(total, records);
    }

    public String flagName(Integer flag) {
        if (flag == null || flag == 0) {
            return "正常";
        }
        return flag == 2 ? "显著" : "关注";
    }

    public String levelName(Integer level) {
        if (level == null || level == 0) {
            return "正常";
        }
        return level == 2 ? "显著" : "关注";
    }

    /** 汇总：关注/显著单数、阈值现状（P16 统计卡） */
    public Map<String, Object> summary() {
        List<PurchaseOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .gt(PurchaseOrder::getDeviationFlag, 0));
        long focus = orders.stream().filter(o -> Objects.equals(o.getDeviationFlag(), 1)).count();
        long significant = orders.stream().filter(o -> Objects.equals(o.getDeviationFlag(), 2)).count();
        List<PurchaseOrderItem> levelRows = orderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().gt(PurchaseOrderItem::getDeviationLevel, 0));
        long itemFocus = levelRows.stream().filter(i -> Objects.equals(i.getDeviationLevel(), 1)).count();
        long itemSignificant = levelRows.stream().filter(i -> Objects.equals(i.getDeviationLevel(), 2)).count();
        Map<String, Object> result = new HashMap<>();
        result.put("focusOrders", focus);
        result.put("significantOrders", significant);
        result.put("totalOrders", focus + significant);
        result.put("focusItems", itemFocus);
        result.put("significantItems", itemSignificant);
        result.put("focusThreshold", focusThreshold());
        result.put("significantThreshold", significantThreshold());
        return result;
    }

    public static class DeviationPage {
        private final long total;
        private final List<DeviationOrderVO> records;

        public DeviationPage(long total, List<DeviationOrderVO> records) {
            this.total = total;
            this.records = records;
        }

        public long getTotal() {
            return total;
        }

        public List<DeviationOrderVO> getRecords() {
            return records;
        }
    }
}
