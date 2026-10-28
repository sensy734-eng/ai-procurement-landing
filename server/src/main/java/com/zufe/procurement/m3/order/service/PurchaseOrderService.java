package com.zufe.procurement.m3.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BillNoGenerator;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.CsvWriter;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.owner.entity.OwnerObject;
import com.zufe.procurement.m1.owner.mapper.OwnerObjectMapper;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.entity.SupplierScope;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m1.supplier.mapper.SupplierScopeMapper;
import com.zufe.procurement.m11.approval.entity.ApprovalTask;
import com.zufe.procurement.m11.approval.service.ApprovalEngineService;
import com.zufe.procurement.m11.system.entity.SysOperationLog;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysOperationLogMapper;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m2.request.service.PurchaseRequestService;
import com.zufe.procurement.m2.request.service.RequestTransferService;
import com.zufe.procurement.m2.request.vo.ApprovalHistoryVO;
import com.zufe.procurement.m2.request.vo.OwnerOptionVO;
import com.zufe.procurement.m2.request.vo.RequestVO;
import com.zufe.procurement.m2.request.vo.TransferResultVO;
import com.zufe.procurement.m3.order.dto.DeliveryRegisterRequest;
import com.zufe.procurement.m3.order.dto.FromRequestRequest;
import com.zufe.procurement.m3.order.dto.OrderChangeRequest;
import com.zufe.procurement.m3.order.dto.OrderCloseRequest;
import com.zufe.procurement.m3.order.dto.OrderItemCloseRequest;
import com.zufe.procurement.m3.order.dto.OrderItemSaveRequest;
import com.zufe.procurement.m3.order.dto.OrderQuery;
import com.zufe.procurement.m3.order.dto.OrderRejectRequest;
import com.zufe.procurement.m3.order.dto.OrderSaveRequest;
import com.zufe.procurement.m3.order.entity.DeliveryProgress;
import com.zufe.procurement.m3.order.entity.OrderChangeLog;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import com.zufe.procurement.m3.order.entity.PurchaseOrderItem;
import com.zufe.procurement.m3.order.mapper.DeliveryProgressMapper;
import com.zufe.procurement.m3.order.mapper.OrderChangeLogMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderItemMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m3.order.vo.DeliveryRecordVO;
import com.zufe.procurement.m3.order.vo.DeliverySummaryVO;
import com.zufe.procurement.m3.order.vo.OrderChangeLogVO;
import com.zufe.procurement.m3.order.vo.OrderItemVO;
import com.zufe.procurement.m3.order.vo.OrderTodoVO;
import com.zufe.procurement.m3.order.vo.OrderVO;
import com.zufe.procurement.m4.arrival.mapper.ArrivalInspectionMapper;
import com.zufe.procurement.m5.inbound.mapper.InboundOrderMapper;
import com.zufe.procurement.m11.system.service.OperationLogService;
import com.zufe.procurement.m6.price.service.PriceDeviationService;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购订单服务，覆盖功能点 M3-1-1 至 M3-5-5（M3-6 价格偏离随轮次 8 的
 * M6 价格参考实现，本版本不计算偏离率）。
 *
 * 业务口径集中在本层：
 * - 三状态分三个独立字段互不推导（R1）：lifecycle 单据生命周期、
 *   audit 审批结果、fulfill 业务履约；fulfill 由明细行重算，不由人工选择；
 * - delivered_qty 已交付数量的唯一写入点是采购入库单确认（R2），
 *   交付进度登记只写 delivery_progress 流水，不改履约数量；
 * - 反审与作废一律「禁止」而非「级联」：存在交付登记、到货验收或入库
 *   记录的订单一律不允许反审与作废（M3-3-5、M3-1-5 的前置口径），
 *   避免破坏库存唯一写入点与在途追溯；
 * - 剩余数量不落库，由 quantity − delivered_qty 现算；
 * - 超期天数不落库，按当前日期与交货期现算（M3-5-4）；
 * - 变更痕迹只增不删，整单变更与行级变更统一落 order_change_log（M3-4）。
 */
@Service
public class PurchaseOrderService {

    // ---------------- 三状态与枚举常量 ----------------

    /** 单据生命周期状态 */
    private static final int LIFECYCLE_DRAFT = 1;
    private static final int LIFECYCLE_SUBMITTED = 2;
    private static final int LIFECYCLE_APPROVED = 3;
    private static final int LIFECYCLE_CLOSED = 4;
    private static final int LIFECYCLE_VOIDED = 5;

    /** 审批结果状态 */
    private static final int AUDIT_PENDING = 1;
    private static final int AUDIT_PASSED = 2;
    private static final int AUDIT_REJECTED = 3;

    /** 业务履约状态（主单与明细行同构） */
    private static final int FULFILL_PENDING = 1;
    private static final int FULFILL_PARTIAL = 2;
    private static final int FULFILL_FULL = 3;
    private static final int FULFILL_CLOSED = 4;

    /** 结算方式 */
    private static final int SETTLEMENT_MONTHLY = 1;
    private static final int SETTLEMENT_ON_DELIVERY = 2;
    private static final int SETTLEMENT_PREPAID = 3;

    /** 归属类型 */
    private static final int OWNER_PUBLIC = 1;

    private static final int BILL_TYPE_ORDER = ApprovalEngineService.BILL_TYPE_ORDER;

    /** 默认税率，与演示数据及 DDL 默认值一致：0.1300 表示 13% */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.1300");

    private static final int NO_ATTEMPTS = 3;

    private static final String PERM_ITEM_OWNER = "m3:order_item:owner";

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final OrderChangeLogMapper changeLogMapper;
    private final DeliveryProgressMapper deliveryMapper;
    private final ArrivalInspectionMapper arrivalMapper;
    private final InboundOrderMapper inboundMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierScopeMapper scopeMapper;
    private final DictItemMapper dictItemMapper;
    private final OwnerObjectMapper ownerObjectMapper;
    private final SysUserMapper userMapper;
    private final ApprovalEngineService approvalEngine;
    private final BillNoGenerator billNoGenerator;
    private final DataScopeService dataScopeService;
    private final OperationLogService operationLogService;
    private final SysOperationLogMapper operationLogMapper;
    private final RequestTransferService transferService;
    private final PurchaseRequestService requestService;
    private final PriceDeviationService priceDeviationService;

    public PurchaseOrderService(PurchaseOrderMapper orderMapper,
                                PurchaseOrderItemMapper orderItemMapper,
                                OrderChangeLogMapper changeLogMapper,
                                DeliveryProgressMapper deliveryMapper,
                                ArrivalInspectionMapper arrivalMapper,
                                InboundOrderMapper inboundMapper,
                                MaterialMapper materialMapper,
                                SupplierMapper supplierMapper,
                                SupplierScopeMapper scopeMapper,
                                DictItemMapper dictItemMapper,
                                OwnerObjectMapper ownerObjectMapper,
                                SysUserMapper userMapper,
                                ApprovalEngineService approvalEngine,
                                BillNoGenerator billNoGenerator,
                                DataScopeService dataScopeService,
                                OperationLogService operationLogService,
                                SysOperationLogMapper operationLogMapper,
                                RequestTransferService transferService,
                                PurchaseRequestService requestService,
                                PriceDeviationService priceDeviationService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.changeLogMapper = changeLogMapper;
        this.deliveryMapper = deliveryMapper;
        this.arrivalMapper = arrivalMapper;
        this.inboundMapper = inboundMapper;
        this.materialMapper = materialMapper;
        this.supplierMapper = supplierMapper;
        this.scopeMapper = scopeMapper;
        this.dictItemMapper = dictItemMapper;
        this.ownerObjectMapper = ownerObjectMapper;
        this.userMapper = userMapper;
        this.approvalEngine = approvalEngine;
        this.billNoGenerator = billNoGenerator;
        this.dataScopeService = dataScopeService;
        this.operationLogService = operationLogService;
        this.operationLogMapper = operationLogMapper;
        this.transferService = transferService;
        this.requestService = requestService;
        this.priceDeviationService = priceDeviationService;
    }

    // ================= M3-1-1 列表与检索 =================

    /**
     * 分页列表。数据范围按当前用户的部门维度注入（M11-3-3），
     * 范围为空时失败关闭返回空列表，不在内存中筛选。
     * overdue=true 时叠加 M3-5-4 超期条件：交货期已过且未交付完。
     */
    public PageResult<OrderVO> page(OrderQuery query) {
        Page<PurchaseOrder> page = new Page<>(Math.max(query.getPageNo(), 1),
                Math.min(Math.max(query.getPageSize(), 1), 100));
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .like(StringUtils.hasText(query.getOrderNo()), PurchaseOrder::getOrderNo,
                        query.getOrderNo() == null ? null : query.getOrderNo().trim())
                .eq(query.getSupplierId() != null, PurchaseOrder::getSupplierId, query.getSupplierId())
                .eq(query.getLifecycleStatus() != null, PurchaseOrder::getLifecycleStatus,
                        query.getLifecycleStatus())
                .eq(query.getAuditStatus() != null, PurchaseOrder::getAuditStatus, query.getAuditStatus())
                .eq(query.getFulfillStatus() != null, PurchaseOrder::getFulfillStatus, query.getFulfillStatus())
                .eq(query.getDeviationLevel() != null, PurchaseOrder::getDeviationFlag, query.getDeviationLevel())
                .eq(query.getDeptId() != null, PurchaseOrder::getDeptId, query.getDeptId())
                .eq(query.getBuyerId() != null, PurchaseOrder::getBuyerId, query.getBuyerId())
                .ge(query.getStartDate() != null, PurchaseOrder::getOrderDate, query.getStartDate())
                .le(query.getEndDate() != null, PurchaseOrder::getOrderDate, query.getEndDate())
                .ge(query.getAmountGte() != null, PurchaseOrder::getTotalAmount, query.getAmountGte())
                .le(query.getAmountLte() != null, PurchaseOrder::getTotalAmount, query.getAmountLte())
                .orderByDesc(PurchaseOrder::getOrderDate)
                .orderByDesc(PurchaseOrder::getId);
        if (Boolean.TRUE.equals(query.getOverdue())) {
            // 超期未交付：交货期早于今天且履约状态仍是待交付或部分交付。
            // NULL 交货期在 SQL 比较中天然落空，不会误入超期集。
            wrapper.le(PurchaseOrder::getDeliveryDate, today.minusDays(1))
                    .in(PurchaseOrder::getFulfillStatus, FULFILL_PENDING, FULFILL_PARTIAL);
        }
        dataScopeService.applyDept(wrapper, PurchaseOrder::getDeptId, currentScope());
        Page<PurchaseOrder> result = orderMapper.selectPage(page, wrapper);
        List<PurchaseOrder> bills = result.getRecords();
        if (bills.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        return PageResult.of(assembleList(bills, today), result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    /** M3-1-1 详情：主表 + 明细行 + 审批历史 + 变更历史 + 交付流水。 */
    public OrderVO detail(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        return assembleBill(bill, LocalDate.now());
    }

    // ================= M3-1-2 新建订单 =================

    /** 新建草稿单。部门取当前用户部门（数据范围按部门过滤，建单人须可见）。 */
    @Transactional
    public Long create(OrderSaveRequest request) {
        if (request.getSupplierId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择供应商");
        }
        Supplier supplier = requireEnabledSupplier(request.getSupplierId());
        Long deptId = currentDeptId();
        validateDept(deptId);
        Integer settlementType = request.getSettlementType() == null
                ? SETTLEMENT_MONTHLY : request.getSettlementType();
        if (settlementType != SETTLEMENT_MONTHLY && settlementType != SETTLEMENT_ON_DELIVERY
                && settlementType != SETTLEMENT_PREPAID) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "结算方式不合法");
        }

        Long id = null;
        String orderNo = null;
        for (int attempt = 1; attempt <= NO_ATTEMPTS; attempt++) {
            PurchaseOrder bill = new PurchaseOrder();
            orderNo = billNoGenerator.next("bill_no_rule_order", orderMapper::maxSeqByPrefix);
            bill.setOrderNo(orderNo);
            bill.setSupplierId(supplier.getId());
            bill.setBuyerId(currentUserId());
            bill.setDeptId(deptId);
            bill.setOrderDate(request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now());
            bill.setDeliveryDate(request.getDeliveryDate());
            bill.setDeliveryPlace(request.getDeliveryPlace());
            bill.setSettlementType(settlementType);
            bill.setLifecycleStatus(LIFECYCLE_DRAFT);
            bill.setAuditStatus(AUDIT_PENDING);
            bill.setFulfillStatus(FULFILL_PENDING);
            bill.setTotalQty(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
            bill.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setNoTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setDeviationFlag(0);
            bill.setRemark(request.getRemark());
            try {
                orderMapper.insert(bill);
                id = bill.getId();
                break;
            } catch (DuplicateKeyException ex) {
                if (attempt == NO_ATTEMPTS) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "订单号生成冲突，请重试");
                }
            }
        }
        operationLogService.record("M3", "create", "purchase_order", id, orderNo,
                "新建采购订单草稿，供应商 " + supplier.getSupplierName());
        return id;
    }

    // ================= M3-1-3 由申请单生成订单 =================

    /**
     * 订单侧发起的转单入口：带入已审核申请单的明细与归属对象。
     * 生成逻辑复用 M2-4 转订单服务——已转数量的唯一写入点不变，
     * 同一供应商多行仍合并为一张订单（M2-4-2）。
     */
    @Transactional
    public TransferResultVO createFromRequest(FromRequestRequest request) {
        if (request.getRequestId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择来源申请单");
        }
        com.zufe.procurement.m2.request.dto.TransferRequest transfer =
                new com.zufe.procurement.m2.request.dto.TransferRequest();
        com.zufe.procurement.m2.request.dto.TransferRequest.TransferSource source =
                new com.zufe.procurement.m2.request.dto.TransferRequest.TransferSource();
        source.setRequestId(request.getRequestId());
        source.setItemIds(null);
        transfer.setSources(List.of(source));
        return transferService.transfer(transfer);
    }

    // ================= M3-1-4 编辑订单 =================

    /**
     * 编辑主表。仅草稿 / 已驳回可编辑——两者都表现为 lifecycle_status=1，
     * 由 audit_status=3 区分"已驳回"，不另设状态值（R1，与 M2 同构）。
     * 更换供应商时逐行校验供货范围，避免草稿期选错供应商、提交时才报错。
     */
    @Transactional
    public void update(Long id, OrderSaveRequest request) {
        PurchaseOrder bill = loadEditableBill(id);
        if (request.getSupplierId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择供应商");
        }
        Supplier supplier = requireEnabledSupplier(request.getSupplierId());
        Integer settlementType = request.getSettlementType() == null
                ? SETTLEMENT_MONTHLY : request.getSettlementType();
        if (settlementType != SETTLEMENT_MONTHLY && settlementType != SETTLEMENT_ON_DELIVERY
                && settlementType != SETTLEMENT_PREPAID) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "结算方式不合法");
        }
        if (!Objects.equals(bill.getSupplierId(), supplier.getId())) {
            for (PurchaseOrderItem item : orderItemMapper.selectByOrderId(id)) {
                Material material = materialMapper.selectById(item.getMaterialId());
                if (material != null) {
                    validateSupplierScope(supplier.getId(), material);
                }
            }
        }
        bill.setSupplierId(supplier.getId());
        if (request.getOrderDate() != null) {
            bill.setOrderDate(request.getOrderDate());
        }
        bill.setDeliveryDate(request.getDeliveryDate());
        bill.setDeliveryPlace(request.getDeliveryPlace());
        bill.setSettlementType(settlementType);
        bill.setRemark(request.getRemark());
        if (orderMapper.updateById(bill) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        operationLogService.record("M3", "update", "purchase_order", id, bill.getOrderNo(),
                "编辑采购订单主表信息");
    }

    // ================= M3-1-5 作废订单 =================

    /**
     * 作废：未发生到货的订单可作废（M3-1-5）。前置与反审同口径——
     * 无交付登记、无到货验收、无入库记录；审批中的订单同时失效待处理任务；
     * 作废后 lifecycle=5，未交付完的行置已关闭，不可再登记到货。
     */
    @Transactional
    public void voidOrder(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        if (bill.getLifecycleStatus() != LIFECYCLE_DRAFT && bill.getLifecycleStatus() != LIFECYCLE_SUBMITTED
                && bill.getLifecycleStatus() != LIFECYCLE_APPROVED) {
            throw new BizException(ResultCode.UNSUPPORTED, "已关闭或已作废的订单不能再次作废");
        }
        requireNoArrival(bill, "作废");
        if (bill.getLifecycleStatus() == LIFECYCLE_SUBMITTED) {
            approvalEngine.invalidateTasks(BILL_TYPE_ORDER, id);
        }
        closeRemainingItems(id);
        PurchaseOrder update = new PurchaseOrder();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_VOIDED);
        if (orderMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        recomputeFulfillStatus(id);
        operationLogService.record("M3", "void", "purchase_order", id, bill.getOrderNo(),
                "作废采购订单");
    }

    // ================= M3-1-6 打印 / 导出 =================

    /** 导出为 CSV（UTF-8 BOM，Excel 可直接打开），一行主表加多行明细的平表视图。 */
    public byte[] exportCsv(Long id) {
        OrderVO vo = detail(id);
        String[] headers = {"订单号", "下单日期", "供应商", "采购员", "部门", "交货期", "交货地点",
                "结算方式", "生命周期", "审批结果", "履约状态", "合计数量", "含税金额", "不含税金额",
                "税额", "超期天数", "行号", "物料编码", "物料名称", "规格", "数量", "单位",
                "含税单价", "税率", "行含税金额", "行交货期", "行交货地点", "已交付数量", "剩余数量",
                "行履约状态", "归属类型", "归属对象", "行备注"};
        List<String[]> rows = new ArrayList<>();
        for (OrderItemVO item : vo.getItems()) {
            rows.add(new String[]{
                    vo.getOrderNo(), String.valueOf(vo.getOrderDate()), vo.getSupplierName(),
                    vo.getBuyerName(), vo.getDeptName(),
                    vo.getDeliveryDate() == null ? "" : vo.getDeliveryDate(),
                    vo.getDeliveryPlace() == null ? "" : vo.getDeliveryPlace(),
                    vo.getSettlementTypeName(), vo.getLifecycleStatusName(), vo.getAuditStatusName(),
                    vo.getFulfillStatusName(),
                    vo.getTotalQty() == null ? "" : vo.getTotalQty().toPlainString(),
                    vo.getTotalAmount() == null ? "" : vo.getTotalAmount().toPlainString(),
                    vo.getNoTaxAmount() == null ? "" : vo.getNoTaxAmount().toPlainString(),
                    vo.getTaxAmount() == null ? "" : vo.getTaxAmount().toPlainString(),
                    String.valueOf(vo.getOverdueDays()),
                    String.valueOf(item.getItemNo()), item.getMaterialNo(), item.getMaterialName(),
                    item.getSpec() == null ? "" : item.getSpec(),
                    item.getQuantity() == null ? "" : item.getQuantity().toPlainString(),
                    item.getUnitName(),
                    item.getUnitPrice() == null ? "" : item.getUnitPrice().toPlainString(),
                    item.getTaxRate() == null ? "" : item.getTaxRate().toPlainString(),
                    item.getAmount() == null ? "" : item.getAmount().toPlainString(),
                    item.getDeliveryDate() == null ? "" : item.getDeliveryDate(),
                    item.getDeliveryPlace() == null ? "" : item.getDeliveryPlace(),
                    item.getDeliveredQty() == null ? "" : item.getDeliveredQty().toPlainString(),
                    item.getRemainingQty() == null ? "" : item.getRemainingQty().toPlainString(),
                    item.getItemFulfillStatusName(), ownerTypeName(item.getOwnerType()),
                    item.getOwnerName() == null ? "" : item.getOwnerName(),
                    item.getRemark() == null ? "" : item.getRemark()});
        }
        return CsvWriter.toCsv(headers, rows);
    }

    // ================= M3-2-1 新增明细行 =================

    /**
     * 新增明细行：草稿态。物料必须存在且启用；数量大于 0；含税单价不小于 0；
     * 供应商供货范围按物料或分类校验（M3-2-1）；行交货期缺省继承主表；
     * 金额服务层计算；行履约状态初始为待交付。
     */
    @Transactional
    public Long addItem(Long orderId, OrderItemSaveRequest request) {
        PurchaseOrder bill = loadEditableBill(orderId);
        PurchaseOrderItem item = buildItem(bill, request);
        item.setItemNo(orderItemMapper.maxItemNo(orderId) + 1);
        orderItemMapper.insert(item);
        recomputeTotals(orderId);
        recomputeFulfillStatus(orderId);
        priceDeviationService.recomputeOrderFlag(orderId);
        operationLogService.record("M3", "create", "purchase_order_item", item.getId(),
                bill.getOrderNo() + "-" + item.getItemNo(), "新增订单明细行，物料 " + request.getMaterialId());
        return item.getId();
    }

    // ================= M3-2-2 编辑 / 删除明细行 =================

    @Transactional
    public void updateItem(Long orderId, Long itemId, OrderItemSaveRequest request) {
        PurchaseOrder bill = loadEditableBill(orderId);
        PurchaseOrderItem item = orderItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getOrderId(), orderId)) {
            throw new BizException(ResultCode.NOT_FOUND, "明细行不存在");
        }
        PurchaseOrderItem updated = buildItem(bill, request);
        updated.setId(item.getId());
        updated.setItemNo(item.getItemNo());
        updated.setVersion(item.getVersion());
        updated.setDeliveredQty(item.getDeliveredQty());
        // 草稿态不会有交付数量，仍按守恒保留原值，防止并发旁路写入被覆盖
        updated.setItemFulfillStatus(item.getItemFulfillStatus());
        updated.setSourceRequestItemId(item.getSourceRequestItemId());
        // 偏离重算结果可能为 null（无生效参考价）：updateById 不写 null 字段，显式补写
        if (!Objects.equals(updated.getDeviationRate(), item.getDeviationRate())
                || !Objects.equals(updated.getDeviationLevel(), item.getDeviationLevel())) {
            orderItemMapper.update(null, new LambdaUpdateWrapper<PurchaseOrderItem>()
                    .eq(PurchaseOrderItem::getId, item.getId())
                    .set(PurchaseOrderItem::getDeviationRate, updated.getDeviationRate())
                    .set(PurchaseOrderItem::getDeviationLevel, updated.getDeviationLevel()));
        }
        if (orderItemMapper.updateById(updated) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "明细行已被变更，请刷新后重试");
        }
        recomputeTotals(orderId);
        recomputeFulfillStatus(orderId);
        priceDeviationService.recomputeOrderFlag(orderId);
        operationLogService.record("M3", "update", "purchase_order_item", itemId,
                bill.getOrderNo() + "-" + item.getItemNo(), "编辑订单明细行");
    }

    @Transactional
    public void deleteItem(Long orderId, Long itemId) {
        PurchaseOrder bill = loadEditableBill(orderId);
        PurchaseOrderItem item = orderItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getOrderId(), orderId)) {
            throw new BizException(ResultCode.NOT_FOUND, "明细行不存在");
        }
        if (item.getDeliveredQty() != null && item.getDeliveredQty().signum() > 0) {
            throw new BizException(ResultCode.UNSUPPORTED, "明细行已产生交付记录，不能删除");
        }
        // @TableLogic 字段经 updateById(实体) 会被 MP 忽略，逻辑删除必须走 delete(wrapper)
        if (orderItemMapper.delete(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getId, itemId)) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "明细行状态已变更，请刷新后重试");
        }
        recomputeTotals(orderId);
        recomputeFulfillStatus(orderId);
        priceDeviationService.recomputeOrderFlag(orderId);
        operationLogService.record("M3", "delete", "purchase_order_item", itemId,
                bill.getOrderNo() + "-" + item.getItemNo(), "删除订单明细行");
    }

    // ================= M3-2-4 按行关闭 =================

    /**
     * 按行关闭：已审核订单中部分交付的行，剩余数量不再要求交付（M3-2-4）。
     * 被关闭行置履约状态已关闭；主单履约状态按行重算——剩余行全部
     * 交付完或被关闭时，主单履约转为已关闭。
     */
    @Transactional
    public void closeItems(Long id, OrderItemCloseRequest request) {
        PurchaseOrder bill = loadApprovedBill(id);
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请至少选择一行关闭");
        }
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderId(id);
        Set<Long> closeIds = new HashSet<>(request.getItemIds());
        List<PurchaseOrderItem> closing = items.stream()
                .filter(item -> closeIds.contains(item.getId()))
                .filter(item -> item.getItemFulfillStatus() == FULFILL_PENDING
                        || item.getItemFulfillStatus() == FULFILL_PARTIAL)
                .collect(Collectors.toList());
        if (closing.size() != closeIds.size()) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "存在不可关闭的明细行（已全部交付、已关闭或不存在的行不能关闭）");
        }
        for (PurchaseOrderItem item : closing) {
            PurchaseOrderItem update = new PurchaseOrderItem();
            update.setId(item.getId());
            update.setVersion(item.getVersion());
            update.setItemFulfillStatus(FULFILL_CLOSED);
            if (orderItemMapper.updateById(update) <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "明细行状态已变更，请刷新后重试");
            }
        }
        recomputeFulfillStatus(id);
        operationLogService.record("M3", "close", "purchase_order_item", id, bill.getOrderNo(),
                "按行关闭 " + closing.size() + " 行，剩余数量不再要求交付");
    }

    // ================= M3-4-1 变更登记 =================

    /**
     * 变更登记：已审核订单的数量 / 单价 / 交期 / 地点 / 结算方式变更，
     * 逐字段比对，有变化的字段写一条变更历史（M3-4-1）。变更不触发
     * 重新审批——单据仍是已审核态，变更痕迹在详情页时间线呈现（M3-4-2）。
     * 行级约束：变更后数量不得小于已交付数量（入库事实不可回改）。
     */
    @Transactional
    public void change(Long id, OrderChangeRequest request) {
        PurchaseOrder bill = loadApprovedBill(id);
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "变更原因必填");
        }
        String reason = request.getReason().trim();
        int changes = 0;
        if (request.getItemId() != null) {
            changes += changeItemFields(bill, request, reason);
        } else {
            changes += changeHeaderFields(bill, request, reason);
        }
        if (changes == 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "没有检测到变更，请填写要修改的字段");
        }
        operationLogService.record("M3", "change", "purchase_order", id, bill.getOrderNo(),
                "订单变更登记 " + changes + " 项，原因：" + reason);
    }

    /** 行级变更：数量、含税单价、行交货期、行交货地点。 */
    private int changeItemFields(PurchaseOrder bill, OrderChangeRequest request, String reason) {
        PurchaseOrderItem item = orderItemMapper.selectById(request.getItemId());
        if (item == null || !Objects.equals(item.getOrderId(), bill.getId())) {
            throw new BizException(ResultCode.NOT_FOUND, "明细行不存在");
        }
        if (Objects.equals(item.getItemFulfillStatus(), FULFILL_CLOSED)) {
            throw new BizException(ResultCode.UNSUPPORTED, "已关闭的明细行不能变更");
        }
        BigDecimal delivered = item.getDeliveredQty() == null
                ? BigDecimal.ZERO : item.getDeliveredQty();
        int changes = 0;
        PurchaseOrderItem update = new PurchaseOrderItem();
        update.setId(item.getId());
        update.setVersion(item.getVersion());
        if (request.getQuantity() != null
                && request.getQuantity().compareTo(item.getQuantity()) != 0) {
            if (request.getQuantity().signum() <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "变更后数量必须大于 0");
            }
            if (request.getQuantity().compareTo(delivered) < 0) {
                throw new BizException(ResultCode.UNSUPPORTED,
                        "变更后数量不能小于已交付数量 " + delivered.toPlainString());
            }
            writeChangeLog(bill, item, "quantity",
                    item.getQuantity().toPlainString(), request.getQuantity().toPlainString(), reason);
            update.setQuantity(request.getQuantity());
            changes++;
        }
        if (request.getUnitPrice() != null
                && request.getUnitPrice().compareTo(item.getUnitPrice()) != 0) {
            if (request.getUnitPrice().signum() < 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "含税单价不能小于 0");
            }
            writeChangeLog(bill, item, "unit_price",
                    item.getUnitPrice().toPlainString(), request.getUnitPrice().toPlainString(), reason);
            update.setUnitPrice(request.getUnitPrice());
            changes++;
        }
        if (request.getDeliveryDate() != null
                && !Objects.equals(request.getDeliveryDate(), item.getDeliveryDate())) {
            writeChangeLog(bill, item, "delivery_date",
                    String.valueOf(item.getDeliveryDate()), String.valueOf(request.getDeliveryDate()), reason);
            update.setDeliveryDate(request.getDeliveryDate());
            changes++;
        }
        if (request.getDeliveryPlace() != null
                && !Objects.equals(request.getDeliveryPlace(), item.getDeliveryPlace())) {
            writeChangeLog(bill, item, "delivery_place",
                    String.valueOf(item.getDeliveryPlace()), request.getDeliveryPlace(), reason);
            update.setDeliveryPlace(request.getDeliveryPlace());
            changes++;
        }
        if (changes > 0) {
            if (update.getQuantity() != null || update.getUnitPrice() != null) {
                BigDecimal quantity = update.getQuantity() != null ? update.getQuantity() : item.getQuantity();
                BigDecimal unitPrice = update.getUnitPrice() != null ? update.getUnitPrice() : item.getUnitPrice();
                BigDecimal[] amounts = computeAmounts(quantity, unitPrice, item.getTaxRate());
                update.setAmount(amounts[0]);
                update.setNoTaxAmount(amounts[1]);
            }
            if (orderItemMapper.updateById(update) <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "明细行已被变更，请刷新后重试");
            }
            recomputeTotals(bill.getId());
        }
        return changes;
    }

    /** 整单级变更：交货期、交货地点、结算方式。 */
    private int changeHeaderFields(PurchaseOrder bill, OrderChangeRequest request, String reason) {
        int changes = 0;
        PurchaseOrder update = new PurchaseOrder();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        if (request.getDeliveryDate() != null
                && !Objects.equals(request.getDeliveryDate(), bill.getDeliveryDate())) {
            writeHeaderChangeLog(bill, "delivery_date",
                    String.valueOf(bill.getDeliveryDate()), String.valueOf(request.getDeliveryDate()), reason);
            update.setDeliveryDate(request.getDeliveryDate());
            changes++;
        }
        if (request.getDeliveryPlace() != null
                && !Objects.equals(request.getDeliveryPlace(), bill.getDeliveryPlace())) {
            writeHeaderChangeLog(bill, "delivery_place",
                    String.valueOf(bill.getDeliveryPlace()), request.getDeliveryPlace(), reason);
            update.setDeliveryPlace(request.getDeliveryPlace());
            changes++;
        }
        if (request.getSettlementType() != null
                && !Objects.equals(request.getSettlementType(), bill.getSettlementType())) {
            if (request.getSettlementType() != SETTLEMENT_MONTHLY
                    && request.getSettlementType() != SETTLEMENT_ON_DELIVERY
                    && request.getSettlementType() != SETTLEMENT_PREPAID) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "结算方式不合法");
            }
            writeHeaderChangeLog(bill, "settlement_type",
                    String.valueOf(bill.getSettlementType()),
                    String.valueOf(request.getSettlementType()), reason);
            update.setSettlementType(request.getSettlementType());
            changes++;
        }
        if (changes > 0 && orderMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        return changes;
    }

    // ================= M3-4-2 变更历史查看 =================

    /** 变更历史时间线，按变更时间正序（早变更在上）。 */
    public List<OrderChangeLogVO> changeLogs(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        List<OrderChangeLog> logs = changeLogMapper.selectByOrderId(id);
        if (logs.isEmpty()) {
            return List.of();
        }
        Map<Long, String> userNameById = userMapper.selectByIds(logs.stream()
                        .map(OrderChangeLog::getOperatorId).filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        List<OrderChangeLogVO> result = new ArrayList<>();
        for (OrderChangeLog log : logs) {
            OrderChangeLogVO vo = new OrderChangeLogVO();
            vo.setId(log.getId());
            vo.setOrderId(log.getOrderId());
            vo.setOrderNo(log.getOrderNo());
            vo.setOrderItemId(log.getOrderItemId());
            vo.setItemNo(log.getItemNo());
            vo.setChangeField(log.getChangeField());
            vo.setChangeFieldName(changeFieldName(log.getChangeField()));
            vo.setBeforeValue(log.getBeforeValue());
            vo.setAfterValue(log.getAfterValue());
            vo.setChangeReason(log.getChangeReason());
            vo.setOperatorName(userNameById.getOrDefault(log.getOperatorId(), "用户已删除"));
            vo.setChangeAt(log.getChangeAt() == null ? null
                    : log.getChangeAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            result.add(vo);
        }
        return result;
    }

    // ================= M3-3-1 提交审批 =================

    /**
     * 提交审批：草稿 → 已提交，按 M11-5-4 生成首节点审批任务（订单为会签）。
     * 完整性校验：至少一行明细、供应商启用、明细数量与单价合法。
     * 状态更新带乐观锁，并发提交只有一次成功。
     */
    @Transactional
    public void submit(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        if (Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_DRAFT)) {
            // 草稿（含驳回后回落）正常提交
        } else if (Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_SUBMITTED)
                && Objects.equals(bill.getAuditStatus(), AUDIT_PENDING)
                && !approvalEngine.hasPendingTask(BILL_TYPE_ORDER, id)) {
            // 反审后重新提交（M3-3-5）：已提交/未审核且原批次任务已全部处理完成，
            // 旧任务留历史，本次生成新批次任务；有 1 待处理任务时不允许重复提交
        } else if (Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_SUBMITTED)) {
            throw new BizException(ResultCode.UNSUPPORTED, "审批进行中，不能重复提交");
        } else {
            throw new BizException(ResultCode.UNSUPPORTED, "只有草稿或已驳回的订单可以编辑");
        }
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderId(id);
        if (items.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "至少需要一行明细才能提交审批");
        }
        for (PurchaseOrderItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "明细行 " + item.getItemNo() + " 数量必须大于 0");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "明细行 " + item.getItemNo() + " 含税单价不能小于 0");
            }
        }
        Supplier supplier = supplierMapper.selectById(bill.getSupplierId());
        if (supplier == null || !Objects.equals(supplier.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, "供应商已停用或不存在，不能提交审批");
        }
        recomputeTotals(id);
        PurchaseOrder update = new PurchaseOrder();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_SUBMITTED);
        if (orderMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        approvalEngine.generateFirstNodeTasks(BILL_TYPE_ORDER, bill.getId(), bill.getOrderNo());
        operationLogService.record("M3", "submit", "purchase_order", id, bill.getOrderNo(),
                "提交审批，明细 " + items.size() + " 行");
    }

    // ================= M3-3-2 审批通过 =================

    @Transactional
    public void approve(Long id) {
        PurchaseOrder bill = loadSubmittedBill(id);
        ApprovalTask task = currentPendingTask(bill);
        approvalEngine.process(task.getId(), currentUserId(), true, null,
                BILL_TYPE_ORDER, bill.getId(), bill.getOrderNo(), approvalHandler);
        operationLogService.record("M3", "approve", "purchase_order", id, bill.getOrderNo(),
                "审批通过（节点 " + task.getNodeSeq() + " " + task.getNodeName() + "）");
    }

    // ================= M3-3-3 审批驳回 =================

    @Transactional
    public void reject(Long id, OrderRejectRequest request) {
        PurchaseOrder bill = loadSubmittedBill(id);
        ApprovalTask task = currentPendingTask(bill);
        approvalEngine.process(task.getId(), currentUserId(), false, request.getOpinion(),
                BILL_TYPE_ORDER, bill.getId(), bill.getOrderNo(), approvalHandler);
        operationLogService.record("M3", "reject", "purchase_order", id, bill.getOrderNo(),
                "审批驳回（节点 " + task.getNodeSeq() + " " + task.getNodeName() + "）：" + request.getOpinion());
    }

    // ================= M3-3-4 撤回审批 =================

    /** 撤回：已提交且本批次无人处理时回到草稿，待处理任务全部失效（与 M2-3-4 同构）。 */
    @Transactional
    public void withdraw(Long id) {
        PurchaseOrder bill = loadSubmittedBill(id);
        if (!Objects.equals(bill.getBuyerId(), currentUserId())) {
            throw new BizException(ResultCode.NO_PERMISSION, "只有采购员可以撤回订单");
        }
        // 撤回基准：本次提交生成的任务区间（submit 内任务先插入、提交日志后写入）
        List<SysOperationLog> submits = operationLogMapper.selectList(
                new LambdaQueryWrapper<SysOperationLog>()
                        .eq(SysOperationLog::getModuleCode, "M3")
                        .eq(SysOperationLog::getAction, "submit")
                        .eq(SysOperationLog::getTargetId, id)
                        .orderByDesc(SysOperationLog::getId)
                        .last("LIMIT 2"));
        if (submits.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        long upper = submits.get(0).getId();
        long lower = submits.size() > 1 ? submits.get(1).getId() : 0L;
        if (approvalEngine.hasHandledInIdRange(BILL_TYPE_ORDER, id, lower, upper)) {
            throw new BizException(ResultCode.UNSUPPORTED, "已有审批处理记录，不能撤回");
        }
        PurchaseOrder update = new PurchaseOrder();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_DRAFT);
        if (orderMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        approvalEngine.invalidateTasks(BILL_TYPE_ORDER, id);
        operationLogService.record("M3", "withdraw", "purchase_order", id, bill.getOrderNo(),
                "撤回审批，回到草稿");
    }

    // ================= M3-3-5 反审 =================

    /**
     * 反审（撤销审核）：仅系统管理员可操作（权限点 m3:order:reaudit 只授给
     * 管理员），已审核且未发生任何到货事实的订单可回到已提交态，
     * 重新走审批。前置与作废同口径：无交付登记、无到货验收、无入库记录——
     * 不做级联作废，避免破坏库存唯一写入点（R2）与在途追溯。
     * 原审批任务保留为历史（与驳回重提同构），重新提交生成新批次任务；
     * 操作强制写审计日志（清单「需二次确认，写审计」的二次确认在前端）。
     */
    @Transactional
    public void reaudit(Long id, String reason) {
        PurchaseOrder bill = loadApprovedBill(id);
        requireNoArrival(bill, "反审");
        PurchaseOrder update = new PurchaseOrder();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_SUBMITTED);
        update.setAuditStatus(AUDIT_PENDING);
        if (orderMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        operationLogService.record("M3", "reaudit", "purchase_order", id, bill.getOrderNo(),
                "反审：已审核回到已提交，重新走审批"
                        + (StringUtils.hasText(reason) ? "；原因：" + reason.trim() : ""));
    }

    // ================= M3-3-6 关闭订单 =================

    /**
     * 整单关闭：已审核订单的剩余未交付部分终止（M3-3-6）。
     * 待交付/部分交付的行置已关闭，主单生命周期转已关闭，
     * 履约状态按行重算（通常为已关闭），关闭原因落 close_reason。
     */
    @Transactional
    public void close(Long id, OrderCloseRequest request) {
        PurchaseOrder bill = loadApprovedBill(id);
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "关闭原因必填");
        }
        closeRemainingItems(id);
        PurchaseOrder update = new PurchaseOrder();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_CLOSED);
        update.setCloseReason(request.getReason().trim());
        if (orderMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        recomputeFulfillStatus(id);
        operationLogService.record("M3", "close", "purchase_order", id, bill.getOrderNo(),
                "整单关闭，剩余未交付部分终止，原因：" + request.getReason().trim());
    }

    // ================= M3-5-1 交付进度登记 =================

    /**
     * 交付进度登记：已审核订单按行登记本次交付数量，只写流水
     * （delivery_progress），不改 delivered_qty 与履约状态——
     * 已交付数量的唯一写入点是入库单确认（R2，轮次 7）。
     * 累计登记数量不得超过订单数量（跟单登记同样守恒）。
     */
    @Transactional
    public Long registerDelivery(Long id, DeliveryRegisterRequest request) {
        PurchaseOrder bill = loadApprovedBill(id);
        if (request.getOrderItemId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择要登记的明细行");
        }
        if (request.getDeliveryQty() == null || request.getDeliveryQty().signum() <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "本次交付数量必须大于 0");
        }
        PurchaseOrderItem item = orderItemMapper.selectById(request.getOrderItemId());
        if (item == null || !Objects.equals(item.getOrderId(), id)) {
            throw new BizException(ResultCode.NOT_FOUND, "明细行不存在");
        }
        if (item.getItemFulfillStatus() != FULFILL_PENDING
                && item.getItemFulfillStatus() != FULFILL_PARTIAL) {
            throw new BizException(ResultCode.UNSUPPORTED, "已全部交付或已关闭的行不能登记交付");
        }
        BigDecimal accumulated = deliveryMapper.sumQtyByOrderItem(item.getId())
                .add(request.getDeliveryQty());
        if (accumulated.compareTo(item.getQuantity()) > 0) {
            throw new BizException(ResultCode.UNSUPPORTED,
                    "累计登记数量 " + accumulated.toPlainString()
                            + " 超过订单数量 " + item.getQuantity().toPlainString());
        }
        DeliveryProgress progress = new DeliveryProgress();
        progress.setOrderId(bill.getId());
        progress.setOrderNo(bill.getOrderNo());
        progress.setOrderItemId(item.getId());
        progress.setItemNo(item.getItemNo());
        progress.setMaterialId(item.getMaterialId());
        progress.setDeliveryQty(request.getDeliveryQty());
        progress.setDeliveryDate(request.getDeliveryDate() != null ? request.getDeliveryDate() : LocalDate.now());
        progress.setBatchNo(request.getBatchNo());
        progress.setRemark(request.getRemark());
        progress.setOperatorId(currentUserId());
        deliveryMapper.insert(progress);
        operationLogService.record("M3", "create", "delivery_progress", progress.getId(),
                bill.getOrderNo() + "-" + item.getItemNo(),
                "交付进度登记 " + request.getDeliveryQty().toPlainString());
        return progress.getId();
    }

    /** M3-5-1 交付流水查询，按交付日期倒序（最近登记在上）。 */
    public List<DeliveryRecordVO> deliveryRecords(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        List<DeliveryProgress> records = deliveryMapper.selectByOrderId(id);
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, String> userNameById = userMapper.selectByIds(records.stream()
                        .map(DeliveryProgress::getOperatorId).filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        List<Long> materialIds = records.stream()
                .map(DeliveryProgress::getMaterialId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, String> materialNameById = materialIds.isEmpty() ? new HashMap<>()
                : materialMapper.selectByIds(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Material::getMaterialName, (a, b) -> a));
        List<DeliveryRecordVO> result = new ArrayList<>();
        for (DeliveryProgress record : records) {
            DeliveryRecordVO vo = new DeliveryRecordVO();
            vo.setId(record.getId());
            vo.setOrderId(record.getOrderId());
            vo.setOrderNo(record.getOrderNo());
            vo.setOrderItemId(record.getOrderItemId());
            vo.setItemNo(record.getItemNo());
            vo.setMaterialName(materialNameById.getOrDefault(record.getMaterialId(), ""));
            vo.setDeliveryQty(record.getDeliveryQty());
            vo.setDeliveryDate(record.getDeliveryDate() == null ? null : record.getDeliveryDate().toString());
            vo.setBatchNo(record.getBatchNo());
            vo.setRemark(record.getRemark());
            vo.setOperatorName(userNameById.getOrDefault(record.getOperatorId(), "用户已删除"));
            vo.setCreatedAt(record.getCreatedAt() == null ? null
                    : record.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            result.add(vo);
        }
        return result;
    }

    // ================= M3-5-5 交付汇总视图 =================

    /**
     * 订单交付汇总：按供应商汇总已交付 / 未交付数量与金额（M3-5-5）。
     * 口径：只统计已审核与已关闭的订单（草稿与审批中没有交付事实）；
     * 已交付金额按行数量比例折算（amount × delivered ÷ quantity），
     * 汇总仅供经营跟单，不作为结算依据。数据范围按部门注入。
     */
    public List<DeliverySummaryVO> deliverySummary(LocalDate startDate, LocalDate endDate,
                                                   Long supplierId) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .in(PurchaseOrder::getLifecycleStatus, LIFECYCLE_APPROVED, LIFECYCLE_CLOSED)
                .ge(startDate != null, PurchaseOrder::getOrderDate, startDate)
                .le(endDate != null, PurchaseOrder::getOrderDate, endDate)
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .orderByAsc(PurchaseOrder::getId);
        dataScopeService.applyDept(wrapper, PurchaseOrder::getDeptId, currentScope());
        List<PurchaseOrder> orders = orderMapper.selectList(wrapper);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderIds(orderIds);
        Map<Long, List<PurchaseOrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getOrderId));

        Map<Long, DeliverySummaryVO> bySupplier = new LinkedHashMap<>();
        Map<Long, String> supplierNameById = new HashMap<>();
        for (PurchaseOrder order : orders) {
            DeliverySummaryVO row = bySupplier.computeIfAbsent(order.getSupplierId(), k -> {
                DeliverySummaryVO vo = new DeliverySummaryVO();
                vo.setSupplierId(k);
                vo.setOrderCount(0);
                vo.setTotalQty(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                vo.setDeliveredQty(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                vo.setRemainingQty(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                vo.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                vo.setDeliveredAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                vo.setRemainingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                return vo;
            });
            row.setOrderCount(row.getOrderCount() + 1);
            for (PurchaseOrderItem item : itemsByOrder.getOrDefault(order.getId(), List.of())) {
                BigDecimal delivered = item.getDeliveredQty() == null
                        ? BigDecimal.ZERO : item.getDeliveredQty();
                BigDecimal remaining = item.getQuantity().subtract(delivered).max(BigDecimal.ZERO);
                row.setTotalQty(row.getTotalQty().add(item.getQuantity()));
                row.setDeliveredQty(row.getDeliveredQty().add(delivered));
                row.setRemainingQty(row.getRemainingQty().add(remaining));
                row.setTotalAmount(row.getTotalAmount().add(nullToZero(item.getAmount())));
                row.setDeliveredAmount(row.getDeliveredAmount().add(
                        proportionalAmount(item.getAmount(), item.getQuantity(), delivered)));
                row.setRemainingAmount(row.getRemainingAmount().add(
                        proportionalAmount(item.getAmount(), item.getQuantity(), remaining)));
            }
        }
        List<DeliverySummaryVO> result = new ArrayList<>(bySupplier.values());
        Set<Long> supplierIds = result.stream().map(DeliverySummaryVO::getSupplierId)
                .collect(Collectors.toSet());
        if (!supplierIds.isEmpty()) {
            supplierNameById = supplierMapper.selectByIds(supplierIds).stream()
                    .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName, (a, b) -> a));
        }
        for (DeliverySummaryVO row : result) {
            row.setSupplierName(supplierNameById.getOrDefault(row.getSupplierId(), "供应商已删除"));
        }
        return result;
    }

    // ================= M3-1-1 订单溯源 =================

    /**
     * 来源申请追溯：订单由申请单生成时，按来源申请单id 取申请单详情
     * （主表、明细、审批历史）。手工创建的订单无来源，返回 NoData。
     */
    public RequestVO trace(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        if (bill.getSourceRequestId() == null) {
            throw new BizException(ResultCode.NO_DATA, "该订单为手工创建，无来源申请单");
        }
        return requestService.detail(bill.getSourceRequestId());
    }

    /** M3-2-5 归属对象下拉数据源，与 M2 共用同一份档案。 */
    public OwnerOptionVO ownerOptions() {
        return requestService.ownerOptions();
    }

    // ================= M3-3-2 订单待办（P9） =================

    /**
     * 订单待办。先取当前用户的待处理任务，再按单据部门数据范围分页查询
     * 订单（与 M2-3-8 同构）：范围外单据的待办不出现。
     */
    public PageResult<OrderTodoVO> todoPage(long pageNo, long pageSize) {
        List<ApprovalTask> tasks = approvalEngine.pendingTasksForApprover(currentUserId(), BILL_TYPE_ORDER);
        if (tasks.isEmpty()) {
            return PageResult.of(List.of(), 0, 1, pageSize);
        }
        Set<Long> billIds = tasks.stream().map(ApprovalTask::getBillId).collect(Collectors.toSet());
        Page<PurchaseOrder> page = new Page<>(Math.max(pageNo, 1),
                Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<PurchaseOrder>()
                .in(PurchaseOrder::getId, billIds)
                .eq(PurchaseOrder::getLifecycleStatus, LIFECYCLE_SUBMITTED)
                .orderByDesc(PurchaseOrder::getOrderDate)
                .orderByDesc(PurchaseOrder::getId);
        dataScopeService.applyDept(wrapper, PurchaseOrder::getDeptId, currentScope());
        Page<PurchaseOrder> result = orderMapper.selectPage(page, wrapper);
        Map<Long, ApprovalTask> taskByBill = tasks.stream()
                .collect(Collectors.toMap(ApprovalTask::getBillId, Function.identity(), (a, b) -> a));
        List<Long> pageBillIds = result.getRecords().stream()
                .map(PurchaseOrder::getId).collect(Collectors.toList());
        Map<Long, Integer> itemCountByBill = pageBillIds.isEmpty() ? new HashMap<>()
                : orderItemMapper.selectByOrderIds(pageBillIds).stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getOrderId,
                        Collectors.reducing(0, item -> 1, Integer::sum)));
        Map<Long, String> supplierNameById = new HashMap<>();
        Map<Long, String> buyerNameById = new HashMap<>();
        if (!result.getRecords().isEmpty()) {
            supplierNameById = supplierMapper.selectByIds(result.getRecords().stream()
                            .map(PurchaseOrder::getSupplierId).distinct().collect(Collectors.toList())).stream()
                    .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName, (a, b) -> a));
            buyerNameById = userMapper.selectByIds(result.getRecords().stream()
                            .map(PurchaseOrder::getBuyerId).filter(Objects::nonNull).distinct()
                            .collect(Collectors.toList())).stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        }
        LocalDate today = LocalDate.now();
        List<OrderTodoVO> records = new ArrayList<>();
        for (PurchaseOrder bill : result.getRecords()) {
            ApprovalTask task = taskByBill.get(bill.getId());
            if (task == null) {
                continue;
            }
            OrderTodoVO vo = new OrderTodoVO();
            vo.setTaskId(task.getId());
            vo.setTaskNo(task.getTaskNo());
            vo.setBillId(bill.getId());
            vo.setBillNo(bill.getOrderNo());
            vo.setNodeSeq(task.getNodeSeq());
            vo.setNodeName(task.getNodeName());
            vo.setSignType(task.getSignType());
            vo.setSignTypeName(Objects.equals(task.getSignType(), 2) ? "会签" : "或签");
            vo.setCreatedTime(task.getCreatedAt() == null ? null
                    : task.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            vo.setSupplierName(supplierNameById.getOrDefault(bill.getSupplierId(), ""));
            vo.setBuyerName(buyerNameById.getOrDefault(bill.getBuyerId(), ""));
            vo.setOrderDate(bill.getOrderDate() == null ? null : bill.getOrderDate().toString());
            vo.setDeliveryDate(bill.getDeliveryDate() == null ? null : bill.getDeliveryDate().toString());
            vo.setTotalAmount(bill.getTotalAmount());
            vo.setItemCount(itemCountByBill.getOrDefault(bill.getId(), 0));
            vo.setOverdueDays(overdueDays(bill.getDeliveryDate(), bill.getFulfillStatus(), today));
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ================= 审批结果回写（M11-5-5 的单据侧实现） =================

    /**
     * 审批引擎回调：末节点全部通过时置"已审核 + 审批通过"，
     * 单据进入可交付状态；驳回时生命周期回落草稿、审批结果置驳回
     * （与 M2 同构，R1）。与引擎在同一事务内执行。
     */
    private final ApprovalEngineService.BillApprovalHandler approvalHandler =
            new ApprovalEngineService.BillApprovalHandler() {
                @Override
                public void onPassed(Long billId) {
                    PurchaseOrder bill = orderMapper.selectById(billId);
                    if (bill == null) {
                        return;
                    }
                    PurchaseOrder update = new PurchaseOrder();
                    update.setId(billId);
                    update.setVersion(bill.getVersion());
                    update.setLifecycleStatus(LIFECYCLE_APPROVED);
                    update.setAuditStatus(AUDIT_PASSED);
                    if (orderMapper.updateById(update) <= 0) {
                        throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
                    }
                }

                @Override
                public void onRejected(Long billId) {
                    PurchaseOrder bill = orderMapper.selectById(billId);
                    if (bill == null) {
                        return;
                    }
                    PurchaseOrder update = new PurchaseOrder();
                    update.setId(billId);
                    update.setVersion(bill.getVersion());
                    update.setLifecycleStatus(LIFECYCLE_DRAFT);
                    update.setAuditStatus(AUDIT_REJECTED);
                    if (orderMapper.updateById(update) <= 0) {
                        throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
                    }
                }
            };

    // ================= 私有：单据加载与状态校验 =================

    /** 可编辑单据：lifecycle=1（草稿或已驳回）且在数据范围内。 */
    private PurchaseOrder loadEditableBill(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        if (!Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_DRAFT)) {
            throw new BizException(ResultCode.UNSUPPORTED, "只有草稿或已驳回的订单可以编辑");
        }
        return bill;
    }

    /** 审批中单据：lifecycle=2 且在数据范围内。 */
    private PurchaseOrder loadSubmittedBill(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        if (!Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_SUBMITTED)) {
            throw new BizException(ResultCode.UNSUPPORTED, "订单不在审批中");
        }
        return bill;
    }

    /** 已审核单据：lifecycle=3 且在数据范围内。 */
    private PurchaseOrder loadApprovedBill(Long id) {
        PurchaseOrder bill = orderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        requireBillInScope(bill);
        if (!Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_APPROVED)) {
            throw new BizException(ResultCode.UNSUPPORTED, "只有已审核的订单可以执行该操作");
        }
        return bill;
    }

    /** 当前用户在审批中单据上的待处理任务；没有则区分"已处理过"与"非审批人"。 */
    private ApprovalTask currentPendingTask(PurchaseOrder bill) {
        Long userId = currentUserId();
        ApprovalTask pending = approvalEngine.pendingTaskForApprover(BILL_TYPE_ORDER, bill.getId(), userId);
        if (pending != null) {
            return pending;
        }
        if (approvalEngine.hasHandledByApprover(BILL_TYPE_ORDER, bill.getId(), userId)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "该单据您已处理过，请刷新后重试");
        }
        throw new BizException(ResultCode.NO_PERMISSION, "当前用户不是该单据当前节点的审批人");
    }

    private void requireBillInScope(PurchaseOrder bill) {
        ScopeCondition scope = currentScope();
        if (scope.isAll()) {
            return;
        }
        if (!scope.hasDept() || !scope.getDeptIds().contains(bill.getDeptId())) {
            throw new BizException(ResultCode.NO_PERMISSION, "单据不在您的数据范围内");
        }
    }

    /**
     * 反审与作废的共用前置：订单未发生任何到货事实。
     * 交付登记、到货验收、入库三类记录任一存在即禁止——
     * 入库是库存唯一写入点（R2），级联作废会破坏库存流水与追溯，
     * 故口径是禁止而非级联（清单 M3-1-5、M3-3-5 的前置表述一致）。
     */
    private void requireNoArrival(PurchaseOrder bill, String action) {
        if (deliveryMapper.countByOrderId(bill.getId()) > 0) {
            throw new BizException(ResultCode.UNSUPPORTED,
                    "订单已有交付进度登记，不能" + action + "（无到货事实才允许）");
        }
        if (arrivalMapper.countByOrderId(bill.getId()) > 0) {
            throw new BizException(ResultCode.UNSUPPORTED,
                    "订单已有到货验收记录，不能" + action + "（无到货事实才允许）");
        }
        if (inboundMapper.countByOrderId(bill.getId()) > 0) {
            throw new BizException(ResultCode.UNSUPPORTED,
                    "订单已有入库记录，不能" + action + "（无到货事实才允许）");
        }
    }

    private ScopeCondition currentScope() {
        CurrentUser user = AuthContext.get();
        return user == null ? new ScopeCondition() : user.getScope();
    }

    private Long currentUserId() {
        return AuthContext.getOrThrow().getUserId();
    }

    private Long currentDeptId() {
        CurrentUser user = AuthContext.get();
        if (user == null || user.getDeptId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "当前用户未配置部门，不能建单");
        }
        return user.getDeptId();
    }

    // ================= 私有：明细行构建与校验 =================

    /**
     * 构建明细行实体并逐项校验（M3-2-1、M3-2-3、M3-2-5）：
     * 物料必须存在且启用；数量大于 0；含税单价不小于 0；
     * 供应商供货范围按物料或分类校验；归属对象按类型校验；
     * 行交货期与交货地点缺省继承主表；金额服务层计算。
     */
    private PurchaseOrderItem buildItem(PurchaseOrder bill, OrderItemSaveRequest request) {
        if (request.getMaterialId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择物料");
        }
        Material material = materialMapper.selectById(request.getMaterialId());
        if (material == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物料不存在");
        }
        if (!Objects.equals(material.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, "物料已停用，不能用于新订单");
        }
        if (request.getQuantity() == null || request.getQuantity().signum() <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "订单数量必须大于 0");
        }
        if (request.getUnitPrice() == null || request.getUnitPrice().signum() < 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "含税单价不能小于 0");
        }
        validateSupplierScope(bill.getSupplierId(), material);
        BigDecimal taxRate = request.getTaxRate() == null ? DEFAULT_TAX_RATE : request.getTaxRate();
        if (taxRate.signum() < 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "税率不能小于 0");
        }
        int ownerType = request.getOwnerType() == null ? OWNER_PUBLIC : request.getOwnerType();
        Long ownerId = null;
        String ownerNo = null;
        if (ownerType != OWNER_PUBLIC || request.getOwnerId() != null) {
            requirePermission(PERM_ITEM_OWNER);
        }
        if (ownerType != OWNER_PUBLIC) {
            if (request.getOwnerId() == null) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "项目或工单归属必须选择归属对象");
            }
            OwnerObject owner = ownerObjectMapper.selectById(request.getOwnerId());
            if (owner == null || !Objects.equals(owner.getStatus(), 1)) {
                throw new BizException(ResultCode.NOT_FOUND, "归属对象不存在或已停用");
            }
            if (!Objects.equals(owner.getOwnerType(), ownerType)) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "归属对象类型与所选类型不一致");
            }
            ownerId = owner.getId();
            ownerNo = owner.getOwnerNo();
        }

        BigDecimal[] amounts = computeAmounts(request.getQuantity(), request.getUnitPrice(), taxRate);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setOrderId(bill.getId());
        item.setMaterialId(material.getId());
        item.setQuantity(request.getQuantity());
        item.setUnitId(material.getUnitId());
        item.setUnitPrice(request.getUnitPrice());
        item.setTaxRate(taxRate);
        item.setAmount(amounts[0]);
        item.setNoTaxAmount(amounts[1]);
        item.setDeliveryDate(request.getDeliveryDate());
        item.setDeliveryPlace(request.getDeliveryPlace());
        item.setDeliveredQty(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        item.setOwnerType(ownerType);
        item.setOwnerId(ownerId);
        item.setOwnerNo(ownerNo);
        item.setItemFulfillStatus(FULFILL_PENDING);
        // M3-6 偏离判定：按订单创建日选用当时生效参考价计算（无参考价不判定）
        priceDeviationService.applyToItem(item, bill);
        item.setRemark(request.getRemark());
        return item;
    }

    /**
     * 校验供应商启用与供货范围（M3-2-1）。口径与 M2-2-3 一致：
     * 供应商配了范围行时，物料本身或物料所属分类必须命中其一；
     * 未配范围行视为不限。
     */
    private void validateSupplierScope(Long supplierId, Material material) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        if (!Objects.equals(supplier.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, "供应商已停用，不能用于新订单");
        }
        List<SupplierScope> scopes = scopeMapper.selectBySupplierId(supplierId);
        if (!scopes.isEmpty()) {
            boolean covered = scopes.stream().anyMatch(scope ->
                    (Objects.equals(scope.getScopeType(), 1) && Objects.equals(scope.getScopeId(), material.getId()))
                            || (Objects.equals(scope.getScopeType(), 2)
                            && Objects.equals(scope.getScopeId(), material.getCategoryId())));
            if (!covered) {
                throw new BizException(ResultCode.UNSUPPORTED,
                        "供应商「" + supplier.getSupplierName() + "」的供货范围不含物料 "
                                + material.getMaterialNo());
            }
        }
    }

    private Supplier requireEnabledSupplier(Long supplierId) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        if (!Objects.equals(supplier.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, "供应商已停用，不能建单");
        }
        return supplier;
    }

    private void validateDept(Long deptId) {
        if (deptId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "采购部门不能为空");
        }
        DictItem dept = dictItemMapper.selectById(deptId);
        if (dept == null || !Objects.equals(dept.getStatus(), 1)) {
            throw new BizException(ResultCode.NOT_FOUND, "采购部门不存在或已停用");
        }
    }

    private void requirePermission(String permCode) {
        CurrentUser user = AuthContext.get();
        if (user == null || !user.hasPermission(permCode)) {
            throw new BizException(ResultCode.NO_PERMISSION, "当前用户无权执行该操作");
        }
    }

    // ================= 私有：金额、履约与超期计算 =================

    /**
     * 行金额计算：含税金额 = 数量 × 单价（HALF_UP，2 位）；
     * 不含税金额 = 含税金额 ÷ (1 + 税率)（HALF_UP，2 位）。
     * 返回 [含税金额, 不含税金额]。
     */
    private BigDecimal[] computeAmounts(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {
        BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal noTax = amount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{amount, noTax};
    }

    /** 重算主表合计：数量、含税金额、不含税金额累加活跃行；税额 = 含税 − 不含税。 */
    private void recomputeTotals(Long orderId) {
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderId(orderId);
        BigDecimal totalQty = items.stream()
                .map(PurchaseOrderItem::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalAmount = items.stream()
                .map(PurchaseOrderItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal noTaxAmount = items.stream()
                .map(PurchaseOrderItem::getNoTaxAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        PurchaseOrder update = new PurchaseOrder();
        update.setId(orderId);
        update.setTotalQty(totalQty);
        update.setTotalAmount(totalAmount);
        update.setNoTaxAmount(noTaxAmount);
        update.setTaxAmount(totalAmount.subtract(noTaxAmount));
        orderMapper.updateById(update);
    }

    /**
     * 回写订单行已交付数量并重算履约（M5-1-3 入库确认 / M5-1-4 作废冲减调用，R2 后半）。
     * delivered_qty 的唯一写入点：入库单确认加、作废冲减退；
     * 行级履约随 delivered 重算（0 → 待交付；达数量 → 全部交付；之间 → 部分交付），
     * 已全部交付/已关闭的行不动（那种行本就不该再有入库，配额闸门在外层挡着）。
     *
     * @param orderItemId 订单明细行id，null 或不存在时静默跳过（手工入库单无订单行）
     * @param delta       正=确认入库，负=作废冲减
     */
    public void applyDeliveredQty(Long orderItemId, BigDecimal delta) {
        if (orderItemId == null || delta == null || delta.signum() == 0) {
            return;
        }
        PurchaseOrderItem item = orderItemMapper.selectById(orderItemId);
        if (item == null) {
            return;
        }
        BigDecimal delivered = item.getDeliveredQty() == null ? BigDecimal.ZERO : item.getDeliveredQty();
        BigDecimal after = delivered.add(delta).setScale(4, RoundingMode.HALF_UP);
        if (after.signum() < 0) {
            throw new BizException(ResultCode.UNSUPPORTED,
                    "冲减后已交付数量不能为负（当前 " + delivered.toPlainString() + "）");
        }
        PurchaseOrderItem update = new PurchaseOrderItem();
        update.setId(item.getId());
        update.setVersion(item.getVersion());
        update.setDeliveredQty(after);
        if (item.getItemFulfillStatus() != null && item.getItemFulfillStatus() != FULFILL_CLOSED) {
            int fulfill;
            if (after.signum() == 0) {
                fulfill = FULFILL_PENDING;
            } else if (after.compareTo(item.getQuantity()) >= 0) {
                fulfill = FULFILL_FULL;
            } else {
                fulfill = FULFILL_PARTIAL;
            }
            update.setItemFulfillStatus(fulfill);
        }
        int rows = orderItemMapper.updateById(update);
        if (rows == 0) {
            throw new BizException(ResultCode.UNSUPPORTED, "订单行已被并发修改，请刷新后重试");
        }
        recomputeFulfillStatus(item.getOrderId());
    }

    /**
     * 履约状态重算（M3-5-2 / M3-5-3 的判定入口）：按明细行履约状态汇总——
     * 有待交付行则主单待交付；否则有部分交付行则部分交付；
     * 全部行交付完则全部交付；剩余行均被关闭（含全部关闭）则已关闭。
     * 由明细行重算，不由人工选择（DDL 口径）。
     * 轮次 7 起对 M5 公开（入库确认/作废冲减后调用）。
     */
    public void recomputeFulfillStatus(Long orderId) {
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderId(orderId);
        int fulfill;
        if (items.isEmpty()) {
            fulfill = FULFILL_PENDING;
        } else if (items.stream().anyMatch(i -> Objects.equals(i.getItemFulfillStatus(), FULFILL_PENDING))) {
            fulfill = FULFILL_PENDING;
        } else if (items.stream().anyMatch(i -> Objects.equals(i.getItemFulfillStatus(), FULFILL_PARTIAL))) {
            fulfill = FULFILL_PARTIAL;
        } else if (items.stream().allMatch(i -> Objects.equals(i.getItemFulfillStatus(), FULFILL_FULL))) {
            fulfill = FULFILL_FULL;
        } else {
            fulfill = FULFILL_CLOSED;
        }
        PurchaseOrder update = new PurchaseOrder();
        update.setId(orderId);
        update.setFulfillStatus(fulfill);
        orderMapper.updateById(update);
    }

    /**
     * 超期天数（M3-5-4）：交货期早于当前日期且未交付完时返回相差天数，
     * 否则返回 0。当前日期由服务层取，不信任客户端时间。
     */
    private Integer overdueDays(LocalDate deliveryDate, Integer fulfillStatus, LocalDate today) {
        if (deliveryDate == null || today == null) {
            return 0;
        }
        if (!Objects.equals(fulfillStatus, FULFILL_PENDING) && !Objects.equals(fulfillStatus, FULFILL_PARTIAL)) {
            return 0;
        }
        if (!deliveryDate.isBefore(today)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(deliveryDate, today);
    }

    /** 关闭单据下所有未交付完的行（整单关闭与作废共用）。 */
    private void closeRemainingItems(Long orderId) {
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (PurchaseOrderItem item : items) {
            if (item.getItemFulfillStatus() == FULFILL_PENDING
                    || item.getItemFulfillStatus() == FULFILL_PARTIAL) {
                PurchaseOrderItem update = new PurchaseOrderItem();
                update.setId(item.getId());
                update.setVersion(item.getVersion());
                update.setItemFulfillStatus(FULFILL_CLOSED);
                orderItemMapper.updateById(update);
            }
        }
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
    }

    /** 按行数量比例折算金额：amount × part ÷ quantity，数量为 0 时记 0。 */
    private BigDecimal proportionalAmount(BigDecimal amount, BigDecimal quantity, BigDecimal part) {
        if (amount == null || quantity == null || quantity.signum() == 0 || part == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(part).divide(quantity, 2, RoundingMode.HALF_UP);
    }

    // ================= 私有：变更痕迹 =================

    private void writeChangeLog(PurchaseOrder bill, PurchaseOrderItem item, String field,
                                String before, String after, String reason) {
        OrderChangeLog log = new OrderChangeLog();
        log.setOrderId(bill.getId());
        log.setOrderNo(bill.getOrderNo());
        log.setOrderItemId(item.getId());
        log.setItemNo(item.getItemNo());
        log.setChangeField(field);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setChangeReason(reason);
        log.setOperatorId(currentUserId());
        changeLogMapper.insert(log);
    }

    private void writeHeaderChangeLog(PurchaseOrder bill, String field,
                                      String before, String after, String reason) {
        OrderChangeLog log = new OrderChangeLog();
        log.setOrderId(bill.getId());
        log.setOrderNo(bill.getOrderNo());
        log.setOrderItemId(null);
        log.setItemNo(null);
        log.setChangeField(field);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setChangeReason(reason);
        log.setOperatorId(currentUserId());
        changeLogMapper.insert(log);
    }

    // ================= 私有：视图装配 =================

    private List<OrderVO> assembleList(List<PurchaseOrder> bills, LocalDate today) {
        List<Long> orderIds = bills.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderIds(orderIds);
        Map<Long, List<PurchaseOrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getOrderId));
        List<Long> supplierIds = bills.stream().map(PurchaseOrder::getSupplierId).distinct()
                .collect(Collectors.toList());
        List<Long> userIds = bills.stream().map(PurchaseOrder::getBuyerId).filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        List<Long> deptIds = bills.stream().map(PurchaseOrder::getDeptId).filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        Map<Long, String> supplierNameById = supplierIds.isEmpty() ? new HashMap<>()
                : supplierMapper.selectByIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName, (a, b) -> a));
        Map<Long, String> userNameById = userIds.isEmpty() ? new HashMap<>()
                : userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> deptNameById = deptIds.isEmpty() ? new HashMap<>()
                : dictItemMapper.selectByIds(deptIds).stream()
                .collect(Collectors.toMap(DictItem::getId, DictItem::getItemName, (a, b) -> a));
        List<OrderVO> result = new ArrayList<>();
        for (PurchaseOrder bill : bills) {
            OrderVO vo = toHeaderVO(bill, today, supplierNameById, userNameById, deptNameById);
            vo.setItems(enrichItems(itemsByOrder.getOrDefault(bill.getId(), List.of()), bill, today));
            result.add(vo);
        }
        return result;
    }

    private OrderVO assembleBill(PurchaseOrder bill, LocalDate today) {
        OrderVO vo = toHeaderVO(bill, today, null, null, null);
        vo.setItems(enrichItems(orderItemMapper.selectByOrderId(bill.getId()), bill, today));
        vo.setApprovalHistory(approvalEngine.history(BILL_TYPE_ORDER, bill.getId()));
        vo.setChangeLogs(changeLogs(bill.getId()));
        vo.setDeliveryRecords(deliveryRecords(bill.getId()));
        return vo;
    }

    private OrderVO toHeaderVO(PurchaseOrder bill, LocalDate today,
                               Map<Long, String> supplierNameById,
                               Map<Long, String> userNameById,
                               Map<Long, String> deptNameById) {
        OrderVO vo = new OrderVO();
        vo.setId(bill.getId());
        vo.setOrderNo(bill.getOrderNo());
        vo.setSourceRequestId(bill.getSourceRequestId());
        vo.setSourceRequestNo(bill.getSourceRequestNo());
        vo.setSupplierId(bill.getSupplierId());
        vo.setSupplierName(lookupName(supplierNameById, bill.getSupplierId(),
                () -> supplierMapper.selectById(bill.getSupplierId()),
                Supplier::getSupplierName));
        vo.setBuyerId(bill.getBuyerId());
        vo.setBuyerName(lookupName(userNameById, bill.getBuyerId(),
                () -> userMapper.selectById(bill.getBuyerId()), SysUser::getRealName));
        vo.setDeptId(bill.getDeptId());
        vo.setDeptName(lookupName(deptNameById, bill.getDeptId(),
                () -> dictItemMapper.selectById(bill.getDeptId()), DictItem::getItemName));
        vo.setOrderDate(bill.getOrderDate() == null ? null : bill.getOrderDate().toString());
        vo.setDeliveryDate(bill.getDeliveryDate() == null ? null : bill.getDeliveryDate().toString());
        vo.setDeliveryPlace(bill.getDeliveryPlace());
        vo.setSettlementType(bill.getSettlementType());
        vo.setSettlementTypeName(settlementTypeName(bill.getSettlementType()));
        vo.setLifecycleStatus(bill.getLifecycleStatus());
        vo.setLifecycleStatusName(lifecycleStatusName(bill.getLifecycleStatus()));
        vo.setAuditStatus(bill.getAuditStatus());
        vo.setAuditStatusName(auditStatusName(bill.getAuditStatus()));
        vo.setFulfillStatus(bill.getFulfillStatus());
        vo.setFulfillStatusName(fulfillStatusName(bill.getFulfillStatus()));
        vo.setDeviationFlag(bill.getDeviationFlag());
        vo.setTotalQty(bill.getTotalQty());
        vo.setTotalAmount(bill.getTotalAmount());
        vo.setNoTaxAmount(bill.getNoTaxAmount());
        vo.setTaxAmount(bill.getTaxAmount());
        vo.setOverdueDays(overdueDays(bill.getDeliveryDate(), bill.getFulfillStatus(), today));
        vo.setCloseReason(bill.getCloseReason());
        vo.setRemark(bill.getRemark());
        return vo;
    }

    private List<OrderItemVO> enrichItems(List<PurchaseOrderItem> items, PurchaseOrder bill,
                                          LocalDate today) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> materialIds = items.stream().map(PurchaseOrderItem::getMaterialId).distinct()
                .collect(Collectors.toList());
        List<Long> unitIds = items.stream().map(PurchaseOrderItem::getUnitId).distinct()
                .collect(Collectors.toList());
        List<Long> ownerIds = items.stream().map(PurchaseOrderItem::getOwnerId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Material> materialById = materialMapper.selectByIds(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));
        Map<Long, DictItem> dictById = unitIds.isEmpty() ? new HashMap<>()
                : dictItemMapper.selectByIds(unitIds).stream()
                .collect(Collectors.toMap(DictItem::getId, Function.identity(), (a, b) -> a));
        Map<Long, OwnerObject> ownerById = ownerIds.isEmpty() ? new HashMap<>()
                : ownerObjectMapper.selectByIds(ownerIds).stream()
                .collect(Collectors.toMap(OwnerObject::getId, Function.identity(), (a, b) -> a));

        List<OrderItemVO> result = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            OrderItemVO vo = new OrderItemVO();
            vo.setId(item.getId());
            vo.setItemNo(item.getItemNo());
            vo.setMaterialId(item.getMaterialId());
            Material material = materialById.get(item.getMaterialId());
            vo.setMaterialNo(material == null ? "" : material.getMaterialNo());
            vo.setMaterialName(material == null ? "" : material.getMaterialName());
            vo.setSpec(material == null ? null : material.getSpec());
            vo.setQuantity(item.getQuantity());
            vo.setUnitId(item.getUnitId());
            vo.setUnitName(dictName(dictById, item.getUnitId()));
            vo.setUnitPrice(item.getUnitPrice());
            vo.setTaxRate(item.getTaxRate());
            vo.setAmount(item.getAmount());
            vo.setNoTaxAmount(item.getNoTaxAmount());
            vo.setDeliveryDate(item.getDeliveryDate() == null ? null : item.getDeliveryDate().toString());
            vo.setDeliveryPlace(item.getDeliveryPlace());
            vo.setDeliveredQty(item.getDeliveredQty());
            BigDecimal delivered = item.getDeliveredQty() == null
                    ? BigDecimal.ZERO : item.getDeliveredQty();
            vo.setRemainingQty(item.getQuantity().subtract(delivered).max(BigDecimal.ZERO));
            vo.setOwnerType(item.getOwnerType());
            vo.setOwnerId(item.getOwnerId());
            OwnerObject owner = ownerById.get(item.getOwnerId());
            vo.setOwnerNo(owner == null ? item.getOwnerNo() : owner.getOwnerNo());
            vo.setOwnerName(owner == null ? null : owner.getOwnerName());
            vo.setItemFulfillStatus(item.getItemFulfillStatus());
            vo.setItemFulfillStatusName(fulfillStatusName(item.getItemFulfillStatus()));
            vo.setSourceRequestItemId(item.getSourceRequestItemId());
            // M3-6 偏离：行级 rate/level 随行返回（整单 flag 由 OrderVO 承载）
            vo.setDeviationRate(item.getDeviationRate());
            vo.setDeviationLevel(item.getDeviationLevel());
            vo.setDeviationLevelName(deviationLevelName(item.getDeviationLevel()));
            // 行级超期：行交货期缺省取主表交货期（DDL 继承口径）
            LocalDate effectiveDate = item.getDeliveryDate() != null
                    ? item.getDeliveryDate() : bill.getDeliveryDate();
            vo.setOverdueDays(overdueDays(effectiveDate, item.getItemFulfillStatus(), today));
            vo.setRemark(item.getRemark());
            result.add(vo);
        }
        return result;
    }

    /** 名称查询：批量 map 命中即用，未命中或未提供 map 时按 id 单查（详情路径）。 */
    private <T> String lookupName(Map<Long, String> batch, Long id,
                                  java.util.function.Supplier<T> single,
                                  Function<T, String> nameOf) {
        if (id == null) {
            return "";
        }
        if (batch != null && batch.containsKey(id)) {
            return batch.get(id);
        }
        T entity = single.get();
        return entity == null ? "" : nameOf.apply(entity);
    }

    private String dictName(Map<Long, DictItem> dictById, Long id) {
        DictItem item = dictById.get(id);
        return item == null ? "" : item.getItemName();
    }

    // ================= 私有：状态与字段名称 =================

    private String lifecycleStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case LIFECYCLE_DRAFT -> "草稿";
            case LIFECYCLE_SUBMITTED -> "已提交";
            case LIFECYCLE_APPROVED -> "已审核";
            case LIFECYCLE_CLOSED -> "已关闭";
            case LIFECYCLE_VOIDED -> "已作废";
            default -> "";
        };
    }

    private String auditStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case AUDIT_PENDING -> "未审核";
            case AUDIT_PASSED -> "通过";
            case AUDIT_REJECTED -> "驳回";
            default -> "";
        };
    }

    private String fulfillStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case FULFILL_PENDING -> "待交付";
            case FULFILL_PARTIAL -> "部分交付";
            case FULFILL_FULL -> "全部交付";
            case FULFILL_CLOSED -> "已关闭";
            default -> "";
        };
    }

    /** M3-6 偏离等级名称：0 正常、1 关注、2 显著 */
    private String deviationLevelName(Integer level) {
        if (level == null || level == 0) {
            return "正常";
        }
        return level == 2 ? "显著" : "关注";
    }

    private String settlementTypeName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case SETTLEMENT_MONTHLY -> "月结";
            case SETTLEMENT_ON_DELIVERY -> "货到付款";
            case SETTLEMENT_PREPAID -> "预付款";
            default -> "";
        };
    }

    private String ownerTypeName(Integer ownerType) {
        if (ownerType == null) {
            return "";
        }
        return switch (ownerType) {
            case OWNER_PUBLIC -> "公共";
            case 2 -> "项目";
            case 3 -> "工单";
            default -> "";
        };
    }

    private String changeFieldName(String field) {
        if (field == null) {
            return "";
        }
        return switch (field) {
            case "quantity" -> "订单数量";
            case "unit_price" -> "含税单价";
            case "delivery_date" -> "交货期";
            case "delivery_place" -> "交货地点";
            case "settlement_type" -> "结算方式";
            default -> field;
        };
    }
}
