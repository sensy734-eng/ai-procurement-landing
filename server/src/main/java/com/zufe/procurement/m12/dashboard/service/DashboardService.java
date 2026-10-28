package com.zufe.procurement.m12.dashboard.service;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.CsvWriter;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m12.dashboard.dto.ChartQuery;
import com.zufe.procurement.m12.dashboard.dto.SummaryQuery;
import com.zufe.procurement.m12.dashboard.mapper.DashboardMapper;
import com.zufe.procurement.m12.dashboard.vo.OverviewVO;
import com.zufe.procurement.m12.dashboard.vo.PurchaseSummaryRow;
import com.zufe.procurement.m12.dashboard.vo.StatusChartVO;
import com.zufe.procurement.m12.dashboard.vo.StockAlertChartVO;
import com.zufe.procurement.m12.dashboard.vo.StockSummaryRow;
import com.zufe.procurement.m12.dashboard.vo.SupplierRankVO;
import com.zufe.procurement.m12.dashboard.vo.TrendChartVO;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台与数据可视化（M12）。
 *
 * 三条口径纪律，全部来自前几轮已固化的业务定义，这里只做归纳不做新发明：
 * 1. 数据范围：订单类按部门、库存类按仓库，与页面列表查询同源（M12-3-6）。
 *    部门范围为空时失败关闭（IN (-1) 返回空），与 M3 列表一致；
 *    仓库范围为空时不过滤，与 M5 库存台账一致——两处语义本来就不同，图表跟随各自页面。
 * 2. 已成交口径：lifecycle 3/4 且 audit_status=2，与 M6 历史成交、M3 交付汇总一致；
 *    草稿与审批中不进金额类图表，否则趋势图会被在途单据抬高。
 * 3. 可用量与库存不足：可用量 = 数量 − 冻结 − 预留现算，不足判定与 M5-5-1 完全一致，
 *    缺口 = 下限 − 可用量，下限为 0 的物料不参与。
 */
@Service
public class DashboardService {

    /** 全量之外的「无可见范围」哨兵：让 SQL 走 IN (-1)，不返回任何行 */
    private static final List<Long> EMPTY_SCOPE = List.of(-1L);

    private static final int DEFAULT_TOP_N = 10;

    private static final int MAX_TOP_N = 50;

    private static final int TREND_MONTHS = 12;

    /** 状态桶顺序与中文名，前端按此顺序渲染 */
    private static final Map<String, String> BUCKET_NAMES = new LinkedHashMap<>();

    /** 不计入分桶的桶（草稿/作废/其他），单独呈现以便对账 */
    private static final Map<String, String> EXCLUDED_NAMES = new LinkedHashMap<>();

    static {
        BUCKET_NAMES.put("PENDING_APPROVAL", "待审批");
        BUCKET_NAMES.put("PENDING_DELIVERY", "待交付");
        BUCKET_NAMES.put("PARTIAL", "部分交付");
        BUCKET_NAMES.put("DONE", "已完成");
        BUCKET_NAMES.put("CLOSED", "已关闭");
        EXCLUDED_NAMES.put("DRAFT", "草稿");
        EXCLUDED_NAMES.put("VOIDED", "已作废");
        EXCLUDED_NAMES.put("OTHER", "其他");
    }

    private final DashboardMapper dashboardMapper;

    private final DictItemMapper dictItemMapper;

    private final WarehouseMapper warehouseMapper;

    public DashboardService(DashboardMapper dashboardMapper,
                            DictItemMapper dictItemMapper,
                            WarehouseMapper warehouseMapper) {
        this.dashboardMapper = dashboardMapper;
        this.dictItemMapper = dictItemMapper;
        this.warehouseMapper = warehouseMapper;
    }

    // ================= M12-1 后台首页 =================

    /** 后台首页：五类维护入口按权限过滤 + 四个指标卡 */
    public OverviewVO overview() {
        CurrentUser user = AuthContext.getOrThrow();
        OverviewVO vo = new OverviewVO();
        vo.setRealName(user.getRealName());
        vo.setRoleNames(new ArrayList<>(user.getRoleNames()));
        vo.setScopeHint(scopeHint(user.getScope()));
        vo.setGroups(buildGroups(user));
        vo.setMetrics(buildMetrics(user));
        return vo;
    }

    private List<OverviewVO.EntryGroup> buildGroups(CurrentUser user) {
        List<OverviewVO.EntryGroup> groups = new ArrayList<>();

        OverviewVO.EntryGroup base = group("base", "基础数据", "Files");
        addEntry(base, user, "物料档案", "/master/material", "m1:material:list",
                "个物料", dashboardMapper.countMaterials(), "编码唯一、停用后不可选");
        addEntry(base, user, "供应商档案", "/master/supplier", "m1:supplier:list",
                "家供应商", dashboardMapper.countSuppliers(), "含联系人、附件与供货范围");
        addEntry(base, user, "仓库与库位", "/master/warehouse", "m1:warehouse:list",
                null, null, "仓库维度是库存与数据范围的边界");
        addEntry(base, user, "物料分类", "/system/category", "m1:material_category:list",
                "个分类", dashboardMapper.countCategories(), "两级树，物料挂一级分类");
        groups.add(base);

        OverviewVO.EntryGroup dict = group("dict", "业务字典", "Collection");
        addEntry(dict, user, "业务字典", "/master/dict", "m1:dict:list",
                "个字典项", dashboardMapper.countDictItems(), "部门树同为字典（type=dept）");
        groups.add(dict);

        OverviewVO.EntryGroup platform = group("platform", "用户与权限", "User");
        addEntry(platform, user, "用户管理", "/system/users", "m11:user:list",
                "个用户", dashboardMapper.countUsers(), "停用即失效，会话同步下线");
        addEntry(platform, user, "角色权限", "/system/roles", "m11:role:list",
                "个角色", dashboardMapper.countRoles(), "内置角色不可删");
        addEntry(platform, user, "审批配置", "/system/approval", "m11:approval_node:list",
                null, null, "或签与会签按单据类型配置");
        addEntry(platform, user, "参数配置", "/system/params", "m11:param:list",
                null, null, "阈值与窗口口径统一由参数驱动");
        groups.add(platform);

        OverviewVO.EntryGroup kb = group("kb", "知识库", "Reading");
        addEntry(kb, user, "知识文档", "/system/kb", "m10:document:list",
                "篇文档", dashboardMapper.countKbDocuments(), "解析、切块、索引与检索测试");
        groups.add(kb);

        OverviewVO.EntryGroup audit = group("audit", "审计", "Document");
        addEntry(audit, user, "操作日志", "/system/logs", "m11:log:list",
                "条日志", dashboardMapper.countOperationLogs(), "业务动作留痕，只增不改");
        addEntry(audit, user, "查询审计", "/assistant/audit", "m9:audit:list",
                "条查询", dashboardMapper.countQueryAudits(), "智能查询的意图、参数与结果码");
        groups.add(audit);

        groups.removeIf(g -> g.getEntries().isEmpty());
        return groups;
    }

    private OverviewVO.EntryGroup group(String key, String title, String icon) {
        OverviewVO.EntryGroup group = new OverviewVO.EntryGroup();
        group.setKey(key);
        group.setTitle(title);
        group.setIcon(icon);
        return group;
    }

    private void addEntry(OverviewVO.EntryGroup group, CurrentUser user, String title, String path,
                          String perm, String countLabel, Long count, String description) {
        if (!user.hasPermission(perm)) {
            return;
        }
        OverviewVO.Entry entry = new OverviewVO.Entry();
        entry.setTitle(title);
        entry.setPath(path);
        entry.setPerm(perm);
        entry.setCountLabel(countLabel);
        entry.setCount(count);
        entry.setDescription(description);
        group.getEntries().add(entry);
    }

    /**
     * 指标卡按权限给：待办是自己的，人人可见；采购金额与在途订单属于订单口径，
     * 库存不足属于库存口径——没有对应权限就算出来也是 0，摆上去只会让人误会「系统没数」，
     * 因此直接不返回该卡，页面上少一张卡好过给一个假的 0。
     */
    private List<OverviewVO.Metric> buildMetrics(CurrentUser user) {
        List<OverviewVO.Metric> metrics = new ArrayList<>();
        long pending = dashboardMapper.countPendingTasks(user.getUserId(), List.of(1, 2));
        metrics.add(metric("todo", "我的待办", String.valueOf(pending), "条",
                "申请与订单的待处理审批任务"));

        if (user.hasPermission("m12:summary:purchase")) {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            BigDecimal monthAmount = dashboardMapper.tradedAmountBetween(deptScope(), monthStart, today);
            metrics.add(metric("monthAmount", "本月采购金额",
                    money(monthAmount).toPlainString(), "元", "已审核及之后订单，不含草稿与在途审批"));
        }

        if (user.hasPermission("m12:chart:stockalert")) {
            StockAlertChartVO alert = stockAlertChart(new ChartQuery());
            metrics.add(metric("stockAlert", "库存不足物料",
                    String.valueOf(alert.getMaterialCount()), "个",
                    "可用量低于预警下限，缺口合计 " + plain(alert.getTotalGap())));
        }

        if (user.hasPermission("m12:chart:status")) {
            StatusChartVO status = statusChart(new ChartQuery());
            int pendingDelivery = status.getBuckets().stream()
                    .filter(b -> "PENDING_DELIVERY".equals(b.getCode()) || "PARTIAL".equals(b.getCode()))
                    .mapToInt(StatusChartVO.Bucket::getCount).sum();
            metrics.add(metric("pendingDelivery", "在途交付订单", String.valueOf(pendingDelivery), "单",
                    "待交付与部分交付之和"));
        }
        return metrics;
    }

    private OverviewVO.Metric metric(String key, String label, String value, String unit, String hint) {
        OverviewVO.Metric metric = new OverviewVO.Metric();
        metric.setKey(key);
        metric.setLabel(label);
        metric.setValue(value);
        metric.setUnit(unit);
        metric.setHint(hint);
        return metric;
    }

    private String scopeHint(ScopeCondition scope) {
        if (scope == null || scope.isAll()) {
            return "全部部门与仓库";
        }
        List<String> parts = new ArrayList<>();
        if (scope.hasDept()) {
            Map<Long, String> deptById = new LinkedHashMap<>();
            for (DictItem item : dictItemMapper.selectByTypeCode("dept")) {
                deptById.put(item.getId(), item.getItemName());
            }
            List<String> names = new ArrayList<>();
            for (Long id : scope.getDeptIds()) {
                names.add(deptById.getOrDefault(id, "部门 " + id));
            }
            parts.add("部门 " + String.join("、", names));
        }
        if (scope.hasWarehouse()) {
            List<String> names = new ArrayList<>();
            for (Warehouse warehouse : warehouseMapper.selectBatchIds(scope.getWarehouseIds())) {
                names.add(warehouse.getWarehouseName());
            }
            parts.add("仓库 " + String.join("、", names));
        }
        if (scope.hasOwner()) {
            parts.add("归属 " + String.join("、", scope.getOwnerNos()));
        }
        return parts.isEmpty() ? "未配置数据范围" : String.join("；", parts);
    }

    // ================= M12-3-1 订单状态分布 =================

    public StatusChartVO statusChart(ChartQuery query) {
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        List<DashboardMapper.BucketRow> rows = dashboardMapper.statusDistribution(deptScope(), start, end);

        StatusChartVO vo = new StatusChartVO();
        vo.setStartDate(start);
        vo.setEndDate(end);
        Map<String, DashboardMapper.BucketRow> byBucket = new LinkedHashMap<>();
        for (DashboardMapper.BucketRow row : rows) {
            byBucket.put(row.getBucket(), row);
        }
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (String code : BUCKET_NAMES.keySet()) {
            DashboardMapper.BucketRow row = byBucket.get(code);
            int count = row == null || row.getOrderCount() == null ? 0 : row.getOrderCount();
            BigDecimal amount = row == null ? BigDecimal.ZERO : money(row.getAmount());
            totalCount += count;
            totalAmount = totalAmount.add(amount);
            vo.getBuckets().add(bucket(code, BUCKET_NAMES.get(code), count, amount, null));
        }
        vo.setOrderCount(totalCount);
        vo.setAmount(totalAmount);
        // 占比在总数确定后再回填，避免边累加边算
        for (StatusChartVO.Bucket bucket : vo.getBuckets()) {
            bucket.setRatio(ratio(bucket.getCount(), totalCount));
        }
        for (String code : EXCLUDED_NAMES.keySet()) {
            DashboardMapper.BucketRow row = byBucket.get(code);
            if (row == null) {
                continue;
            }
            vo.getExcluded().add(bucket(code, EXCLUDED_NAMES.get(code),
                    row.getOrderCount() == null ? 0 : row.getOrderCount(),
                    money(row.getAmount()), null));
        }
        return vo;
    }

    private StatusChartVO.Bucket bucket(String code, String name, int count, BigDecimal amount,
                                        BigDecimal ratio) {
        StatusChartVO.Bucket bucket = new StatusChartVO.Bucket();
        bucket.setCode(code);
        bucket.setName(name);
        bucket.setCount(count);
        bucket.setAmount(amount);
        bucket.setRatio(ratio);
        return bucket;
    }

    // ================= M12-3-2 月度趋势 =================

    public TrendChartVO trendChart(ChartQuery query) {
        LocalDate end = query.getEndDate() == null ? LocalDate.now() : query.getEndDate();
        LocalDate start = query.getStartDate() == null
                ? end.withDayOfMonth(1).minusMonths(TREND_MONTHS - 1)
                : query.getStartDate();

        Map<String, DashboardMapper.MonthRow> byMonth = new LinkedHashMap<>();
        for (DashboardMapper.MonthRow row : dashboardMapper.monthlyTrend(deptScope(), start, end)) {
            byMonth.put(row.getMonth(), row);
        }

        TrendChartVO vo = new TrendChartVO();
        vo.setStartDate(start);
        vo.setEndDate(end);
        BigDecimal max = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        int totalOrders = 0;
        // 月份必须连续：缺月补 0，折线才不会把两个不相邻的月直接连起来
        LocalDate cursor = start.withDayOfMonth(1);
        LocalDate last = end.withDayOfMonth(1);
        while (!cursor.isAfter(last)) {
            String month = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
            DashboardMapper.MonthRow row = byMonth.get(month);
            TrendChartVO.Point point = new TrendChartVO.Point();
            point.setMonth(month);
            point.setLabel(cursor.getMonthValue() + "月");
            point.setOrderCount(row == null || row.getOrderCount() == null ? 0 : row.getOrderCount());
            point.setAmount(row == null ? BigDecimal.ZERO : money(row.getAmount()));
            if (point.getAmount().compareTo(max) > 0) {
                max = point.getAmount();
            }
            total = total.add(point.getAmount());
            totalOrders += point.getOrderCount();
            vo.getPoints().add(point);
            cursor = cursor.plusMonths(1);
        }
        vo.setMaxAmount(max);
        vo.setTotalAmount(total);
        vo.setTotalOrders(totalOrders);
        return vo;
    }

    // ================= M12-3-3 供应商金额排名 =================

    public SupplierRankVO supplierRank(ChartQuery query) {
        int topN = query.getTopN() == null ? DEFAULT_TOP_N
                : Math.min(Math.max(query.getTopN(), 1), MAX_TOP_N);
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        List<DashboardMapper.SupplierRow> rows = dashboardMapper.supplierRank(deptScope(), start, end, topN);
        DashboardMapper.TotalRow total = dashboardMapper.tradedTotal(deptScope(), start, end);
        BigDecimal totalAmount = total == null ? BigDecimal.ZERO : money(total.getAmount());

        SupplierRankVO vo = new SupplierRankVO();
        vo.setTopN(topN);
        vo.setStartDate(start);
        vo.setEndDate(end);
        vo.setTotalAmount(totalAmount);
        vo.setSupplierCount(total == null || total.getSupplierCount() == null ? 0 : total.getSupplierCount());
        int rank = 1;
        for (DashboardMapper.SupplierRow row : rows) {
            SupplierRankVO.Row item = new SupplierRankVO.Row();
            item.setRank(rank++);
            item.setSupplierId(row.getSupplierId());
            item.setSupplierNo(row.getSupplierNo());
            item.setSupplierName(row.getSupplierName());
            item.setOrderCount(row.getOrderCount() == null ? 0 : row.getOrderCount());
            item.setAmount(money(row.getAmount()));
            item.setRatio(ratio(item.getAmount(), totalAmount));
            vo.getRows().add(item);
        }
        return vo;
    }

    // ================= M12-3-4 库存不足 =================

    public StockAlertChartVO stockAlertChart(ChartQuery query) {
        List<DashboardMapper.StockAlertRow> rows = dashboardMapper.stockAlertRows(
                warehouseScope(), query.getWarehouseId(), query.getCategoryId());
        List<StockAlertChartVO.Row> alerts = new ArrayList<>();
        BigDecimal totalGap = BigDecimal.ZERO;
        for (DashboardMapper.StockAlertRow row : rows) {
            BigDecimal safety = row.getSafetyStock() == null ? BigDecimal.ZERO : row.getSafetyStock();
            if (safety.signum() <= 0) {
                // 没有预警下限就没有「不足」，与 M5-5-1 同一判定
                continue;
            }
            BigDecimal available = nz(row.getQuantity()).subtract(nz(row.getFrozenQty()))
                    .subtract(nz(row.getReservedQty()));
            if (available.compareTo(safety) >= 0) {
                continue;
            }
            StockAlertChartVO.Row item = new StockAlertChartVO.Row();
            item.setMaterialId(row.getMaterialId());
            item.setMaterialNo(row.getMaterialNo());
            item.setMaterialName(row.getMaterialName());
            item.setSpec(row.getSpec());
            item.setCategoryName(row.getCategoryName());
            item.setQuantity(qty(row.getQuantity()));
            item.setAvailableQty(qty(available));
            item.setSafetyStock(qty(safety));
            item.setGapQty(qty(safety.subtract(available)));
            alerts.add(item);
            totalGap = totalGap.add(safety.subtract(available));
        }
        alerts.sort((a, b) -> b.getGapQty().compareTo(a.getGapQty()));

        StockAlertChartVO vo = new StockAlertChartVO();
        vo.setMaterialCount(alerts.size());
        vo.setTotalGap(qty(totalGap));
        vo.setRows(alerts);
        return vo;
    }

    // ================= M12-2-1 采购单据汇总 =================

    public List<PurchaseSummaryRow> purchaseSummary(SummaryQuery query) {
        String groupBy = query.getGroupBy() == null ? "status" : query.getGroupBy();
        List<PurchaseSummaryRow> rows = switch (groupBy) {
            case "supplier" -> dashboardMapper.summaryBySupplier(deptScope(), query.getStartDate(),
                    query.getEndDate(), query.getSupplierId(), query.getMaterialId(),
                    query.getLifecycleStatus(), query.getAuditStatus(), query.getFulfillStatus());
            case "material" -> dashboardMapper.summaryByMaterial(deptScope(), query.getStartDate(),
                    query.getEndDate(), query.getSupplierId(), query.getMaterialId(),
                    query.getLifecycleStatus(), query.getAuditStatus(), query.getFulfillStatus());
            case "month" -> dashboardMapper.summaryByMonth(deptScope(), query.getStartDate(),
                    query.getEndDate(), query.getSupplierId(), query.getMaterialId(),
                    query.getLifecycleStatus(), query.getAuditStatus(), query.getFulfillStatus());
            default -> dashboardMapper.summaryByStatus(deptScope(), query.getStartDate(),
                    query.getEndDate(), query.getSupplierId(), query.getMaterialId(),
                    query.getLifecycleStatus(), query.getAuditStatus(), query.getFulfillStatus());
        };
        for (PurchaseSummaryRow row : rows) {
            row.setAmount(money(row.getAmount()));
            row.setQty(qty(row.getQty()));
            if (row.getOrderCount() == null) {
                row.setOrderCount(0);
            }
            if ("status".equals(groupBy)) {
                row.setGroupName(statusComboName(row.getGroupKey()));
            }
            if (row.getGroupName() == null) {
                row.setGroupName(unknownName(row.getGroupKey()));
            }
            // 明细行数只有物料分组能算（按明细行聚合）；其余分组保持 null，
            // 前端显示为「—」，不拿单据数冒充明细行数
        }
        return rows;
    }

    /** 三状态组合的中文名，顺序与列表页三列一致：生命周期·审批·履约 */
    private String statusComboName(String key) {
        if (key == null) {
            return "";
        }
        String[] parts = key.split("-");
        if (parts.length != 3) {
            return key;
        }
        return lifecycleName(parseInt(parts[0])) + "·" + auditName(parseInt(parts[1]))
                + "·" + fulfillName(parseInt(parts[2]));
    }

    private String unknownName(String key) {
        return key == null ? "" : key;
    }

    private Integer parseInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String lifecycleName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "草稿";
            case 2 -> "已提交";
            case 3 -> "已审核";
            case 4 -> "已关闭";
            case 5 -> "已作废";
            default -> "";
        };
    }

    private String auditName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "未审核";
            case 2 -> "通过";
            case 3 -> "驳回";
            default -> "";
        };
    }

    private String fulfillName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "待交付";
            case 2 -> "部分交付";
            case 3 -> "全部交付";
            case 4 -> "已关闭";
            default -> "";
        };
    }

    // ================= M12-2-2 库存汇总 =================

    public List<StockSummaryRow> stockSummary(SummaryQuery query) {
        String groupBy = query.getGroupBy() == null ? "warehouse" : query.getGroupBy();
        List<StockSummaryRow> rows = switch (groupBy) {
            case "category" -> dashboardMapper.stockByCategory(warehouseScope(), query.getWarehouseId(),
                    query.getCategoryId(), query.getOwnerNo());
            case "owner" -> dashboardMapper.stockByOwner(warehouseScope(), query.getWarehouseId(),
                    query.getCategoryId(), query.getOwnerNo());
            default -> dashboardMapper.stockByWarehouse(warehouseScope(), query.getWarehouseId(),
                    query.getCategoryId(), query.getOwnerNo());
        };
        for (StockSummaryRow row : rows) {
            row.setQuantity(qty(row.getQuantity()));
            row.setFrozenQty(qty(row.getFrozenQty()));
            row.setReservedQty(qty(row.getReservedQty()));
            row.setAvailableQty(qty(nz(row.getQuantity()).subtract(nz(row.getFrozenQty()))
                    .subtract(nz(row.getReservedQty()))));
            if (row.getGroupName() == null) {
                row.setGroupName(row.getGroupKey());
            }
            if (row.getMaterialCount() == null) {
                row.setMaterialCount(0);
            }
            if (row.getRowCount() == null) {
                row.setRowCount(0);
            }
        }
        return rows;
    }

    // ================= M12-2-3 导出 =================

    /**
     * 汇总结果导出。type=purchase 走采购单据汇总，type=stock 走库存汇总。
     * 导出与页面查询共用同一套查询方法——导出的数字必须与屏幕上的一致，
     * 两条各写一遍 SQL 是这类功能最常见的对不上的原因。
     */
    public byte[] export(String type, SummaryQuery query) {
        if ("stock".equalsIgnoreCase(type)) {
            String groupBy = query.getGroupBy() == null ? "warehouse" : query.getGroupBy();
            String[] headers = {"分组", "涉及物料数", "明细行数", "数量", "冻结数量", "预留数量", "可用量"};
            List<String[]> lines = new ArrayList<>();
            for (StockSummaryRow row : stockSummary(query)) {
                lines.add(new String[]{nullToEmpty(row.getGroupName()),
                        String.valueOf(row.getMaterialCount()), String.valueOf(row.getRowCount()),
                        plain(row.getQuantity()), plain(row.getFrozenQty()),
                        plain(row.getReservedQty()), plain(row.getAvailableQty())});
            }
            return CsvWriter.toCsv(headers, lines);
        }
        String groupBy = query.getGroupBy() == null ? "status" : query.getGroupBy();
        String groupTitle = switch (groupBy) {
            case "supplier" -> "供应商";
            case "material" -> "物料";
            case "month" -> "月份";
            default -> "状态";
        };
        String[] headers = {groupTitle, "单据数", "明细行数", "数量合计", "金额合计"};
        List<String[]> lines = new ArrayList<>();
        for (PurchaseSummaryRow row : purchaseSummary(query)) {
            lines.add(new String[]{nullToEmpty(row.getGroupName()),
                    String.valueOf(row.getOrderCount()),
                    row.getItemCount() == null ? "" : String.valueOf(row.getItemCount()),
                    plain(row.getQty()), plain(row.getAmount())});
        }
        return CsvWriter.toCsv(headers, lines);
    }

    // ================= 范围与数值工具 =================

    /**
     * 部门范围：全量为 null（不过滤），有部门给集合，无任何部门范围给哨兵
     * （IN (-1) 不返回任何行）——后者与 M3 订单列表的失败关闭语义一致。
     */
    private List<Long> deptScope() {
        CurrentUser user = AuthContext.get();
        ScopeCondition scope = user == null ? null : user.getScope();
        if (scope == null || scope.isAll()) {
            return null;
        }
        if (scope.hasDept()) {
            return new ArrayList<>(scope.getDeptIds());
        }
        return EMPTY_SCOPE;
    }

    /** 仓库范围：全量或无仓库范围时不过滤，与 M5 库存台账一致（部门范围用户在库存上不看仓库维度） */
    private List<Long> warehouseScope() {
        CurrentUser user = AuthContext.get();
        ScopeCondition scope = user == null ? null : user.getScope();
        if (scope == null || scope.isAll() || !scope.hasWarehouse()) {
            return null;
        }
        return new ArrayList<>(scope.getWarehouseIds());
    }

    private BigDecimal money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal qty(BigDecimal value) {
        return nz(value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal ratio(Number part, Number total) {
        BigDecimal totalValue = total == null ? BigDecimal.ZERO : new BigDecimal(total.toString());
        if (totalValue.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal partValue = part == null ? BigDecimal.ZERO : new BigDecimal(part.toString());
        return partValue.divide(totalValue, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return nz(part).divide(total, 4, RoundingMode.HALF_UP);
    }

    private String plain(BigDecimal value) {
        return nz(value).stripTrailingZeros().toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
