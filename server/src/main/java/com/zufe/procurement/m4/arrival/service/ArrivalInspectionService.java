package com.zufe.procurement.m4.arrival.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BillNoGenerator;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m4.arrival.dto.ArrivalItemSaveRequest;
import com.zufe.procurement.m4.arrival.dto.ArrivalQuery;
import com.zufe.procurement.m4.arrival.dto.ArrivalSaveRequest;
import com.zufe.procurement.m4.arrival.dto.ArrivalStatQuery;
import com.zufe.procurement.m4.arrival.dto.ConcessionRequest;
import com.zufe.procurement.m4.arrival.dto.InboundGenerateRequest;
import com.zufe.procurement.m4.arrival.entity.ArrivalInspection;
import com.zufe.procurement.m4.arrival.entity.ArrivalInspectionItem;
import com.zufe.procurement.m4.arrival.mapper.ArrivalInspectionItemMapper;
import com.zufe.procurement.m4.arrival.mapper.ArrivalInspectionMapper;
import com.zufe.procurement.m4.arrival.vo.ArrivalItemVO;
import com.zufe.procurement.m4.arrival.vo.ArrivalOrderVO;
import com.zufe.procurement.m4.arrival.vo.ArrivalStatVO;
import com.zufe.procurement.m4.arrival.vo.ArrivalVO;
import com.zufe.procurement.m4.arrival.vo.InboundDraftVO;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import com.zufe.procurement.m3.order.entity.PurchaseOrderItem;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderItemMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m5.inbound.entity.InboundItem;
import com.zufe.procurement.m5.inbound.entity.InboundOrder;
import com.zufe.procurement.m5.inbound.mapper.InboundItemMapper;
import com.zufe.procurement.m5.inbound.mapper.InboundOrderMapper;
import com.zufe.procurement.m11.system.service.OperationLogService;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 到货验收服务，覆盖功能点 M4-1 到货登记（M4-1-1 至 M4-1-3）、
 * M4-2 验收登记与判定（M4-2-1 至 M4-2-5）、M4-3 生成采购入库单
 * （M4-3-1 至 M4-3-4）、M4-4 验收记录查询（M4-4-1 至 M4-4-3）。
 *
 * 业务口径集中在本层：
 * - 到货与入库是两个配额：到货不超「订单量 − 累计到货」，入库不超
 *   「订单量 − 累计已生成入库量」（草稿也占两个配额，防同时建草稿
 *   超发，M4-1-3、M4-3-4）；
 * - 守恒律 R12：合格 + 不合格 = 到货，提交时逐行校验；
 * - 整单验收结果由明细行重算：全行合格 → 1；有不合格且让步 → 3 让步接收；
 *   有不合格未让步 → 2 不合格。concession_flag 与结果 3 同步；
 * - 不合格数量不生成入库单；让步接收是唯一例外（不合格但同意接收），
 *   否则让步接收没有业务意义（M4-3-1）；
 * - delivered_qty 不因到货与验收变化，唯一写入点仍是入库单确认（R2），
 *   在轮次 7 落地；本模块只生成入库单草稿；
 * - 让步接收是整单动作（M4-2-4），一单多行不能只让一行；
 * - 列表按仓库维度注入数据范围（仓管员授权口径），部门维度的采购员
 *   与主管在验收列表上失败关闭；单据详情与动作不套范围，由权限点
 *   把关（明细查看授给全部角色，让步归主管，登记与入库归仓管员）；
 *   统计是跨仓库的质量视角（M4-4-3 归采购主管），同样不注入个人范围；
 * - 草稿可删除（放弃未提交登记，挂 create 权限），已验收单不可删除——
 *   清单 M4 无删除与作废功能点，错单在入库环节消化。
 */
@Service
public class ArrivalInspectionService {

    // ---------------- 状态与枚举常量 ----------------

    /** 验收单状态 */
    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_INSPECTED = 2;

    /** 验收方式 */
    private static final int MODE_SKIP = 1;
    private static final int MODE_SAMPLE = 2;
    private static final int MODE_FULL = 3;

    /** 验收结果 */
    private static final int RESULT_QUALIFIED = 1;
    private static final int RESULT_UNQUALIFIED = 2;
    private static final int RESULT_CONCESSION = 3;

    /** 订单生命周期（仅已审核订单可登记到货） */
    private static final int ORDER_APPROVED = 3;
    private static final int ORDER_CLOSED = 4;
    private static final int ORDER_VOIDED = 5;

    /** 行履约状态：4 已关闭 */
    private static final int ITEM_FULFILL_CLOSED = 4;

    /** 入库单状态 */
    private static final int INBOUND_DRAFT = 1;
    private static final int INBOUND_CONFIRMED = 2;

    private static final int NO_ATTEMPTS = 3;

    private static final String PERM_CONCESSION = "m4:inspection:concession";

    private final ArrivalInspectionMapper inspectionMapper;
    private final ArrivalInspectionItemMapper inspectionItemMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundItemMapper inboundItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final DictItemMapper dictItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final BillNoGenerator billNoGenerator;
    private final DataScopeService dataScopeService;
    private final OperationLogService operationLogService;

    public ArrivalInspectionService(ArrivalInspectionMapper inspectionMapper,
                                    ArrivalInspectionItemMapper inspectionItemMapper,
                                    InboundOrderMapper inboundOrderMapper,
                                    InboundItemMapper inboundItemMapper,
                                    PurchaseOrderMapper orderMapper,
                                    PurchaseOrderItemMapper orderItemMapper,
                                    MaterialMapper materialMapper,
                                    SupplierMapper supplierMapper,
                                    DictItemMapper dictItemMapper,
                                    WarehouseMapper warehouseMapper,
                                    SysUserMapper userMapper,
                                    BillNoGenerator billNoGenerator,
                                    DataScopeService dataScopeService,
                                    OperationLogService operationLogService) {
        this.inspectionMapper = inspectionMapper;
        this.inspectionItemMapper = inspectionItemMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundItemMapper = inboundItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.materialMapper = materialMapper;
        this.supplierMapper = supplierMapper;
        this.dictItemMapper = dictItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.userMapper = userMapper;
        this.billNoGenerator = billNoGenerator;
        this.dataScopeService = dataScopeService;
        this.operationLogService = operationLogService;
    }

    // ================= M4-4-1 验收记录列表 =================

    /**
     * 分页列表。数据范围按仓库维度注入（仓管员授权口径），
     * 范围为空失败关闭返回空列表；统计口径见 stat 方法注释。
     */
    public PageResult<ArrivalVO> page(ArrivalQuery query) {
        Page<ArrivalInspection> page = new Page<>(Math.max(query.getPageNo(), 1),
                Math.min(Math.max(query.getPageSize(), 1), 100));
        LambdaQueryWrapper<ArrivalInspection> wrapper = new LambdaQueryWrapper<ArrivalInspection>()
                .like(StringUtils.hasText(query.getInspectionNo()), ArrivalInspection::getInspectionNo,
                        query.getInspectionNo() == null ? null : query.getInspectionNo().trim())
                .like(StringUtils.hasText(query.getOrderNo()), ArrivalInspection::getOrderNo,
                        query.getOrderNo() == null ? null : query.getOrderNo().trim())
                .eq(query.getOrderId() != null, ArrivalInspection::getOrderId, query.getOrderId())
                .eq(query.getSupplierId() != null, ArrivalInspection::getSupplierId, query.getSupplierId())
                .eq(query.getWarehouseId() != null, ArrivalInspection::getWarehouseId, query.getWarehouseId())
                .eq(query.getInspectionResult() != null, ArrivalInspection::getInspectionResult,
                        query.getInspectionResult())
                .eq(query.getStatus() != null, ArrivalInspection::getStatus, query.getStatus())
                .ge(query.getStartDate() != null, ArrivalInspection::getArrivalDate, query.getStartDate())
                .le(query.getEndDate() != null, ArrivalInspection::getArrivalDate, query.getEndDate())
                .orderByDesc(ArrivalInspection::getArrivalDate)
                .orderByDesc(ArrivalInspection::getId);
        dataScopeService.applyWarehouse(wrapper, ArrivalInspection::getWarehouseId, currentScope());
        Page<ArrivalInspection> result = inspectionMapper.selectPage(page, wrapper);
        List<ArrivalInspection> bills = result.getRecords();
        if (bills.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        return PageResult.of(assembleList(bills), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** M4-4-2 详情：主表 + 明细行（带配额现算）+ 已生成的入库单草稿。 */
    public ArrivalVO detail(Long id) {
        ArrivalInspection bill = requireInScope(id);
        return assembleBill(bill);
    }

    // ================= M4-1-1 至 M4-1-3 到货登记 =================

    /**
     * 新建到货登记（草稿）。订单必须已审核且未关闭未作废；
     * 每行到货量不超过该订单行剩余到货配额（M4-1-3）；
     * 合格/不合格缺省按全合格落行，提交时再校验守恒。
     */
    @Transactional
    public Long create(ArrivalSaveRequest request) {
        PurchaseOrder order = requireArrivableOrder(request.getOrderId());
        Long warehouseId = requireArrivableWarehouse(request.getWarehouseId());
        List<ArrivalInspectionItem> items = buildItems(order, request.getItems(), null);

        ArrivalInspection bill = new ArrivalInspection();
        bill.setInspectionNo(nextInspectionNo());
        bill.setOrderId(order.getId());
        bill.setOrderNo(order.getOrderNo());
        bill.setSupplierId(order.getSupplierId());
        bill.setArrivalDate(request.getArrivalDate() == null ? LocalDate.now() : request.getArrivalDate());
        bill.setWarehouseId(warehouseId);
        bill.setInspectionMode(normalizeMode(request.getInspectionMode()));
        bill.setQualifiedQty(sum(items, ArrivalInspectionItem::getQualifiedQty));
        bill.setUnqualifiedQty(sum(items, ArrivalInspectionItem::getUnqualifiedQty));
        bill.setConcessionFlag(0);
        bill.setInspectorId(currentUserId());
        bill.setStatus(STATUS_DRAFT);
        bill.setRemark(request.getRemark());
        insertWithRetry(bill);
        saveItems(bill.getId(), items);
        operationLogService.record("M4", "create", "arrival_inspection", bill.getId(),
                bill.getInspectionNo(),
                "到货登记：订单 " + order.getOrderNo() + "，" + items.size() + " 行");
        return bill.getId();
    }

    /**
     * 编辑到货登记（M4-1，权限点 81）。仅草稿可编辑；
     * 明细行按 (inspection_id, order_item_id) 全量替换后重算数量，
     * 到货配额扣减已落库的其他验收单量，不含本次草稿——
     * 编辑时可把到货量改大（只要不超剩余），与新建口径一致。
     */
    @Transactional
    public void update(Long id, ArrivalSaveRequest request) {
        ArrivalInspection bill = requireDraft(id);
        PurchaseOrder order = requireArrivableOrder(bill.getOrderId());
        Long warehouseId = requireArrivableWarehouse(request.getWarehouseId());
        List<ArrivalInspectionItem> items = buildItems(order, request.getItems(), id);

        bill.setArrivalDate(request.getArrivalDate() == null ? bill.getArrivalDate() : request.getArrivalDate());
        bill.setWarehouseId(warehouseId);
        bill.setInspectionMode(normalizeMode(request.getInspectionMode()));
        bill.setQualifiedQty(sum(items, ArrivalInspectionItem::getQualifiedQty));
        bill.setUnqualifiedQty(sum(items, ArrivalInspectionItem::getUnqualifiedQty));
        bill.setRemark(request.getRemark());
        inspectionMapper.updateById(bill);
        inspectionItemMapper.deleteByInspectionId(id);
        saveItems(id, items);
        operationLogService.record("M4", "update", "arrival_inspection", id,
                bill.getInspectionNo(), "编辑到货登记：" + items.size() + " 行");
    }

    /**
     * 删除草稿验收单。清单 M4 无删除功能点，但草稿必须可放弃，
     * 否则错登的草稿永远占到货配额。挂 create 权限（登记职责的一部分），
     * 已验收单不可删除——错单在入库环节消化。
     */
    @Transactional
    public void delete(Long id) {
        ArrivalInspection bill = requireDraft(id);
        inspectionItemMapper.deleteByInspectionId(id);
        inspectionMapper.deleteById(id);
        operationLogService.record("M4", "delete", "arrival_inspection", id,
                bill.getInspectionNo(), "删除草稿验收单");
    }

    // ================= M4-2-4 让步接收 =================

    /**
     * 让步接收标记（M4-2-4，权限点 83，归采购主管）。
     * 整单动作：一单多行不能只让一行。打标记时理由必填且必须
     * 存在不合格数量（没有不合格就无需让步）；整单结果随之
     * 重算为 3 让步接收，可入量随之含不合格部分。
     */
    @Transactional
    public void concession(Long id, ConcessionRequest request) {
        ArrivalInspection bill = requireInScope(id);
        if (request.isFlag()) {
            if (!StringUtils.hasText(request.getReason())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "让步接收必须填写理由");
            }
            if (bill.getUnqualifiedQty() == null
                    || bill.getUnqualifiedQty().signum() <= 0) {
                throw new BizException(ResultCode.UNSUPPORTED, "无不合格数量，无需让步接收");
            }
            bill.setConcessionFlag(1);
            bill.setConcessionReason(request.getReason().trim());
            bill.setInspectionResult(RESULT_CONCESSION);
        } else {
            bill.setConcessionFlag(0);
            bill.setConcessionReason(null);
            bill.setInspectionResult(recomputeResult(bill));
        }
        inspectionMapper.updateById(bill);
        operationLogService.record("M4", "concession", "arrival_inspection", id,
                bill.getInspectionNo(),
                request.isFlag() ? "让步接收：" + request.getReason() : "取消让步接收");
    }

    // ================= M4-2-5 验收单提交 =================

    /**
     * 提交验收单（M4-2-5）。校验信息完整：至少一行、每行守恒
     * （合格 + 不合格 = 到货，R12）、验收方式合法；整单合格/不合格
     * 合计与结果由明细重算；提交后进入已验收态，字段锁定，
     * 后续只允许让步标记与生成入库单。
     */
    @Transactional
    public void submit(Long id) {
        ArrivalInspection bill = requireDraft(id);
        List<ArrivalInspectionItem> items = inspectionItemMapper.selectByInspectionId(id);
        if (items.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "验收单没有明细行，不能提交");
        }
        BigDecimal qualified = BigDecimal.ZERO;
        BigDecimal unqualified = BigDecimal.ZERO;
        for (ArrivalInspectionItem item : items) {
            BigDecimal arrival = nz(item.getArrivalQty());
            BigDecimal q = nz(item.getQualifiedQty());
            BigDecimal u = nz(item.getUnqualifiedQty());
            if (q.add(u).compareTo(arrival) != 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "第 " + item.getId() + " 行合格数量加不合格数量必须等于到货数量");
            }
            qualified = qualified.add(q);
            unqualified = unqualified.add(u);
        }
        bill.setQualifiedQty(qualified);
        bill.setUnqualifiedQty(unqualified);
        bill.setStatus(STATUS_INSPECTED);
        bill.setInspectorId(currentUserId());
        // 无不合格数量的让步标记自动清零（没有不合格就无所谓让步）
        if (Integer.valueOf(1).equals(bill.getConcessionFlag()) && unqualified.signum() <= 0) {
            bill.setConcessionFlag(0);
            bill.setConcessionReason(null);
        }
        bill.setInspectionResult(recomputeResult(bill));
        inspectionMapper.updateById(bill);
        operationLogService.record("M4", "submit", "arrival_inspection", id,
                bill.getInspectionNo(),
                "验收提交：合格 " + qualified + "，不合格 " + unqualified);
    }

    // ================= M4-3-1 至 M4-3-4 生成入库单 =================

    /**
     * 由已验收单生成入库单草稿（M4-3-1）。每行入库量：
     * 让步单 = 合格 + 不合格（不合格不生成入库的唯一例外），
     * 否则只取合格；不传行级量时缺省入全部可入量，显式传量时
     * 只入显式列出的行（部分入库 M4-3-2）；行级量逐行校验不超过
     * 可入量；可多次生成，每次一张独立草稿（多批次 M4-3-3）；
     * 订单行级校验累计已生成 + 本次不超过订单量（M4-3-4，
     * delivered_qty 是确认回写的结果不参与配额）。
     * delivered_qty 不动（R2），由轮次 7 的入库确认回写。
     */
    @Transactional
    public Long generateInbound(Long id, InboundGenerateRequest request) {
        ArrivalInspection bill = requireInScope(id);
        if (!Integer.valueOf(STATUS_INSPECTED).equals(bill.getStatus())) {
            throw new BizException(ResultCode.UNSUPPORTED, "仅已验收的验收单可以生成入库单");
        }
        PurchaseOrder order = requireArrivableOrder(bill.getOrderId());
        List<ArrivalInspectionItem> items = inspectionItemMapper.selectByInspectionId(id);
        if (items.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "验收单没有明细行");
        }

        boolean concession = Integer.valueOf(1).equals(bill.getConcessionFlag());
        // 显式传行级量时只入显式列出的行；不传才缺省入全部可入量
        boolean partial = request.getItems() != null && !request.getItems().isEmpty();
        Map<Long, ArrivalInspectionItem> itemById = items.stream()
                .collect(Collectors.toMap(ArrivalInspectionItem::getId, Function.identity(), (a, b) -> a));
        Map<Long, BigDecimal> requested = new HashMap<>();
        if (partial) {
            for (InboundGenerateRequest.InboundItemQty row : request.getItems()) {
                if (row.getInspectionItemId() == null || row.getQuantity() == null) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "行级入库量缺少明细行或数量");
                }
                if (!itemById.containsKey(row.getInspectionItemId())) {
                    throw new BizException(ResultCode.NOT_FOUND, "入库量明细行不属于本验收单");
                }
                if (row.getQuantity().signum() <= 0) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "行级入库量必须大于 0");
                }
                requested.put(row.getInspectionItemId(), row.getQuantity());
            }
        }

        // 订单行级入库配额：订单量 − 累计已生成入库量（草稿 + 已确认）。
        // 不扣 delivered_qty——已确认单的 quantity 已含在累计里，再扣会
        // 双重计算（delivered_qty 是确认回写的结果，不是配额口径）。
        List<Long> orderItemIds = items.stream().map(ArrivalInspectionItem::getOrderItemId).distinct().toList();
        Map<Long, PurchaseOrderItem> orderItems = orderItemMapper.selectByOrderIds(List.of(bill.getOrderId())).stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity(), (a, b) -> a));
        Map<Long, BigDecimal> generated = sumGeneratedQty(orderItemIds);

        List<InboundItem> inboundItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ArrivalInspectionItem item : items) {
            if (partial && !requested.containsKey(item.getId())) {
                continue; // 部分入库：只入显式列出的行
            }
            PurchaseOrderItem orderItem = orderItems.get(item.getOrderItemId());
            if (orderItem == null) {
                throw new BizException(ResultCode.NOT_FOUND, "订单明细行不存在：" + item.getOrderItemId());
            }
            BigDecimal inboundable = concession
                    ? nz(item.getQualifiedQty()).add(nz(item.getUnqualifiedQty()))
                    : nz(item.getQualifiedQty());
            if (inboundable.signum() <= 0) {
                continue; // 该行无可入量（全不合格且非让步）
            }
            BigDecimal used = generated.getOrDefault(item.getOrderItemId(), BigDecimal.ZERO);
            BigDecimal cap = nz(orderItem.getQuantity()).subtract(used);
            if (cap.signum() <= 0) {
                throw new BizException(ResultCode.UNSUPPORTED,
                        "订单行 " + item.getOrderItemId() + " 入库配额已用完，不能继续入库");
            }
            BigDecimal qty = requested.getOrDefault(item.getId(), inboundable);
            if (qty.compareTo(inboundable) > 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "行 " + item.getId() + " 入库量超过本行可入量 " + inboundable.toPlainString());
            }
            if (qty.compareTo(cap) > 0) {
                throw new BizException(ResultCode.UNSUPPORTED,
                        "行 " + item.getId() + " 入库量超过订单剩余入库配额 " + cap.toPlainString());
            }
            InboundItem inboundItem = new InboundItem();
            inboundItem.setOrderItemId(item.getOrderItemId());
            inboundItem.setMaterialId(item.getMaterialId());
            inboundItem.setQuantity(qty);
            inboundItem.setUnitId(item.getUnitId());
            inboundItem.setBatchNo(item.getBatchNo() == null ? "" : item.getBatchNo());
            inboundItem.setOwnerType(item.getOwnerType());
            inboundItem.setOwnerId(item.getOwnerId());
            inboundItem.setOwnerNo(item.getOwnerNo());
            inboundItem.setRemark(item.getRemark());
            inboundItems.add(inboundItem);
            total = total.add(qty);
        }
        if (inboundItems.isEmpty()) {
            throw new BizException(ResultCode.NO_DATA, "没有可入库的数量");
        }

        InboundOrder inbound = new InboundOrder();
        inbound.setInboundNo(nextInboundNo());
        inbound.setInspectionId(bill.getId());
        inbound.setInspectionNo(bill.getInspectionNo());
        inbound.setOrderId(bill.getOrderId());
        inbound.setOrderNo(bill.getOrderNo());
        inbound.setSupplierId(bill.getSupplierId());
        inbound.setWarehouseId(bill.getWarehouseId());
        inbound.setInboundDate(request.getInboundDate() == null ? LocalDate.now() : request.getInboundDate());
        inbound.setStatus(INBOUND_DRAFT);
        inbound.setOperatorId(currentUserId());
        inbound.setRemark(request.getRemark());
        insertInboundWithRetry(inbound);
        for (InboundItem inboundItem : inboundItems) {
            inboundItem.setInboundId(inbound.getId());
            inboundItemMapper.insert(inboundItem);
        }
        operationLogService.record("M4", "inbound_generate", "inbound_order", inbound.getId(),
                inbound.getInboundNo(),
                "由验收单 " + bill.getInspectionNo() + " 生成入库单草稿，共 " + total.toPlainString());
        return inbound.getId();
    }

    // ================= M4-4-3 验收结果统计 =================

    /**
     * 按供应商汇总验收结果（M4-4-3，权限点 84，归采购主管）。
     * 只统计已验收单（草稿的判定未落定）；不注入数据范围——
     * 统计是跨仓库的质量视角，部门维度授权对仓库维度单据无意义。
     */
    public List<ArrivalStatVO> stat(ArrivalStatQuery query) {
        LambdaQueryWrapper<ArrivalInspection> wrapper = new LambdaQueryWrapper<ArrivalInspection>()
                .eq(ArrivalInspection::getStatus, STATUS_INSPECTED)
                .eq(query.getSupplierId() != null, ArrivalInspection::getSupplierId, query.getSupplierId())
                .ge(query.getStartDate() != null, ArrivalInspection::getArrivalDate, query.getStartDate())
                .le(query.getEndDate() != null, ArrivalInspection::getArrivalDate, query.getEndDate());
        List<ArrivalInspection> bills = inspectionMapper.selectList(wrapper);
        if (bills.isEmpty()) {
            return List.of();
        }
        Map<Long, ArrivalStatVO> grouped = new LinkedHashMap<>();
        for (ArrivalInspection bill : bills) {
            ArrivalStatVO row = grouped.computeIfAbsent(bill.getSupplierId(),
                    k -> new ArrivalStatVO());
            row.setSupplierId(bill.getSupplierId());
            row.setInspectionCount(row.getInspectionCount() + 1);
            row.setArrivalQty(nz(row.getArrivalQty()).add(nz(bill.getQualifiedQty()))
                    .add(nz(bill.getUnqualifiedQty())));
            row.setQualifiedQty(nz(row.getQualifiedQty()).add(nz(bill.getQualifiedQty())));
            row.setUnqualifiedQty(nz(row.getUnqualifiedQty()).add(nz(bill.getUnqualifiedQty())));
        }
        List<ArrivalStatVO> rows = new ArrayList<>(grouped.values());
        fillSupplierNames(rows, ArrivalStatVO::getSupplierId, ArrivalStatVO::setSupplierName);
        for (ArrivalStatVO row : rows) {
            BigDecimal arrival = nz(row.getArrivalQty());
            row.setQualifiedRate(arrival.signum() == 0 ? BigDecimal.ZERO
                    : nz(row.getQualifiedQty()).divide(arrival, 4, RoundingMode.HALF_UP));
        }
        return rows;
    }

    // ================= 登记页辅助：可登记订单与明细 =================

    /**
     * 可登记的已审核订单列表（M4-1-1 选单下拉）。
     * 只取已审核、未关闭、未作废、还有未到货明细的订单。
     * 不注入数据范围：订单是部门维度单据，仓管员是仓库维度授权，
     * 注入部门条件会让登记页永远选不到单；范围控制在收货仓库
     * 校验处落地（requireArrivableWarehouse）。
     */
    public List<ArrivalOrderVO> arrivableOrders() {
        List<PurchaseOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getLifecycleStatus, ORDER_APPROVED)
                .orderByDesc(PurchaseOrder::getId));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<ArrivalOrderVO> result = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            List<PurchaseOrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId()).stream()
                    .filter(i -> !Integer.valueOf(ITEM_FULFILL_CLOSED).equals(i.getItemFulfillStatus()))
                    .toList();
            if (orderItems.isEmpty()) {
                continue;
            }
            Map<Long, BigDecimal> arrived = sumArrivalQty(order.getId(),
                    orderItems.stream().map(PurchaseOrderItem::getId).toList());
            List<ArrivalItemVO> itemVOs = new ArrayList<>();
            BigDecimal remaining = BigDecimal.ZERO;
            for (PurchaseOrderItem orderItem : orderItems) {
                BigDecimal orderQty = nz(orderItem.getQuantity());
                BigDecimal arrivedQty = arrived.getOrDefault(orderItem.getId(), BigDecimal.ZERO);
                BigDecimal left = orderQty.subtract(arrivedQty);
                if (left.signum() <= 0) {
                    continue;
                }
                itemVOs.add(buildArrivalItemVO(orderItem, arrivedQty, left));
                remaining = remaining.add(left);
            }
            if (itemVOs.isEmpty()) {
                continue;
            }
            ArrivalOrderVO vo = new ArrivalOrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setSupplierId(order.getSupplierId());
            vo.setDeliveryDate(order.getDeliveryDate());
            vo.setDeliveryPlace(order.getDeliveryPlace());
            vo.setItems(itemVOs);
            vo.setRemainingArrivalQty(remaining);
            result.add(vo);
        }
        fillSupplierNames(result, ArrivalOrderVO::getSupplierId, ArrivalOrderVO::setSupplierName);
        return result;
    }

    /**
     * 单个订单的可登记明细（M4-1-1「列出未交付明细」）。
     * 已全部到货的行不返回——没有剩余到货配额就不必展示。
     */
    public ArrivalOrderVO orderArrivalItems(Long orderId) {
        PurchaseOrder order = requireArrivableOrder(orderId);
        List<PurchaseOrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId()).stream()
                .filter(i -> !Integer.valueOf(ITEM_FULFILL_CLOSED).equals(i.getItemFulfillStatus()))
                .toList();
        Map<Long, BigDecimal> arrived = sumArrivalQty(order.getId(),
                orderItems.stream().map(PurchaseOrderItem::getId).toList());
        List<ArrivalItemVO> itemVOs = new ArrayList<>();
        BigDecimal remaining = BigDecimal.ZERO;
        for (PurchaseOrderItem orderItem : orderItems) {
            BigDecimal orderQty = nz(orderItem.getQuantity());
            BigDecimal arrivedQty = arrived.getOrDefault(orderItem.getId(), BigDecimal.ZERO);
            BigDecimal left = orderQty.subtract(arrivedQty);
            if (left.signum() <= 0) {
                continue;
            }
            itemVOs.add(buildArrivalItemVO(orderItem, arrivedQty, left));
            remaining = remaining.add(left);
        }
        ArrivalOrderVO vo = new ArrivalOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setSupplierId(order.getSupplierId());
        vo.setDeliveryDate(order.getDeliveryDate());
        vo.setDeliveryPlace(order.getDeliveryPlace());
        vo.setItems(itemVOs);
        vo.setRemainingArrivalQty(remaining);
        fillSupplierNames(List.of(vo), ArrivalOrderVO::getSupplierId, ArrivalOrderVO::setSupplierName);
        return vo;
    }

    // ================= 私有：装配 =================

    private List<ArrivalVO> assembleList(List<ArrivalInspection> bills) {
        List<ArrivalVO> result = new ArrayList<>();
        for (ArrivalInspection bill : bills) {
            result.add(assembleBill(bill, false));
        }
        return result;
    }

    private ArrivalVO assembleBill(ArrivalInspection bill) {
        return assembleBill(bill, true);
    }

    private ArrivalVO assembleBill(ArrivalInspection bill, boolean withDetail) {
        ArrivalVO vo = new ArrivalVO();
        vo.setId(bill.getId());
        vo.setInspectionNo(bill.getInspectionNo());
        vo.setOrderId(bill.getOrderId());
        vo.setOrderNo(bill.getOrderNo());
        vo.setSupplierId(bill.getSupplierId());
        vo.setArrivalDate(bill.getArrivalDate());
        vo.setWarehouseId(bill.getWarehouseId());
        vo.setInspectionMode(bill.getInspectionMode());
        vo.setInspectionModeName(modeName(bill.getInspectionMode()));
        vo.setInspectionResult(bill.getInspectionResult());
        vo.setInspectionResultName(resultName(bill.getInspectionResult()));
        vo.setQualifiedQty(bill.getQualifiedQty());
        vo.setUnqualifiedQty(bill.getUnqualifiedQty());
        vo.setConcessionFlag(bill.getConcessionFlag());
        vo.setConcessionReason(bill.getConcessionReason());
        vo.setInspectorId(bill.getInspectorId());
        vo.setStatus(bill.getStatus());
        vo.setStatusName(Integer.valueOf(STATUS_INSPECTED).equals(bill.getStatus()) ? "已验收" : "草稿");
        vo.setRemark(bill.getRemark());
        vo.setCreatedAt(bill.getCreatedAt());
        vo.setCreatedBy(bill.getCreatedBy());
        fillSupplierNames(List.of(vo), ArrivalVO::getSupplierId, ArrivalVO::setSupplierName);
        fillWarehouseNames(List.of(vo), ArrivalVO::getWarehouseId, ArrivalVO::setWarehouseName);
        fillUserNames(List.of(vo), ArrivalVO::getInspectorId, ArrivalVO::setInspectorName);
        fillUserNames(List.of(vo), ArrivalVO::getCreatedBy, ArrivalVO::setCreatedByName);
        if (withDetail) {
            List<ArrivalInspectionItem> items = inspectionItemMapper.selectByInspectionId(bill.getId());
            List<Long> orderItemIds = items.stream().map(ArrivalInspectionItem::getOrderItemId).distinct().toList();
            Map<Long, PurchaseOrderItem> orderItems = orderItemIds.isEmpty() ? Map.of()
                    : orderItemMapper.selectByOrderIds(List.of(bill.getOrderId())).stream()
                    .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity(), (a, b) -> a));
            Map<Long, BigDecimal> arrived = orderItemIds.isEmpty() ? Map.of()
                    : sumArrivalQty(bill.getOrderId(), orderItemIds);
            Map<Long, BigDecimal> generated = orderItemIds.isEmpty() ? Map.of()
                    : sumGeneratedQty(orderItemIds);
            boolean concession = Integer.valueOf(1).equals(bill.getConcessionFlag());
            BigDecimal inboundableTotal = BigDecimal.ZERO;
            BigDecimal generatedTotal = BigDecimal.ZERO;
            List<ArrivalItemVO> itemVOs = new ArrayList<>();
            for (ArrivalInspectionItem item : items) {
                PurchaseOrderItem orderItem = orderItems.get(item.getOrderItemId());
                ArrivalItemVO itemVO = new ArrivalItemVO();
                itemVO.setId(item.getId());
                itemVO.setInspectionId(item.getInspectionId());
                itemVO.setOrderItemId(item.getOrderItemId());
                itemVO.setOrderItemNo(orderItem == null ? null : orderItem.getItemNo());
                itemVO.setMaterialId(item.getMaterialId());
                itemVO.setUnitId(item.getUnitId());
                itemVO.setArrivalQty(item.getArrivalQty());
                itemVO.setQualifiedQty(item.getQualifiedQty());
                itemVO.setUnqualifiedQty(item.getUnqualifiedQty());
                itemVO.setBatchNo(item.getBatchNo());
                itemVO.setOwnerType(item.getOwnerType());
                itemVO.setOwnerId(item.getOwnerId());
                itemVO.setOwnerNo(item.getOwnerNo());
                itemVO.setRemark(item.getRemark());
                itemVO.setOrderQty(orderItem == null ? null : orderItem.getQuantity());
                itemVO.setArrivedQty(arrived.getOrDefault(item.getOrderItemId(), BigDecimal.ZERO));
                itemVO.setRemainingArrival(orderItem == null ? null
                        : nz(orderItem.getQuantity()).subtract(nz(itemVO.getArrivedQty())));
                BigDecimal inboundable = concession
                        ? nz(item.getQualifiedQty()).add(nz(item.getUnqualifiedQty()))
                        : nz(item.getQualifiedQty());
                itemVO.setInboundableQty(inboundable);
                inboundableTotal = inboundableTotal.add(inboundable);
                generatedTotal = generatedTotal.add(
                        generated.getOrDefault(item.getOrderItemId(), BigDecimal.ZERO));
                itemVOs.add(itemVO);
            }
            fillMaterialNames(itemVOs, ArrivalItemVO::getMaterialId, ArrivalItemVO::setMaterialNo,
                    ArrivalItemVO::setMaterialName, ArrivalItemVO::setSpec);
            fillUnitNames(itemVOs, ArrivalItemVO::getUnitId, ArrivalItemVO::setUnitName);
            vo.setItems(itemVOs);
            vo.setInboundableQty(inboundableTotal);
            vo.setGeneratedQty(generatedTotal);
            List<InboundOrder> inbounds = inboundOrderMapper.selectByInspectionId(bill.getId());
            vo.setInboundOrders(assembleInbounds(inbounds));
        }
        return vo;
    }

    private List<InboundDraftVO> assembleInbounds(List<InboundOrder> inbounds) {
        if (inbounds.isEmpty()) {
            return List.of();
        }
        List<InboundDraftVO> result = new ArrayList<>();
        for (InboundOrder inbound : inbounds) {
            InboundDraftVO vo = new InboundDraftVO();
            vo.setId(inbound.getId());
            vo.setInboundNo(inbound.getInboundNo());
            vo.setInspectionId(inbound.getInspectionId());
            vo.setInspectionNo(inbound.getInspectionNo());
            vo.setOrderId(inbound.getOrderId());
            vo.setOrderNo(inbound.getOrderNo());
            vo.setSupplierId(inbound.getSupplierId());
            vo.setWarehouseId(inbound.getWarehouseId());
            vo.setInboundDate(inbound.getInboundDate());
            vo.setStatus(inbound.getStatus());
            vo.setStatusName(inboundStatusName(inbound.getStatus()));
            vo.setOperatorId(inbound.getOperatorId());
            vo.setRemark(inbound.getRemark());
            List<InboundItem> items = inboundItemMapper.selectByInboundId(inbound.getId());
            vo.setTotalQty(items.stream().map(InboundItem::getQuantity)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
            result.add(vo);
        }
        fillSupplierNames(result, InboundDraftVO::getSupplierId, InboundDraftVO::setSupplierName);
        fillWarehouseNames(result, InboundDraftVO::getWarehouseId, InboundDraftVO::setWarehouseName);
        fillUserNames(result, InboundDraftVO::getOperatorId, InboundDraftVO::setOperatorName);
        return result;
    }

    // ================= 私有：构建与校验 =================

    /** 订单必须已审核且未关闭未作废（到货登记的前置，M4-1-1）。 */
    private PurchaseOrder requireArrivableOrder(Long orderId) {
        if (orderId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择来源订单");
        }
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        if (!Integer.valueOf(ORDER_APPROVED).equals(order.getLifecycleStatus())) {
            throw new BizException(ResultCode.UNSUPPORTED, "仅已审核的订单可以登记到货");
        }
        return order;
    }

    /** 仓库必须存在且启用，且在当前用户的仓库数据范围内。 */
    private Long requireArrivableWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择收货仓库");
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BizException(ResultCode.NOT_FOUND, "仓库不存在");
        }
        if (!Integer.valueOf(1).equals(warehouse.getStatus())) {
            throw new BizException(ResultCode.UNSUPPORTED, "仓库已停用，不能登记到货");
        }
        ScopeCondition scope = currentScope();
        if (!scope.isAll() && scope.hasWarehouse() && !scope.getWarehouseIds().contains(warehouseId)) {
            throw new BizException(ResultCode.NO_PERMISSION, "仓库不在您的管辖范围内");
        }
        return warehouseId;
    }

    /**
     * 构建明细行并逐项校验（M4-1-2、M4-1-3）：
     * 行必须属于该订单且未关闭；到货量大于 0 且不超过剩余到货配额
     * （订单量 − 其他验收单累计到货，本次草稿编辑时排除自身）；
     * 合格/不合格缺省全合格，守恒在提交时校验（R12）。
     * excludeInspectionId 非空表示编辑草稿：配额扣减排除本单。
     */
    private List<ArrivalInspectionItem> buildItems(PurchaseOrder order,
                                                   List<ArrivalItemSaveRequest> requests, Long excludeInspectionId) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请至少登记一行到货明细");
        }
        List<PurchaseOrderItem> orderRows = orderItemMapper.selectByOrderId(order.getId());
        Map<Long, PurchaseOrderItem> orderItems = orderRows.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity(), (a, b) -> a));
        Set<Long> seen = new HashSet<>();
        for (ArrivalItemSaveRequest request : requests) {
            if (request.getOrderItemId() == null) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "请选择订单明细行");
            }
            if (!seen.add(request.getOrderItemId())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "同一订单行在一次登记里只能出现一次");
            }
        }
        List<Long> orderItemIds = List.copyOf(seen);
        // 到货配额：订单量 − 该行其他验收单累计到货（排除正在编辑的本单）
        Map<Long, BigDecimal> arrived = sumArrivalQtyExcluding(order.getId(), orderItemIds, excludeInspectionId);

        List<ArrivalInspectionItem> items = new ArrayList<>();
        for (ArrivalItemSaveRequest request : requests) {
            if (request.getOrderItemId() == null) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "请选择订单明细行");
            }
            PurchaseOrderItem orderItem = orderItems.get(request.getOrderItemId());
            if (orderItem == null) {
                throw new BizException(ResultCode.NOT_FOUND, "订单明细行不存在或不属于本订单");
            }
            if (Integer.valueOf(ITEM_FULFILL_CLOSED).equals(orderItem.getItemFulfillStatus())) {
                throw new BizException(ResultCode.UNSUPPORTED, "订单行已关闭，不能登记到货");
            }
            if (request.getArrivalQty() == null || request.getArrivalQty().signum() <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "到货数量必须大于 0");
            }
            BigDecimal left = nz(orderItem.getQuantity())
                    .subtract(arrived.getOrDefault(request.getOrderItemId(), BigDecimal.ZERO));
            if (request.getArrivalQty().compareTo(left) > 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "到货数量超过剩余到货量 " + left.toPlainString() + "（订单行 "
                                + orderItem.getItemNo() + "）");
            }
            BigDecimal arrival = request.getArrivalQty();
            BigDecimal qualified;
            BigDecimal unqualified;
            if (request.getQualifiedQty() == null && request.getUnqualifiedQty() == null) {
                // 只记到货量时缺省全合格，守恒自然成立
                qualified = arrival;
                unqualified = BigDecimal.ZERO;
            } else {
                // 显式判定时必须满足守恒律 R12：合格 + 不合格 = 到货
                qualified = request.getQualifiedQty() == null ? BigDecimal.ZERO : request.getQualifiedQty();
                unqualified = request.getUnqualifiedQty() == null ? BigDecimal.ZERO : request.getUnqualifiedQty();
                if (qualified.add(unqualified).compareTo(arrival) != 0) {
                    throw new BizException(ResultCode.INVALID_PARAMETER,
                            "合格数量加不合格数量必须等于到货数量（守恒律）");
                }
            }
            if (qualified.signum() < 0 || unqualified.signum() < 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "合格与不合格数量不能为负");
            }
            ArrivalInspectionItem item = new ArrivalInspectionItem();
            item.setOrderItemId(orderItem.getId());
            item.setMaterialId(orderItem.getMaterialId());
            item.setUnitId(orderItem.getUnitId());
            item.setArrivalQty(request.getArrivalQty());
            item.setQualifiedQty(qualified);
            item.setUnqualifiedQty(unqualified);
            item.setBatchNo(request.getBatchNo());
            item.setOwnerType(orderItem.getOwnerType());
            item.setOwnerId(orderItem.getOwnerId());
            item.setOwnerNo(orderItem.getOwnerNo());
            item.setRemark(request.getRemark());
            items.add(item);
        }
        return items;
    }

    private void saveItems(Long inspectionId, List<ArrivalInspectionItem> items) {
        for (ArrivalInspectionItem item : items) {
            item.setInspectionId(inspectionId);
            inspectionItemMapper.insert(item);
        }
    }

    private int normalizeMode(Integer mode) {
        int value = mode == null ? MODE_SAMPLE : mode;
        if (value != MODE_SKIP && value != MODE_SAMPLE && value != MODE_FULL) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "验收方式不合法（免检/抽样/全检）");
        }
        return value;
    }

    /** 整单结果由明细重算：全合格 → 1；让步 → 3；有不合格未让步 → 2。 */
    private int recomputeResult(ArrivalInspection bill) {
        if (Integer.valueOf(1).equals(bill.getConcessionFlag())) {
            return RESULT_CONCESSION;
        }
        return nz(bill.getUnqualifiedQty()).signum() > 0 ? RESULT_UNQUALIFIED : RESULT_QUALIFIED;
    }

    private ArrivalItemVO buildArrivalItemVO(PurchaseOrderItem orderItem,
                                             BigDecimal arrivedQty, BigDecimal remaining) {
        ArrivalItemVO vo = new ArrivalItemVO();
        vo.setOrderItemId(orderItem.getId());
        vo.setOrderItemNo(orderItem.getItemNo());
        vo.setMaterialId(orderItem.getMaterialId());
        vo.setUnitId(orderItem.getUnitId());
        vo.setOrderQty(orderItem.getQuantity());
        vo.setArrivedQty(arrivedQty);
        vo.setRemainingArrival(remaining);
        vo.setInboundableQty(remaining);
        List<ArrivalItemVO> single = List.of(vo);
        fillMaterialNames(single, ArrivalItemVO::getMaterialId, ArrivalItemVO::setMaterialNo,
                ArrivalItemVO::setMaterialName, ArrivalItemVO::setSpec);
        fillUnitNames(single, ArrivalItemVO::getUnitId, ArrivalItemVO::setUnitName);
        return vo;
    }

    private Map<Long, BigDecimal> sumArrivalQty(Long orderId, List<Long> orderItemIds) {
        return sumArrivalQtyExcluding(orderId, orderItemIds, null);
    }

    /**
     * 累计到货量。草稿也计入配额（防同时建草稿超发）；
     * excludeInspectionId 非空（编辑草稿）时排除本单已落库的行。
     */
    private Map<Long, BigDecimal> sumArrivalQtyExcluding(Long orderId, List<Long> orderItemIds,
                                                         Long excludeInspectionId) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        for (ArrivalInspection inspection : inspectionMapper.selectByOrderId(orderId)) {
            if (excludeInspectionId != null && excludeInspectionId.equals(inspection.getId())) {
                continue;
            }
            for (ArrivalInspectionItem item :
                    inspectionItemMapper.selectByInspectionId(inspection.getId())) {
                result.merge(item.getOrderItemId(), nz(item.getArrivalQty()), BigDecimal::add);
            }
        }
        return result;
    }

    /** 累计已生成入库量（草稿 + 已确认，不含作废）。 */
    private Map<Long, BigDecimal> sumGeneratedQty(List<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        for (InboundItemMapper.GeneratedQtyRow row :
                inboundItemMapper.sumGeneratedQtyByOrderItems(orderItemIds)) {
            result.put(row.getOrderItemId(), row.getGeneratedQty());
        }
        return result;
    }

    private BigDecimal sum(List<ArrivalInspectionItem> items,
                           Function<ArrivalInspectionItem, BigDecimal> getter) {
        return items.stream().map(getter).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String modeName(Integer mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case MODE_SKIP -> "免检";
            case MODE_SAMPLE -> "抽样";
            case MODE_FULL -> "全检";
            default -> "未知";
        };
    }

    private String resultName(Integer result) {
        if (result == null) {
            return null;
        }
        return switch (result) {
            case RESULT_QUALIFIED -> "合格";
            case RESULT_UNQUALIFIED -> "不合格";
            case RESULT_CONCESSION -> "让步接收";
            default -> "未知";
        };
    }

    private String inboundStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case INBOUND_DRAFT -> "草稿";
            case INBOUND_CONFIRMED -> "已确认";
            default -> "已作废";
        };
    }

    // ================= 私有：名称回填 =================

    private <T> void fillMaterialNames(List<T> rows, Function<T, Long> idGetter,
                                       java.util.function.BiConsumer<T, String> noSetter,
                                       java.util.function.BiConsumer<T, String> nameSetter,
                                       java.util.function.BiConsumer<T, String> specSetter) {
        List<Long> ids = rows.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        Map<Long, Material> byId = batchByIds(ids, materialMapper::selectByIds, Material::getId);
        for (T row : rows) {
            Material material = byId.get(idGetter.apply(row));
            noSetter.accept(row, material == null ? null : material.getMaterialNo());
            nameSetter.accept(row, material == null ? null : material.getMaterialName());
            specSetter.accept(row, material == null ? null : material.getSpec());
        }
    }

    /** 计量单位是字典项（与订单服务同口径），unit_id 即 dict_item.id */
    private <T> void fillUnitNames(List<T> rows, Function<T, Long> idGetter,
                                   java.util.function.BiConsumer<T, String> setter) {
        List<Long> ids = rows.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        Map<Long, DictItem> byId = batchByIds(ids, dictItemMapper::selectByIds, DictItem::getId);
        for (T row : rows) {
            DictItem dict = byId.get(idGetter.apply(row));
            setter.accept(row, dict == null ? null : dict.getItemName());
        }
    }

    private <T> void fillSupplierNames(List<T> rows, Function<T, Long> idGetter,
                                       java.util.function.BiConsumer<T, String> setter) {
        List<Long> ids = rows.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        Map<Long, Supplier> byId = batchByIds(ids, supplierMapper::selectByIds, Supplier::getId);
        for (T row : rows) {
            Supplier supplier = byId.get(idGetter.apply(row));
            setter.accept(row, supplier == null ? null : supplier.getSupplierName());
        }
    }

    private <T> void fillWarehouseNames(List<T> rows, Function<T, Long> idGetter,
                                        java.util.function.BiConsumer<T, String> setter) {
        List<Long> ids = rows.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        Map<Long, Warehouse> byId = batchByIds(ids, warehouseMapper::selectByIds, Warehouse::getId);
        for (T row : rows) {
            Warehouse warehouse = byId.get(idGetter.apply(row));
            setter.accept(row, warehouse == null ? null : warehouse.getWarehouseName());
        }
    }

    private <T> void fillUserNames(List<T> rows, Function<T, Long> idGetter,
                                   java.util.function.BiConsumer<T, String> setter) {
        List<Long> ids = rows.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> byId = batchByIds(ids, userMapper::selectByIds, SysUser::getId);
        for (T row : rows) {
            SysUser user = byId.get(idGetter.apply(row));
            setter.accept(row, user == null ? null : user.getRealName());
        }
    }

    /** 批量加载并按主键建索引，空 id 集合不查库。 */
    private <E> Map<Long, E> batchByIds(List<Long> ids,
                                        Function<Collection<Long>, List<E>> loader,
                                        Function<E, Long> idGetter) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loader.apply(ids).stream()
                .collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));
    }

    // ================= 私有：单号与重试 =================

    private String nextInspectionNo() {
        return billNoGenerator.next("bill_no_rule_inspection", inspectionMapper::maxSeqByPrefix);
    }

    private String nextInboundNo() {
        return billNoGenerator.next("bill_no_rule_inbound", inboundOrderMapper::maxSeqByPrefix);
    }

    /** 单号撞唯一键时重新取流水重试，与订单同口径（NO_ATTEMPTS 次）。 */
    private void insertWithRetry(ArrivalInspection bill) {
        for (int attempt = 1; attempt <= NO_ATTEMPTS; attempt++) {
            bill.setInspectionNo(nextInspectionNo());
            try {
                inspectionMapper.insert(bill);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == NO_ATTEMPTS) {
                    throw new BizException(ResultCode.AMBIGUOUS, "验收单号生成冲突，请重试");
                }
            }
        }
    }

    private void insertInboundWithRetry(InboundOrder inbound) {
        for (int attempt = 1; attempt <= NO_ATTEMPTS; attempt++) {
            inbound.setInboundNo(nextInboundNo());
            try {
                inboundOrderMapper.insert(inbound);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == NO_ATTEMPTS) {
                    throw new BizException(ResultCode.AMBIGUOUS, "入库单号生成冲突，请重试");
                }
            }
        }
    }

    // ================= 私有：加载与范围 =================

    /**
     * 加载验收单。只做存在性校验，不注入数据范围：
     * 范围是查询层概念——列表按仓库维度过滤（page），单据详情与
     * 动作由权限点把关（M4-4-2 明细查看授给全部角色，
     * M4-2-4 让步归主管、登记与入库归仓管员）。
     * 跨维度角色（部门范围的主管）要能查看并处置仓库维度单据，
     * 在详情上套仓库范围会把让步接收挡死。
     */
    private ArrivalInspection requireInScope(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "缺少验收单id");
        }
        ArrivalInspection bill = inspectionMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "到货验收单不存在");
        }
        return bill;
    }

    private ArrivalInspection requireDraft(Long id) {
        ArrivalInspection bill = requireInScope(id);
        if (!Integer.valueOf(STATUS_DRAFT).equals(bill.getStatus())) {
            throw new BizException(ResultCode.UNSUPPORTED, "仅草稿状态的验收单可以修改");
        }
        return bill;
    }

    private ScopeCondition currentScope() {
        CurrentUser user = AuthContext.get();
        return user == null ? new ScopeCondition() : user.getScope();
    }

    private Long currentUserId() {
        return AuthContext.getOrThrow().getUserId();
    }
}
