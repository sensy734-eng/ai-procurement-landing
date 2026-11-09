package com.zufe.procurement.m7.assistant.task;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m3.order.service.PurchaseOrderService;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m3.order.vo.OrderItemVO;
import com.zufe.procurement.m6.price.mapper.PriceReferenceMapper;
import com.zufe.procurement.m6.price.service.PriceDeviationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import static com.zufe.procurement.m7.assistant.task.TaskContracts.*;

/** A short, read-only snapshot. Model/network calls must stay outside this transaction. */
@Service
public class OrderEvidenceReader {
    private final PurchaseOrderService orders;
    private final PurchaseOrderMapper orderMapper;
    private final PriceReferenceMapper references;
    private final PriceDeviationService deviations;
    private final JdbcTemplate db;
    private final TaskAccess access;
    private final TaskRunStore runs;
    public OrderEvidenceReader(PurchaseOrderService orders, PurchaseOrderMapper orderMapper,
            PriceReferenceMapper references, PriceDeviationService deviations, JdbcTemplate db,
            TaskAccess access, TaskRunStore runs) {
        this.orders = orders; this.orderMapper = orderMapper; this.references = references;
        this.deviations = deviations; this.db = db; this.access = access; this.runs = runs;
    }

    @Transactional(readOnly = true)
    public ReviewResult read(Long orderId, String focus) {
        access.require("m7:chat:query", "m9:data:query", "m3:order:view");
        var order = orders.detail(orderId); // Existing department scope check is mandatory.
        var scope = AuthContext.getOrThrow().getScope();
        if (scope.hasOwner() && !scope.isAll() && order.getItems().stream().anyMatch(i -> !scope.getOwnerNos().contains(Objects.equals(i.getOwnerType(),1) ? "PUBLIC" : i.getOwnerNo())))
            throw new BizException(ResultCode.NO_PERMISSION, "订单包含归属范围外的明细，无法进行整单审查");
        if (order.getItems().size() > 30)
            throw new BizException(ResultCode.UNSUPPORTED, "当前单次审查最多支持30行订单明细，请使用业务明细核对");
        ReviewResult result = new ReviewResult();
        result.setOrderId(orderId); result.setOrderNo(order.getOrderNo()); result.setFocus(focus);
        result.setAsOf(LocalDateTime.now().toString());
        String path = "/order?orderId=" + orderId;
        int header = add(result, "order", "订单状态", "订单" + order.getOrderNo() + "，生命周期" + order.getLifecycleStatusName()
                + "，审批" + order.getAuditStatusName() + "，履约" + order.getFulfillStatusName()
                + "，订单含税金额" + n(order.getTotalAmount()) + "元。", path, orderId, null);
        result.getChecks().add(new Check("workflow", Objects.equals(order.getAuditStatus(), 3) ? "attention" : "information",
                "流程状态", "当前为" + order.getLifecycleStatusName() + " / " + order.getAuditStatusName()
                + "。审批操作须由有权限的用户在原页面完成。", List.of(header)));
        if (order.getItems().isEmpty()) result.getMissingEvidence().add("订单没有明细，无法检查价格与交付");
        boolean price = !"delivery".equals(focus);
        boolean delivery = !"price".equals(focus);
        boolean canPrice = AuthContext.getOrThrow().hasPermission("m6:evidence:view");
        boolean canHistory = AuthContext.getOrThrow().hasPermission("m6:stat:view");
        var entity = orderMapper.selectById(orderId);
        var referenceDate = entity.getCreatedAt().toLocalDate();
        for (OrderItemVO item : order.getItems()) {
            String unit = item.getUnitName() == null ? "（单位未配置）" : item.getUnitName();
            int fact = add(result, "order_item", "明细 " + item.getItemNo() + " · " + item.getMaterialName(),
                    item.getMaterialName() + " " + Objects.toString(item.getSpec(), "") + "，数量" + n(item.getQuantity()) + unit
                    + "，单价" + n(item.getUnitPrice()) + "元，已交付" + n(item.getDeliveredQty()) + unit
                    + "，未交付" + n(item.getRemainingQty()) + unit + "，交期" + Objects.toString(item.getDeliveryDate(), "未填写")
                    + "，超期" + Objects.toString(item.getOverdueDays(), "0") + "天。", path, item.getId(), null);
            if (delivery) {
                boolean active = Objects.equals(order.getLifecycleStatus(), 3) && !Objects.equals(item.getItemFulfillStatus(), 4);
                String status = !active ? "information" : item.getDeliveryDate() == null ? "unknown"
                        : item.getOverdueDays() != null && item.getOverdueDays() > 0 ? "attention" : "pass";
                result.getChecks().add(new Check("delivery:" + item.getId(), status, "交付检查 · " + item.getMaterialName(),
                        !active ? "当前不属于在执行的已审核明细，不作逾期风险判定。"
                        : "unknown".equals(status) ? "缺少交期，无法判定是否逾期。"
                        : "attention".equals(status) ? "交期已过且仍有未交付数量；延期原因需另行核实。"
                        : "未发现已到期且未交付完整的情况；不代表未来交付保证。", List.of(fact)));
            }
            if (!price) continue;
            if (!canPrice) {
                result.getChecks().add(new Check("price:" + item.getId(), "unknown", "价格检查 · " + item.getMaterialName(),
                        "无价格证据查看权限，此项未检查。", List.of(fact)));
                if (canHistory) addHistory(result, item, orderId);
                continue;
            }
            var ref = references.selectEffectiveAsOf(item.getMaterialId(), referenceDate);
            BigDecimal rate = ref == null ? null : deviations.calcRate(item.getUnitPrice(), ref.getRefPrice());
            if (rate == null) {
                result.getChecks().add(new Check("price:" + item.getId(), "unknown", "价格检查 · " + item.getMaterialName(),
                        "缺少有效参考价，未判定价格偏离。", List.of(fact)));
                result.getMissingEvidence().add(item.getMaterialName() + "：缺少参考价");
            } else {
                int level = deviations.levelOf(rate);
                String text = item.getMaterialName() + "，订单单价" + n(item.getUnitPrice()) + "元，参考价" + n(ref.getRefPrice())
                        + "元，参考生效日" + ref.getEffectiveDate() + "，偏离率" + n(rate.movePointRight(2)) + "%"
                        + "，关注阈值" + n(deviations.focusThreshold().movePointRight(2)) + "%"
                        + "，显著阈值" + n(deviations.significantThreshold().movePointRight(2)) + "%。";
                int id = add(result, "price_reference", "价格复核 · " + item.getMaterialName(), text, path, ref.getId(),
                        String.valueOf(ref.getPriceVersion()));
                result.getChecks().add(new Check("price:" + item.getId(), level > 0 ? "attention" : "pass",
                        "价格检查 · " + item.getMaterialName(), text + "按订单创建日可查参考版本复核；参考价不是供应商报价。", List.of(fact, id)));
            }
            if (canHistory) addHistory(result, item, orderId);
        }
        if (price && !canPrice) result.getMissingEvidence().add("价格证据未授权");
        if (price && !canHistory) result.getMissingEvidence().add("历史成交统计未授权");
        result.getStages().add("已读取权限范围内的订单与明细");
        result.getStages().add("已完成所选规则检查，缺失项单独标注");
        // Excludes the wall clock and later model/retrieval results; changes to any source/check invalidate the snapshot.
        result.setSnapshotId(runs.hash(List.of(order, result.getEvidence(), result.getChecks(), result.getMissingEvidence(), access.fingerprint())));
        return result;
    }

    private void addHistory(ReviewResult result, OrderItemVO item, Long currentOrder) {
        access.require("m6:stat:view");
        var scope = AuthContext.getOrThrow().getScope();
        List<Object> args = new ArrayList<>(List.of(item.getMaterialId(), item.getUnitId(), currentOrder));
        StringBuilder sql = new StringBuilder("SELECT o.id,o.order_no,i.unit_price,i.quantity FROM purchase_order_item i JOIN purchase_order o ON o.id=i.order_id "
                + "WHERE o.is_deleted=0 AND i.is_deleted=0 AND o.lifecycle_status IN (3,4) AND o.audit_status=2 "
                + "AND i.material_id=? AND i.unit_id=? AND o.id<>? AND i.quantity>0 AND i.tax_rate <=> ?"
                + " AND o.created_at < (SELECT created_at FROM purchase_order WHERE id=?)");
        args.add(item.getTaxRate());
        args.add(currentOrder);
        if (!scope.isAll()) {
            if (!scope.hasDept()) sql.append(" AND 1=0");
            else { sql.append(" AND o.dept_id IN (").append(marks(scope.getDeptIds().size())).append(')'); args.addAll(scope.getDeptIds()); }
            if (scope.hasOwner()) {
                sql.append(" AND (i.owner_no IN (").append(marks(scope.getOwnerNos().size())).append(')'); args.addAll(scope.getOwnerNos());
                if(scope.getOwnerNos().contains("PUBLIC")) sql.append(" OR i.owner_type=1");
                sql.append(')');
            }
        }
        sql.append(" ORDER BY o.order_date DESC,o.id DESC,i.id DESC LIMIT 5");
        var rows = db.queryForList(sql.toString(), args.toArray());
        if (rows.isEmpty()) { result.getMissingEvidence().add(item.getMaterialName() + "：当前数据范围内无同物料、单位、税率的历史成交样本"); return; }
        BigDecimal cost = BigDecimal.ZERO, quantity = BigDecimal.ZERO;
        List<Integer> ids = new ArrayList<>();
        for (var row : rows) {
            BigDecimal price = (BigDecimal) row.get("unit_price"), qty = (BigDecimal) row.get("quantity");
            cost = cost.add(price.multiply(qty)); quantity = quantity.add(qty);
            ids.add(add(result, "history", "历史成交 · " + row.get("order_no"), item.getMaterialName() + "，单价" + n(price)
                    + "元，数量" + n(qty) + item.getUnitName() + "。创建时间早于本单的已审批订单明细，仅当前授权范围，非已入库或付款统计，非供应商当前报价。",
                    "/order?orderId=" + row.get("id"), ((Number) row.get("id")).longValue(), null));
        }
        String summary = "当前授权范围最近" + rows.size() + "条同物料、单位、税率成交明细，按数量加权均价"
                + n(cost.divide(quantity, 4, RoundingMode.HALF_UP)) + "元。";
        int id = add(result, "history_summary", "历史统计 · " + item.getMaterialName(), summary, null, item.getMaterialId(), null);
        ids.add(id);
        result.getChecks().add(new Check("history:" + item.getId(), "information", "历史价格参考 · " + item.getMaterialName(), summary, ids));
    }

    private static String marks(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    public static String n(BigDecimal value) { return value == null ? "未知" : value.stripTrailingZeros().toPlainString(); }
    public static int add(ReviewResult result, String kind, String title, String text, String path, Long source, String version) {
        int id = result.getEvidence().size() + 1;
        result.getEvidence().add(new Evidence(id, kind, title, text, path, source, version)); return id;
    }
}
