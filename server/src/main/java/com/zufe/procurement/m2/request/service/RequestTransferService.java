package com.zufe.procurement.m2.request.service;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BillNoGenerator;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m11.system.service.OperationLogService;
import com.zufe.procurement.m2.request.dto.TransferRequest;
import com.zufe.procurement.m2.request.entity.PurchaseRequest;
import com.zufe.procurement.m2.request.entity.PurchaseRequestItem;
import com.zufe.procurement.m2.request.mapper.PurchaseRequestItemMapper;
import com.zufe.procurement.m2.request.mapper.PurchaseRequestMapper;
import com.zufe.procurement.m2.request.vo.TransferResultVO;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import com.zufe.procurement.m3.order.entity.PurchaseOrderItem;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderItemMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m6.price.service.PriceDeviationService;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 申请转订单服务，覆盖功能点 M2-4-1 按行选择转订单、M2-4-2 批量转订单、
 * M2-4-3 已转数量回写。同一事务内完成：订单草稿生成、明细行写入、
 * 申请行已转数量回写、操作日志。任一步失败整体回滚，不会出现
 * "订单已建但申请行未回写"的中间态。
 *
 * 口径：
 * - 只有已审核（lifecycle=3）的申请单可转，且单据部门须在转单人数据范围内；
 * - 同一供应商的多行合并为一张订单草稿（M2-4-2），行级追溯经
 *   purchase_order_item.source_request_item_id 保留，多单合并时主表
 *   source_request_no 以逗号连接全部来源单号；
 * - 已转数量（transferred_qty）的唯一写入点是本服务（M2-4-3），
 *   到货与入库不回写申请行，与库存唯一写入点（R2）同构；
 * - 行预估单价作为订单含税单价，未填预估单价的行不能转订单；
 * - 转出的订单为草稿，提交与审批在 M3 实现（轮次 5）。
 */
@Service
public class RequestTransferService {

    private static final int LIFECYCLE_APPROVED = 3;
    private static final int ITEM_PENDING = 1;
    private static final int ITEM_PARTIAL = 2;
    private static final int ITEM_TRANSFERRED = 3;
    private static final int BILL_TYPE_REQUEST = 1;

    /** 默认税率，与演示数据及 DDL 默认值一致：0.1300 表示 13% */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.1300");
    private static final int SETTLEMENT_MONTHLY = 1;
    private static final int NO_ATTEMPTS = 3;

    private final PurchaseRequestMapper requestMapper;
    private final PurchaseRequestItemMapper itemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final SupplierMapper supplierMapper;
    private final BillNoGenerator billNoGenerator;
    private final OperationLogService operationLogService;
    private final PriceDeviationService priceDeviationService;

    public RequestTransferService(PurchaseRequestMapper requestMapper,
                                  PurchaseRequestItemMapper itemMapper,
                                  PurchaseOrderMapper orderMapper,
                                  PurchaseOrderItemMapper orderItemMapper,
                                  SupplierMapper supplierMapper,
                                  BillNoGenerator billNoGenerator,
                                  OperationLogService operationLogService,
                                  PriceDeviationService priceDeviationService) {
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.supplierMapper = supplierMapper;
        this.billNoGenerator = billNoGenerator;
        this.operationLogService = operationLogService;
        this.priceDeviationService = priceDeviationService;
    }

    /**
     * 按行选择转订单 / 批量转订单。sources 为空表示所有可转行，
     * 否则只转勾选的行；按建议供应商分组合并。
     */
    @Transactional
    public TransferResultVO transfer(TransferRequest request) {
        if (request.getSources() == null || request.getSources().isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请至少选择一张申请单");
        }
        CurrentUser currentUser = AuthContext.getOrThrow();
        ScopeCondition scope = currentUser.getScope();

        // 1. 加载并校验申请单：已审核、在数据范围内、未被删除
        List<PurchaseRequest> bills = new ArrayList<>();
        for (TransferRequest.TransferSource source : request.getSources()) {
            PurchaseRequest bill = requestMapper.selectById(source.getRequestId());
            if (bill == null) {
                throw new BizException(ResultCode.NOT_FOUND, "申请单不存在：" + source.getRequestId());
            }
            if (!Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_APPROVED)) {
                throw new BizException(ResultCode.UNSUPPORTED,
                        "申请单 " + bill.getRequestNo() + " 不是已审核状态，不能转订单");
            }
            if (!scope.isAll() && (!scope.hasDept() || !scope.getDeptIds().contains(bill.getDeptId()))) {
                throw new BizException(ResultCode.NO_PERMISSION,
                        "申请单 " + bill.getRequestNo() + " 不在您的数据范围内");
            }
            bills.add(bill);
        }

        // 2. 收集可转明细行：待转或部分已转、剩余数量大于 0、勾选命中
        List<PurchaseRequestItem> transferable = new ArrayList<>();
        for (int i = 0; i < bills.size(); i++) {
            PurchaseRequest bill = bills.get(i);
            TransferRequest.TransferSource source = request.getSources().get(i);
            List<PurchaseRequestItem> items = itemMapper.selectByRequestId(bill.getId());
            Set<Long> selected = source.getItemIds() == null || source.getItemIds().isEmpty()
                    ? null : Set.copyOf(source.getItemIds());
            for (PurchaseRequestItem item : items) {
                if (item.getItemStatus() != ITEM_PENDING && item.getItemStatus() != ITEM_PARTIAL) {
                    continue;
                }
                if (remainingQty(item).signum() <= 0) {
                    continue;
                }
                if (selected != null && !selected.contains(item.getId())) {
                    continue;
                }
                transferable.add(item);
            }
        }
        if (transferable.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "没有可转订单的明细行");
        }

        // 3. 前置校验：建议供应商必填且存在，预估单价必填（订单含税单价非空）
        for (PurchaseRequestItem item : transferable) {
            if (item.getSuggestSupplierId() == null) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "存在未指定建议供应商的明细行，不能转订单（请先在明细行指定建议供应商）");
            }
            if (item.getEstPrice() == null || item.getEstPrice().signum() <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "存在未填写预估单价的明细行，不能转订单（订单含税单价取自行预估单价）");
            }
        }

        // 4. 按建议供应商分组，同一供应商的多行合并为一张订单（M2-4-2）
        Map<Long, List<PurchaseRequestItem>> bySupplier = transferable.stream()
                .collect(Collectors.groupingBy(PurchaseRequestItem::getSuggestSupplierId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, PurchaseRequest> billById = bills.stream()
                .collect(Collectors.toMap(PurchaseRequest::getId, Function.identity(), (a, b) -> a));

        TransferResultVO result = new TransferResultVO();
        for (Map.Entry<Long, List<PurchaseRequestItem>> entry : bySupplier.entrySet()) {
            Long supplierId = entry.getKey();
            List<PurchaseRequestItem> items = entry.getValue();
            Supplier supplier = supplierMapper.selectById(supplierId);
            if (supplier == null || !Objects.equals(supplier.getStatus(), 1)) {
                throw new BizException(ResultCode.UNSUPPORTED, "建议供应商不存在或已停用，不能转订单");
            }

            PurchaseOrder order = buildOrder(supplierId, currentUser.getUserId(), items, billById);
            Long orderId = insertOrderWithRetry(order);
            insertOrderItems(orderId, items, order);

            // M2-4-3 已转数量回写
            for (PurchaseRequestItem item : items) {
                BigDecimal transferred = item.getTransferredQty() == null
                        ? BigDecimal.ZERO : item.getTransferredQty();
                BigDecimal newTransferred = transferred.add(item.getQuantity());
                PurchaseRequestItem update = new PurchaseRequestItem();
                update.setId(item.getId());
                update.setVersion(item.getVersion());
                update.setTransferredQty(newTransferred);
                update.setItemStatus(newTransferred.compareTo(item.getQuantity()) >= 0
                        ? ITEM_TRANSFERRED : ITEM_PARTIAL);
                if (itemMapper.updateById(update) <= 0) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "申请明细行状态已变更，请刷新后重试");
                }
            }

            List<String> sourceNos = items.stream()
                    .map(item -> billById.get(item.getRequestId()))
                    .filter(Objects::nonNull)
                    .map(PurchaseRequest::getRequestNo)
                    .distinct()
                    .collect(Collectors.toList());
            operationLogService.record("M2", "transfer", "purchase_order", orderId, order.getOrderNo(),
                    "申请转订单，来源 " + String.join("、", sourceNos) + "，转入 " + items.size()
                            + " 行，供应商 " + supplier.getSupplierName());

            TransferResultVO.TransferredOrder vo = new TransferResultVO.TransferredOrder();
            vo.setOrderId(orderId);
            vo.setOrderNo(order.getOrderNo());
            vo.setSupplierId(supplierId);
            vo.setSupplierName(supplier.getSupplierName());
            vo.setItemCount(items.size());
            vo.setTotalQty(order.getTotalQty());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setSourceRequestNo(String.join("、", sourceNos));
            result.getOrders().add(vo);
            result.getRequestNos().addAll(sourceNos);
        }
        return result;
    }

    private PurchaseOrder buildOrder(Long supplierId, Long buyerId, List<PurchaseRequestItem> items,
                                     Map<Long, PurchaseRequest> billById) {
        PurchaseRequest firstBill = billById.get(items.get(0).getRequestId());
        BigDecimal totalQty = items.stream().map(PurchaseRequestItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getQuantity().multiply(item.getEstPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal noTaxAmount = totalAmount.divide(BigDecimal.ONE.add(DEFAULT_TAX_RATE), 2, RoundingMode.HALF_UP);

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(null); // 插入时生成
        order.setSourceRequestId(firstBill.getId());
        order.setSourceRequestNo(firstBill.getRequestNo());
        order.setSupplierId(supplierId);
        order.setBuyerId(buyerId);
        order.setDeptId(firstBill.getDeptId());
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(items.stream().map(PurchaseRequestItem::getRequireDate)
                .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null));
        order.setDeliveryPlace(null);
        order.setSettlementType(SETTLEMENT_MONTHLY);
        order.setLifecycleStatus(1);
        order.setAuditStatus(1);
        order.setFulfillStatus(1);
        order.setTotalQty(totalQty.setScale(4, RoundingMode.HALF_UP));
        order.setTotalAmount(totalAmount);
        order.setNoTaxAmount(noTaxAmount);
        order.setTaxAmount(totalAmount.subtract(noTaxAmount));
        order.setDeviationFlag(0);
        order.setRemark("由采购申请转单生成");
        return order;
    }

    /** 订单号按编号规则生成，并发撞唯一键时重新取流水重试。 */
    private Long insertOrderWithRetry(PurchaseOrder order) {
        for (int attempt = 1; attempt <= NO_ATTEMPTS; attempt++) {
            order.setOrderNo(billNoGenerator.next("bill_no_rule_order", orderMapper::maxSeqByPrefix));
            try {
                orderMapper.insert(order);
                return order.getId();
            } catch (DuplicateKeyException ex) {
                if (attempt == NO_ATTEMPTS) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "订单号生成冲突，请重试");
                }
            }
        }
        throw new BizException(ResultCode.INVALID_PARAMETER, "订单号生成失败");
    }

    private void insertOrderItems(Long orderId, List<PurchaseRequestItem> items, PurchaseOrder bill) {
        int itemNo = 0;
        for (PurchaseRequestItem item : items) {
            BigDecimal amount = item.getQuantity().multiply(item.getEstPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal noTax = amount.divide(BigDecimal.ONE.add(DEFAULT_TAX_RATE), 2, RoundingMode.HALF_UP);
            PurchaseOrderItem orderItem = new PurchaseOrderItem();
            orderItem.setOrderId(orderId);
            orderItem.setItemNo(++itemNo);
            orderItem.setMaterialId(item.getMaterialId());
            orderItem.setSourceRequestItemId(item.getId());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setUnitId(item.getUnitId());
            orderItem.setUnitPrice(item.getEstPrice());
            orderItem.setTaxRate(DEFAULT_TAX_RATE);
            orderItem.setAmount(amount);
            orderItem.setNoTaxAmount(noTax);
            orderItem.setDeliveryDate(item.getRequireDate());
            orderItem.setDeliveryPlace(null);
            orderItem.setDeliveredQty(BigDecimal.ZERO);
            orderItem.setOwnerType(item.getOwnerType());
            orderItem.setOwnerId(item.getOwnerId());
            orderItem.setOwnerNo(item.getOwnerNo());
            orderItem.setItemFulfillStatus(1);
            // M3-6 偏离判定与手工建单同口径：按订单创建日选用当时生效参考价
            priceDeviationService.applyToItem(orderItem, bill);
            orderItem.setRemark(item.getRemark());
            orderItemMapper.insert(orderItem);
        }
        // 整单偏离标记 = 明细行最高等级
        priceDeviationService.recomputeOrderFlag(orderId);
    }

    private BigDecimal remainingQty(PurchaseRequestItem item) {
        BigDecimal transferred = item.getTransferredQty() == null ? BigDecimal.ZERO : item.getTransferredQty();
        return item.getQuantity().subtract(transferred).max(BigDecimal.ZERO);
    }
}
