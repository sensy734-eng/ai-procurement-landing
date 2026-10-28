package com.zufe.procurement.m9.query.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m3.order.dto.OrderQuery;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import com.zufe.procurement.m3.order.mapper.DeliveryProgressMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m3.order.service.PurchaseOrderService;
import com.zufe.procurement.m3.order.vo.OrderVO;
import com.zufe.procurement.m5.stock.dto.StockQuery;
import com.zufe.procurement.m5.stock.service.StockService;
import com.zufe.procurement.m5.stock.vo.StockBalanceVO;
import com.zufe.procurement.m6.price.dto.StatQuery;
import com.zufe.procurement.m6.price.service.PriceStatService;
import com.zufe.procurement.m6.price.vo.PriceHistoryVO;
import com.zufe.procurement.m6.price.vo.PriceStatVO;
import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 五类意图的执行器（M9-4、M9-5）。
 *
 * 受控要点：
 * - 只调既有业务服务的只读方法（订单列表、库存台账、M6 价格统计），
 *   与页面查询共用同一份数据范围条件，页面查不到的这里也查不到（M9-4-2）；
 * - 业务口径（可用量、超期天数、均价）由被调服务计算，本类不做二次计算（M9-5-3）；
 * - 结果为空分两种：单据号不存在返回 NotFound，条件无匹配返回 NoData（M9-5-2）；
 * - 本类不持有任何写方法，智能接口全只读（M9-5-4）。
 */
@Service
public class DataQueryExecutor {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PurchaseOrderService orderService;
    private final StockService stockService;
    private final PriceStatService priceStatService;
    private final PurchaseOrderMapper orderMapper;
    private final DeliveryProgressMapper deliveryProgressMapper;
    private final SupplierMapper supplierMapper;
    private final DataScopeService dataScopeService;

    public DataQueryExecutor(PurchaseOrderService orderService, StockService stockService,
                             PriceStatService priceStatService, PurchaseOrderMapper orderMapper,
                             DeliveryProgressMapper deliveryProgressMapper, SupplierMapper supplierMapper,
                             DataScopeService dataScopeService) {
        this.orderService = orderService;
        this.stockService = stockService;
        this.priceStatService = priceStatService;
        this.orderMapper = orderMapper;
        this.deliveryProgressMapper = deliveryProgressMapper;
        this.supplierMapper = supplierMapper;
        this.dataScopeService = dataScopeService;
    }

    public Outcome execute(String intentCode, Map<String, Object> params) {
        return switch (intentCode) {
            case "order_status" -> orderStatus(params);
            case "delivery_progress" -> deliveryProgress(params);
            case "purchase_price_history" -> priceHistory(params);
            case "stock_available" -> stockAvailable(params);
            case "supplier_delivery" -> supplierDelivery(params);
            default -> throw new IllegalStateException("白名单外意图：" + intentCode);
        };
    }

    // ================= 1 订单状态查询 =================

    private Outcome orderStatus(Map<String, Object> params) {
        OrderQuery query = baseOrderQuery(params);
        PageResult<OrderVO> page = orderService.page(query);
        List<OrderVO> rows = page.getRecords();
        Outcome outcome = new Outcome();
        outcome.setRowCount(rows.size());
        outcome.setScopeSnapshot(scopeSnapshot());

        List<ChatVO.Column> columns = List.of(
                column("orderNo", "订单号", "string"),
                column("supplierName", "供应商", "string"),
                column("orderDate", "订单日期", "date"),
                column("deliveryDate", "交货期", "date"),
                column("lifecycleStatusName", "生命周期", "string"),
                column("auditStatusName", "审批结果", "string"),
                column("fulfillStatusName", "履约状态", "string"),
                column("totalAmount", "含税金额", "money"),
                column("overdueDays", "超期天数", "number"));
        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (OrderVO row : rows) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("orderNo", row.getOrderNo());
            cell.put("supplierName", row.getSupplierName());
            cell.put("orderDate", row.getOrderDate());
            cell.put("deliveryDate", row.getDeliveryDate());
            cell.put("lifecycleStatusName", row.getLifecycleStatusName());
            cell.put("auditStatusName", row.getAuditStatusName());
            cell.put("fulfillStatusName", row.getFulfillStatusName());
            cell.put("totalAmount", row.getTotalAmount());
            cell.put("overdueDays", row.getOverdueDays());
            tableRows.add(cell);
        }
        outcome.setTable(table(columns, tableRows, page.getTotal(),
                "生命周期、审批结果、履约状态三个字段相互独立，不合并表述"));

        if (rows.isEmpty()) {
            String orderNo = str(params.get("order_no"));
            if (orderNo != null) {
                outcome.setResultCode(ResultCode.NOT_FOUND.getCode());
                outcome.setSummary("没有找到订单号 " + orderNo + "，可能编号有误或该订单不在您的数据范围内。");
            } else {
                outcome.setResultCode(ResultCode.NO_DATA.getCode());
                outcome.setSummary("按当前条件没有匹配的订单。可以放宽时间范围或去掉状态条件再问一次。");
            }
            return outcome;
        }
        outcome.setResultCode(ResultCode.SUCCESS.getCode());
        outcome.setSummary(describeOrders(rows));
        return outcome;
    }

    // ================= 2 交付进度查询 =================

    private Outcome deliveryProgress(Map<String, Object> params) {
        OrderQuery query = baseOrderQuery(params);
        if (Boolean.TRUE.equals(params.get("overdue_only"))) {
            query.setOverdue(true);
        } else {
            query.setFulfillStatus(null);
            query.setOverdue(null);
        }
        PageResult<OrderVO> page = orderService.page(query);
        List<OrderVO> rows = page.getRecords();

        Outcome outcome = new Outcome();
        outcome.setRowCount(rows.size());
        outcome.setScopeSnapshot(scopeSnapshot());
        List<ChatVO.Column> columns = List.of(
                column("orderNo", "订单号", "string"),
                column("supplierName", "供应商", "string"),
                column("deliveryDate", "交货期", "date"),
                column("fulfillStatusName", "履约状态", "string"),
                column("totalQty", "订单数量", "number"),
                column("overdueDays", "超期天数", "number"));
        List<Map<String, Object>> tableRows = new ArrayList<>();
        int overdue = 0;
        int maxOverdue = 0;
        for (OrderVO row : rows) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("orderNo", row.getOrderNo());
            cell.put("supplierName", row.getSupplierName());
            cell.put("deliveryDate", row.getDeliveryDate());
            cell.put("fulfillStatusName", row.getFulfillStatusName());
            cell.put("totalQty", row.getTotalQty());
            cell.put("overdueDays", row.getOverdueDays());
            tableRows.add(cell);
            if (row.getOverdueDays() != null && row.getOverdueDays() > 0) {
                overdue++;
                maxOverdue = Math.max(maxOverdue, row.getOverdueDays());
            }
        }
        outcome.setTable(table(columns, tableRows, page.getTotal(),
                "超期天数 = 当前日期 − 交货期，后端按自然日计算，不落库"));

        if (rows.isEmpty()) {
            outcome.setResultCode(ResultCode.NO_DATA.getCode());
            outcome.setSummary(Boolean.TRUE.equals(params.get("overdue_only"))
                    ? "当前没有超期未交付的订单，订单交付节奏正常。"
                    : "按当前条件没有匹配的订单。");
            return outcome;
        }
        outcome.setResultCode(ResultCode.SUCCESS.getCode());
        String summary = "共 " + rows.size() + " 张订单未完成交付";
        if (overdue > 0) {
            summary += "，其中 " + overdue + " 张已超期，最长超期 " + maxOverdue + " 天。";
        } else {
            summary += "，均未超过交货期。";
        }
        outcome.setSummary(summary);
        return outcome;
    }

    // ================= 3 历史采购价格查询 =================

    private Outcome priceHistory(Map<String, Object> params) {
        Long materialId = longValue(params.get("material_id"));
        Long supplierId = longValue(params.get("supplier_id"));
        LocalDate from = dateValue(params.get("date_from"));
        LocalDate to = dateValue(params.get("date_to"));
        Outcome outcome = new Outcome();
        outcome.setScopeSnapshot(scopeSnapshot());

        if (from != null || to != null) {
            PriceHistoryVO history = priceStatService.rangeStat(materialId, supplierId, from, to);
            List<ChatVO.Column> columns = List.of(
                    column("orderNo", "订单号", "string"),
                    column("orderDate", "订单日期", "date"),
                    column("supplierName", "供应商", "string"),
                    column("unitPrice", "单价", "money"),
                    column("quantity", "数量", "number"),
                    column("amount", "金额", "money"));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (PriceHistoryVO.DealRow deal : history.getDeals()) {
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("orderNo", deal.getOrderNo());
                cell.put("orderDate", deal.getOrderDate());
                cell.put("supplierName", deal.getSupplierName());
                cell.put("unitPrice", deal.getUnitPrice());
                cell.put("quantity", deal.getQuantity());
                cell.put("amount", deal.getAmount());
                rows.add(cell);
            }
            outcome.setTable(table(columns, rows, history.getSampleCount() == null ? 0 : history.getSampleCount(),
                    "成交口径：已审核通过（生命周期 3/4 且审批结果 2）的订单行，均价按金额加权"));
            outcome.setRowCount(rows.size());
            if (history.getSampleCount() == null || history.getSampleCount() == 0) {
                outcome.setResultCode(ResultCode.NO_DATA.getCode());
                outcome.setSummary("该物料在 " + rangeText(from, to)
                        + " 内没有已审核通过的成交记录，因此不给出均价（无样本不伪造）。");
                return outcome;
            }
            outcome.setResultCode(ResultCode.SUCCESS.getCode());
            outcome.setSummary("《" + nullToDash(history.getMaterialName()) + "》在 " + rangeText(from, to)
                    + " 共 " + history.getSampleCount() + " 笔成交，金额加权均价 "
                    + money(history.getAvgPrice()) + " 元，单价区间 "
                    + money(history.getMinUnitPrice()) + "~" + money(history.getMaxUnitPrice()) + " 元。");
            return outcome;
        }

        StatQuery query = new StatQuery();
        query.setMaterialId(materialId);
        query.setSupplierId(supplierId);
        List<PriceStatVO> stats = priceStatService.list(query);
        PriceStatVO stat = stats.isEmpty() ? null : stats.get(0);
        List<ChatVO.Column> columns = List.of(
                column("materialName", "物料", "string"),
                column("avgPrice", "窗口均价", "money"),
                column("avgSampleCount", "样本笔数", "number"),
                column("lastPrice", "最近成交价", "money"),
                column("lastBillNo", "来源单号", "string"),
                column("refPrice", "当前参考价", "money"),
                column("statWindow", "统计窗口", "string"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (stat != null) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("materialName", stat.getMaterialName() == null
                    ? "物料 " + materialId : stat.getMaterialName());
            cell.put("avgPrice", stat.getAvgPrice());
            cell.put("avgSampleCount", stat.getAvgSampleCount());
            cell.put("lastPrice", stat.getLastPrice());
            cell.put("lastBillNo", stat.getLastBillNo());
            cell.put("refPrice", stat.getRefPrice());
            cell.put("statWindow", stat.getStatWindow());
            rows.add(cell);
        }
        outcome.setTable(table(columns, rows, rows.size(),
                "窗口口径由系统参数固化；历史成交与当前参考价严格分离，不互相替代"));
        outcome.setRowCount(rows.size());
        if (stat == null || stat.getAvgPrice() == null) {
            outcome.setResultCode(ResultCode.NO_DATA.getCode());
            outcome.setSummary("该物料还没有可用的成交统计快照（没有已审核的采购订单，或快照未刷新），"
                    + "因此不给出成交均价。可以指定时间范围再问一次。");
            return outcome;
        }
        outcome.setResultCode(ResultCode.SUCCESS.getCode());
        String summary = "《" + nullToDash(stat.getMaterialName()) + "》按 " + nullToDash(stat.getStatWindow())
                + " 窗口统计：样本 " + stat.getAvgSampleCount() + " 笔，加权均价 "
                + money(stat.getAvgPrice()) + " 元，最近一次成交价 " + money(stat.getLastPrice()) + " 元"
                + (stat.getLastBillNo() == null ? "" : "（来源单号 " + stat.getLastBillNo() + "）");
        if (stat.getRefPrice() != null) {
            summary += "；当前参考价 " + money(stat.getRefPrice()) + " 元，属参考价口径，与历史成交不是同一概念。";
        } else {
            summary += "；该物料暂无生效参考价。";
        }
        outcome.setSummary(summary);
        return outcome;
    }

    // ================= 4 库存余量与可用量查询 =================

    private Outcome stockAvailable(Map<String, Object> params) {
        Long materialId = longValue(params.get("material_id"));
        Long warehouseId = longValue(params.get("warehouse_id"));
        StockQuery query = new StockQuery();
        query.setPageNo(1);
        query.setPageSize(50);
        query.setMaterialId(materialId);
        query.setWarehouseId(warehouseId);
        PageResult<StockBalanceVO> page = stockService.page(query);
        List<StockBalanceVO> rows = page.getRecords();
        Outcome outcome = new Outcome();
        outcome.setRowCount(rows.size());
        outcome.setScopeSnapshot(scopeSnapshot());

        List<ChatVO.Column> columns = List.of(
                column("materialNo", "物料编号", "string"),
                column("materialName", "物料", "string"),
                column("warehouseName", "仓库", "string"),
                column("quantity", "数量", "number"),
                column("frozenQty", "冻结", "number"),
                column("reservedQty", "预留", "number"),
                column("availableQty", "可用量", "number"));
        List<Map<String, Object>> tableRows = new ArrayList<>();
        BigDecimal totalAvailable = BigDecimal.ZERO;
        for (StockBalanceVO row : rows) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("materialNo", row.getMaterialNo());
            cell.put("materialName", row.getMaterialName());
            cell.put("warehouseName", row.getWarehouseName());
            cell.put("quantity", row.getQuantity());
            cell.put("frozenQty", row.getFrozenQty());
            cell.put("reservedQty", row.getReservedQty());
            cell.put("availableQty", row.getAvailableQty());
            tableRows.add(cell);
            if (row.getAvailableQty() != null) {
                totalAvailable = totalAvailable.add(row.getAvailableQty());
            }
        }
        outcome.setTable(table(columns, tableRows, page.getTotal(),
                "可用量 = 数量 − 冻结 − 预留（后端实时计算），按仓库分行"));

        if (rows.isEmpty()) {
            outcome.setResultCode(ResultCode.NO_DATA.getCode());
            outcome.setSummary("该物料没有库存记录：可能尚未入库，或库存不在您的数据范围内。");
            return outcome;
        }
        outcome.setResultCode(ResultCode.SUCCESS.getCode());
        BigDecimal quantity = rows.stream().map(StockBalanceVO::getQuantity)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal frozen = rows.stream().map(StockBalanceVO::getFrozenQty)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = rows.stream().map(StockBalanceVO::getReservedQty)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        outcome.setSummary("《" + nullToDash(rows.get(0).getMaterialName()) + "》在 " + rows.size()
                + " 个仓库合计：数量 " + number(quantity) + "，冻结 " + number(frozen)
                + "，预留 " + number(reserved) + "，可用量 " + number(totalAvailable) + "。");
        return outcome;
    }

    // ================= 5 供应商历史交付情况 =================

    private Outcome supplierDelivery(Map<String, Object> params) {
        Long supplierId = longValue(params.get("supplier_id"));
        LocalDate from = dateValue(params.get("date_from"));
        LocalDate to = dateValue(params.get("date_to"));
        Outcome outcome = new Outcome();
        outcome.setScopeSnapshot(scopeSnapshot());

        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                // 草稿与审批中的订单还没有交付事实，不计入历史交付口径（与 M3-5-5 交付汇总一致）
                .in(PurchaseOrder::getLifecycleStatus, List.of(3, 4))
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .ge(from != null, PurchaseOrder::getOrderDate, from)
                .le(to != null, PurchaseOrder::getOrderDate, to)
                .orderByDesc(PurchaseOrder::getOrderDate);
        dataScopeService.applyDept(wrapper, PurchaseOrder::getDeptId, currentScope());
        List<PurchaseOrder> orders = orderMapper.selectList(wrapper);

        List<ChatVO.Column> columns = List.of(
                column("orderNo", "订单号", "string"),
                column("orderDate", "订单日期", "date"),
                column("deliveryDate", "约定交货期", "date"),
                column("fulfillStatusName", "履约状态", "string"),
                column("lastDeliveryDate", "最后交付日期", "date"),
                column("onTime", "是否按期", "string"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (orders.isEmpty()) {
            outcome.setTable(table(columns, rows, 0, "只统计您的数据范围内、且订单日期落在查询区间的订单"));
            outcome.setResultCode(ResultCode.NO_DATA.getCode());
            outcome.setSummary("该供应商在您的数据范围内没有订单记录，无法给出交付情况。");
            return outcome;
        }

        List<Long> orderIds = orders.stream().map(PurchaseOrder::getId).toList();
        Map<Long, LocalDate> lastDelivery = new HashMap<>();
        for (Map<String, Object> row : deliveryProgressMapper.lastDeliveryDates(orderIds)) {
            Object id = row.get("orderId");
            Object last = row.get("lastDate");
            if (id != null && last != null) {
                lastDelivery.put(Long.valueOf(String.valueOf(id)),
                        LocalDate.parse(String.valueOf(last).substring(0, 10)));
            }
        }

        LocalDate today = LocalDate.now();
        int delivered = 0;
        int onTime = 0;
        int overdueOpen = 0;
        for (PurchaseOrder order : orders) {
            LocalDate last = lastDelivery.get(order.getId());
            LocalDate due = order.getDeliveryDate();
            boolean finished = order.getFulfillStatus() != null && order.getFulfillStatus() == 3;
            String onTimeText = "尚未交付完成";
            if (finished) {
                delivered++;
                if (due != null && last != null) {
                    if (!last.isAfter(due)) {
                        onTime++;
                        onTimeText = "按期";
                    } else {
                        onTimeText = "超期 " + (last.toEpochDay() - due.toEpochDay()) + " 天";
                    }
                } else if (due != null && order.getOrderDate() != null) {
                    onTimeText = "按期";
                    onTime++;
                }
            } else if (due != null && due.isBefore(today)) {
                overdueOpen++;
                onTimeText = "已超期未交付 " + (today.toEpochDay() - due.toEpochDay()) + " 天";
            }
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("orderNo", order.getOrderNo());
            cell.put("orderDate", order.getOrderDate() == null ? null : order.getOrderDate().toString());
            cell.put("deliveryDate", due == null ? null : due.toString());
            cell.put("fulfillStatusName", ParamExtractor.fulfillName(order.getFulfillStatus()));
            cell.put("lastDeliveryDate", last == null ? null : last.toString());
            cell.put("onTime", onTimeText);
            rows.add(cell);
        }
        outcome.setTable(table(columns, rows, orders.size(),
                "按期 = 已全部交付且最后交付日期不晚于约定交货期；只统计订单已交付完成的样本"));
        outcome.setRowCount(rows.size());
        outcome.setResultCode(ResultCode.SUCCESS.getCode());

        Supplier supplier = supplierId == null ? null : supplierMapper.selectById(supplierId);
        String supplierName = supplier == null ? "该供应商" : supplier.getSupplierName();
        StringBuilder summary = new StringBuilder();
        summary.append(supplierName).append("在您的数据范围内共有 ").append(orders.size()).append(" 张订单");
        if (from != null || to != null) {
            summary.append("（").append(rangeText(from, to)).append("）");
        }
        if (delivered == 0) {
            summary.append("，目前还没有交付完成的订单，暂不计算准时率");
        } else {
            BigDecimal rate = BigDecimal.valueOf(onTime)
                    .divide(BigDecimal.valueOf(delivered), 4, RoundingMode.HALF_UP);
            summary.append("，已完成交付 ").append(delivered).append(" 张，其中按期 ").append(onTime)
                    .append(" 张，准时率 ").append(rate.multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP)).append("%");
        }
        if (overdueOpen > 0) {
            summary.append("；当前有 ").append(overdueOpen).append(" 张已超期仍未交付。");
        } else {
            summary.append("；当前没有超期未交付的订单。");
        }
        summary.append("该统计为历史交付记录，不代表当前履约承诺。");
        outcome.setSummary(summary.toString());
        return outcome;
    }

    // ================= 公共辅助 =================

    private OrderQuery baseOrderQuery(Map<String, Object> params) {
        OrderQuery query = new OrderQuery();
        query.setPageNo(1);
        query.setPageSize(20);
        String orderNo = str(params.get("order_no"));
        if (orderNo != null) {
            query.setOrderNo(orderNo);
        }
        query.setSupplierId(longValue(params.get("supplier_id")));
        query.setLifecycleStatus(intValue(params.get("lifecycle_status")));
        query.setAuditStatus(intValue(params.get("audit_status")));
        query.setFulfillStatus(intValue(params.get("fulfill_status")));
        query.setStartDate(dateValue(params.get("date_from")));
        query.setEndDate(dateValue(params.get("date_to")));
        query.setAmountGte(decimalValue(params.get("amount_gte")));
        query.setAmountLte(decimalValue(params.get("amount_lte")));
        return query;
    }

    private String describeOrders(List<OrderVO> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(rows.size()).append(" 张订单。");
        int limit = Math.min(rows.size(), 3);
        for (int i = 0; i < limit; i++) {
            OrderVO row = rows.get(i);
            sb.append(row.getOrderNo()).append("：生命周期 ").append(nullToDash(row.getLifecycleStatusName()))
                    .append("、审批结果 ").append(nullToDash(row.getAuditStatusName()))
                    .append("、履约 ").append(nullToDash(row.getFulfillStatusName()));
            if (row.getOverdueDays() != null && row.getOverdueDays() > 0) {
                sb.append("，已超期 ").append(row.getOverdueDays()).append(" 天");
            }
            sb.append("；");
        }
        if (rows.size() > limit) {
            sb.append("其余 ").append(rows.size() - limit).append(" 张见下表。");
        } else {
            sb.setLength(sb.length() - 1);
            sb.append("。生命周期、审批结果、履约状态是三个独立字段，不能互相替代。");
        }
        return sb.toString();
    }

    private ChatVO.ResultTable table(List<ChatVO.Column> columns, List<Map<String, Object>> rows,
                                     long total, String caption) {
        ChatVO.ResultTable table = new ChatVO.ResultTable();
        table.setColumns(columns);
        table.setRows(rows);
        table.setTotal(total);
        table.setCaption(caption);
        return table;
    }

    private ChatVO.Column column(String key, String label, String type) {
        ChatVO.Column column = new ChatVO.Column();
        column.setKey(key);
        column.setLabel(label);
        column.setType(type);
        return column;
    }

    private Map<String, Object> scopeSnapshot() {
        ScopeCondition scope = currentScope();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("all", scope.isAll());
        snapshot.put("deptIds", new ArrayList<>(scope.getDeptIds()));
        snapshot.put("warehouseIds", new ArrayList<>(scope.getWarehouseIds()));
        return snapshot;
    }

    private ScopeCondition currentScope() {
        CurrentUser user = AuthContext.get();
        return user == null ? new ScopeCondition() : user.getScope();
    }

    private String rangeText(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return from.format(DATE) + " 至 " + to.format(DATE);
        }
        if (from != null) {
            return from.format(DATE) + " 之后";
        }
        if (to != null) {
            return to.format(DATE) + " 之前";
        }
        return "全部时间";
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate dateValue(Object value) {        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        String text = String.valueOf(value);
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String money(BigDecimal value) {
        return value == null ? "—" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String number(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /** 执行结果：结果码 + 表格 + 转述 + 注入的数据范围快照 */
    @Data
    public static class Outcome {
        private String resultCode = ResultCode.SUCCESS.getCode();
        private ChatVO.ResultTable table;
        private String summary;
        private Integer rowCount = 0;
        private List<String> notices = new ArrayList<>();
        private Map<String, Object> scopeSnapshot;
    }
}
