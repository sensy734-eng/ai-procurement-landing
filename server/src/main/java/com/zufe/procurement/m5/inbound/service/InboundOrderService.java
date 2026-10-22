package com.zufe.procurement.m5.inbound.service;

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
import com.zufe.procurement.m1.owner.entity.OwnerObject;
import com.zufe.procurement.m1.owner.mapper.OwnerObjectMapper;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.entity.WarehouseLocation;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseLocationMapper;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m11.system.service.OperationLogService;
import com.zufe.procurement.m2.request.entity.PurchaseRequest;
import com.zufe.procurement.m2.request.mapper.PurchaseRequestMapper;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import com.zufe.procurement.m3.order.entity.PurchaseOrderItem;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderItemMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m3.order.service.PurchaseOrderService;
import com.zufe.procurement.m4.arrival.entity.ArrivalInspection;
import com.zufe.procurement.m4.arrival.mapper.ArrivalInspectionMapper;
import com.zufe.procurement.m5.inbound.dto.InboundQuery;
import com.zufe.procurement.m5.inbound.dto.InboundSaveRequest;
import com.zufe.procurement.m5.inbound.dto.InboundVoidRequest;
import com.zufe.procurement.m5.inbound.entity.InboundItem;
import com.zufe.procurement.m5.inbound.entity.InboundOrder;
import com.zufe.procurement.m5.inbound.mapper.InboundItemMapper;
import com.zufe.procurement.m5.inbound.mapper.InboundOrderMapper;
import com.zufe.procurement.m5.inbound.vo.InboundVO;
import com.zufe.procurement.m5.stock.entity.StockBalance;
import com.zufe.procurement.m5.stock.entity.StockFlow;
import com.zufe.procurement.m5.stock.mapper.StockBalanceMapper;
import com.zufe.procurement.m5.stock.mapper.StockFlowMapper;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购入库服务，覆盖功能点 M5-1 采购入库单（M5-1-1 至 M5-1-5）、
 * M5-2 批次与库位登记（M5-2-1、M5-2-2，作为明细行字段在新建/编辑时登记）、
 * M5-4 入库流水写入（M5-4-1，确认时同事务落流水）。
 * 库存余额读写在 StockService（查询侧）与本类的确认/作废（写入侧）；
 * 一致性保障 M5-5-3（重复确认防护）与 M5-5-4（乐观锁并发控制）在 confirm 落地。
 *
 * 业务口径集中在本层：
 * - **库存唯一写入点（R2）**：quantity 只由 confirm（加）与 voidOrder（冲减）
 *   维护，同事务写 stock_flow（before/after/change），草稿不碰库存表；
 * - 确认入库同时回写订单行 delivered_qty 并重算履约（R2 后半），
 *   作废冲减是它的逆操作（确认+作废一对逆操作，同事务保证一致）；
 * - 重复确认防护（M5-5-3）：状态机闸门，只有草稿可确认，已确认再确认
 *   直接拒（Unsupported），不存在「同一单据重复更新余额」的窗口；
 * - 并发控制（M5-5-4）：stock_balance.version 乐观锁，update 影响行数
 *   为 0 时重试，三次仍冲突报 Unsupported；并发首插撞唯一键走
 *   「重新查 + 乐观锁更新」兜底，不覆盖不超发；
 * - 明细完整性（M5-1-3 前置）：确认时逐行校验数量为正、库位已登记；
 *   批次号可空串（无批次管理），但一经确认即进入库存唯一键；
 * - 带订单行的明细受入库配额约束（订单量 − 累计已生成，与 M4 同口径），
 *   纯手工单（无订单行）不校验配额——公共入库/补录场景；
 * - 数据范围：列表按仓库维度注入，但只对「拥有仓库范围」的用户生效
 *   （仓管员口径）；部门维度的采购员/主管与 admin 不过滤——
 *   入库单不是部门维度实体，清单 M5-1-2 明确授权这些角色查询；
 * - 作废的「未发生后续出库」：本系统无出库模块，闸门落在冲减后
 *   余额非负（该批次库存未被后续业务占用）；作废后入库配额自动释放
 *   （累计已生成只统计草稿+已确认）。
 */
@Service
public class InboundOrderService {

    /** 乐观锁最大重试次数（M5-5-4） */
    private static final int MAX_RETRY = 3;

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundItemMapper inboundItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseRequestMapper requestMapper;
    private final ArrivalInspectionMapper arrivalInspectionMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final DictItemMapper dictItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper locationMapper;
    private final OwnerObjectMapper ownerObjectMapper;
    private final SysUserMapper userMapper;
    private final StockBalanceMapper stockBalanceMapper;
    private final StockFlowMapper stockFlowMapper;
    private final BillNoGenerator billNoGenerator;
    private final DataScopeService dataScopeService;
    private final OperationLogService operationLogService;

    public InboundOrderService(InboundOrderMapper inboundOrderMapper,
                               InboundItemMapper inboundItemMapper,
                               PurchaseOrderMapper orderMapper,
                               PurchaseOrderItemMapper orderItemMapper,
                               PurchaseOrderService purchaseOrderService,
                               PurchaseRequestMapper requestMapper,
                               ArrivalInspectionMapper arrivalInspectionMapper,
                               MaterialMapper materialMapper,
                               SupplierMapper supplierMapper,
                               DictItemMapper dictItemMapper,
                               WarehouseMapper warehouseMapper,
                               WarehouseLocationMapper locationMapper,
                               OwnerObjectMapper ownerObjectMapper,
                               SysUserMapper userMapper,
                               StockBalanceMapper stockBalanceMapper,
                               StockFlowMapper stockFlowMapper,
                               BillNoGenerator billNoGenerator,
                               DataScopeService dataScopeService,
                               OperationLogService operationLogService) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundItemMapper = inboundItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.purchaseOrderService = purchaseOrderService;
        this.requestMapper = requestMapper;
        this.arrivalInspectionMapper = arrivalInspectionMapper;
        this.materialMapper = materialMapper;
        this.supplierMapper = supplierMapper;
        this.dictItemMapper = dictItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.ownerObjectMapper = ownerObjectMapper;
        this.userMapper = userMapper;
        this.stockBalanceMapper = stockBalanceMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.billNoGenerator = billNoGenerator;
        this.dataScopeService = dataScopeService;
        this.operationLogService = operationLogService;
    }

    // ================= M5-1-2 入库单查看与检索 =================

    /**
     * 分页列表。数据范围按仓库维度注入（仅对拥有仓库范围的用户，
     * 见类注释）；状态/仓库/日期/来源订单筛选；每行带明细合计。
     */
    public PageResult<InboundVO> page(InboundQuery query) {
        Page<InboundOrder> page = new Page<>(Math.max(query.getPageNo(), 1),
                Math.min(Math.max(query.getPageSize(), 1), 100));
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<InboundOrder>()
                .like(StringUtils.hasText(query.getKeyword()), InboundOrder::getInboundNo,
                        query.getKeyword() == null ? null : query.getKeyword().trim())
                .eq(query.getWarehouseId() != null, InboundOrder::getWarehouseId, query.getWarehouseId())
                .eq(query.getStatus() != null, InboundOrder::getStatus, query.getStatus())
                .eq(query.getOrderId() != null, InboundOrder::getOrderId, query.getOrderId())
                .ge(query.getDateFrom() != null, InboundOrder::getInboundDate, query.getDateFrom())
                .le(query.getDateTo() != null, InboundOrder::getInboundDate, query.getDateTo())
                .orderByDesc(InboundOrder::getInboundDate)
                .orderByDesc(InboundOrder::getId);
        applyWarehouseScope(wrapper);
        Page<InboundOrder> result = inboundOrderMapper.selectPage(page, wrapper);
        List<InboundOrder> bills = result.getRecords();
        if (bills.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        return PageResult.of(assembleList(bills), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * M5-1-2 详情：主表 + 明细行（物料/单位/库位名）+ 来源链 + 库存流水摘要。
     * 详情不套数据范围——单据级查看由权限点 m5:inbound:view 把关
     * （与 M4 详情同口径：范围是查询层概念，动作与详情由权限点把关）。
     */
    public InboundVO detail(Long id) {
        InboundOrder bill = requireBill(id);
        return assembleBill(bill, true, true);
    }

    // ================= M5-1-1 新建 / 编辑入库单 =================

    /**
     * 手工新建入库单（草稿）。明细行要么全部带订单行（关联单，
     * 主表带 order/supplier，受入库配额约束），要么全部不带（纯手工单，
     * 公共入库/补录场景，不受配额约束）；不接受混合。
     */
    @Transactional
    public Long create(InboundSaveRequest request) {
        InboundOrder bill = new InboundOrder();
        bill.setInboundNo(nextInboundNo());
        fillBillFromRequest(bill, request, null);
        bill.setStatus(1);
        bill.setOperatorId(currentUserId());
        inboundOrderMapper.insert(bill);
        saveItems(bill, request.getItems(), null);
        // saveItems 里按明细行回填了订单/供应商/验收单快照，但 insert 已经执行，
        // 这些字段只在内存里——必须补一次更新落库，否则溯源链与流水摘要
        // 都拿不到来源单（M5-1-5 依赖）。同事务内完成，不存在中间态。
        LambdaUpdateWrapper<InboundOrder> patch = new LambdaUpdateWrapper<InboundOrder>()
                .eq(InboundOrder::getId, bill.getId())
                .set(InboundOrder::getOrderId, bill.getOrderId())
                .set(InboundOrder::getOrderNo, bill.getOrderNo())
                .set(InboundOrder::getSupplierId, bill.getSupplierId())
                .set(InboundOrder::getInspectionId, bill.getInspectionId())
                .set(InboundOrder::getInspectionNo, bill.getInspectionNo());
        inboundOrderMapper.update(null, patch);
        operationLogService.record("M5", "create", "inbound_order", bill.getId(),
                bill.getInboundNo(), "新建入库单草稿，" + request.getItems().size() + " 行明细");
        return bill.getId();
    }

    /**
     * 编辑入库单（M5-1-1）。仅草稿状态可编辑；明细行整体替换
     * （旧行逻辑删除后重插，配额校验按「不含本单」口径，见 checkQuota）。
     * 批次号（M5-2-1）与库位（M5-2-2）在明细行上登记。
     */
    @Transactional
    public void update(Long id, InboundSaveRequest request) {
        InboundOrder bill = requireEditableBill(id);
        fillBillFromRequest(bill, request, bill.getId());
        LambdaUpdateWrapper<InboundOrder> wrapper = new LambdaUpdateWrapper<InboundOrder>()
                .eq(InboundOrder::getId, id)
                .set(InboundOrder::getWarehouseId, bill.getWarehouseId())
                .set(InboundOrder::getInboundDate, bill.getInboundDate())
                .set(InboundOrder::getRemark, bill.getRemark())
                .set(InboundOrder::getOrderId, bill.getOrderId())
                .set(InboundOrder::getOrderNo, bill.getOrderNo())
                .set(InboundOrder::getSupplierId, bill.getSupplierId())
                .set(InboundOrder::getInspectionId, bill.getInspectionId())
                .set(InboundOrder::getInspectionNo, bill.getInspectionNo());
        inboundOrderMapper.update(null, wrapper);
        saveItems(bill, request.getItems(), id);
        operationLogService.record("M5", "update", "inbound_order", id,
                bill.getInboundNo(), "编辑入库单草稿，" + request.getItems().size() + " 行明细");
    }

    // ================= M5-1-3 确认入库（库存唯一写入点） =================

    /**
     * 确认入库。同事务内完成：状态机 1 → 2（M5-5-3 重复确认防护）、
     * 逐行更新库存余额并写流水（M5-4-1）、回写订单行已交付数量
     * 与履约（R2 后半）。任一步失败整体回滚——不存在「单确认了
     * 库存没写」的中间态。
     */
    @Transactional
    public void confirm(Long id) {
        InboundOrder bill = requireBill(id);
        if (!Objects.equals(bill.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, bill.getStatus() != null && bill.getStatus() == 2
                    ? "该入库单已确认，不能重复确认（重复入库防护）"
                    : "已作废的入库单不能确认");
        }
        List<InboundItem> items = inboundItemMapper.selectByInboundId(id);
        if (items.isEmpty()) {
            throw new BizException(ResultCode.UNSUPPORTED, "入库单没有明细行，不能确认");
        }
        for (InboundItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "明细入库数量必须大于 0");
            }
            if (item.getLocationId() == null) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "明细行尚未登记库位，请先编辑入库单补登库位再确认");
            }
        }
        for (InboundItem item : items) {
            applyStockDelta(item, bill.getWarehouseId(), item.getQuantity(), bill, 1);
            purchaseOrderService.applyDeliveredQty(item.getOrderItemId(), item.getQuantity());
        }
        LambdaUpdateWrapper<InboundOrder> wrapper = new LambdaUpdateWrapper<InboundOrder>()
                .eq(InboundOrder::getId, id)
                .set(InboundOrder::getStatus, 2)
                .set(InboundOrder::getConfirmTime, LocalDateTime.now())
                .set(InboundOrder::getConfirmBy, currentUserId());
        inboundOrderMapper.update(null, wrapper);
        operationLogService.record("M5", "confirm", "inbound_order", id, bill.getInboundNo(),
                "入库确认，" + items.size() + " 行明细写入库存并登记流水");
    }

    // ================= M5-1-4 作废入库单 =================

    /**
     * 作废已确认入库单（M5-1-4）。作废是确认的逆操作：同事务冲减库存
     * （写 change_type=2 的冲减流水，不删原入库流水）、回退订单行
     * 已交付数量与履约。「未发生后续出库」的闸门落在冲减后余额非负
     * ——本系统无出库模块，若该批次库存已被占用（余额不足）即拒。
     * 作废后入库配额自动释放（累计已生成只统计草稿+已确认）。
     */
    @Transactional
    public void voidOrder(Long id, InboundVoidRequest request) {
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "作废原因必填");
        }
        InboundOrder bill = requireBill(id);
        if (!Objects.equals(bill.getStatus(), 2)) {
            throw new BizException(ResultCode.UNSUPPORTED, "只有已确认的入库单可以作废");
        }
        List<InboundItem> items = inboundItemMapper.selectByInboundId(id);
        for (InboundItem item : items) {
            applyStockDelta(item, bill.getWarehouseId(), item.getQuantity().negate(), bill, 2);
            purchaseOrderService.applyDeliveredQty(item.getOrderItemId(), item.getQuantity().negate());
        }
        LambdaUpdateWrapper<InboundOrder> wrapper = new LambdaUpdateWrapper<InboundOrder>()
                .eq(InboundOrder::getId, id)
                .set(InboundOrder::getStatus, 3)
                .set(InboundOrder::getVoidTime, LocalDateTime.now())
                .set(InboundOrder::getVoidReason, request.getReason().trim());
        inboundOrderMapper.update(null, wrapper);
        operationLogService.record("M5", "void", "inbound_order", id, bill.getInboundNo(),
                "作废入库单并冲减库存：" + request.getReason().trim());
    }

    // ================= M5-1-5 入库单溯源 =================

    /**
     * 溯源链：入库单 → 验收单 → 订单 → 申请单 → 申请人。任一级没有关联
     * （手工建单）时链上对应字段为 null，不报错——溯源是展示不是校验。
     * 申请人取申请单的 applicant_id（不是订单的 source_request_id——
     * 那是申请单主键，直接当用户 id 查会查到错误的人）。
     */
    public InboundVO.TraceChain trace(Long id) {
        InboundOrder bill = requireBill(id);
        InboundVO.TraceChain chain = new InboundVO.TraceChain();
        chain.setInboundId(bill.getId());
        chain.setInboundNo(bill.getInboundNo());
        chain.setInspectionId(bill.getInspectionId());
        chain.setInspectionNo(bill.getInspectionNo());
        if (bill.getOrderId() != null) {
            PurchaseOrder order = orderMapper.selectById(bill.getOrderId());
            if (order != null) {
                chain.setOrderId(order.getId());
                chain.setOrderNo(order.getOrderNo());
                chain.setRequestId(order.getSourceRequestId());
                chain.setRequestNo(order.getSourceRequestNo());
                if (order.getSourceRequestId() != null) {
                    PurchaseRequest request = requestMapper.selectById(order.getSourceRequestId());
                    if (request != null && request.getApplicantId() != null) {
                        SysUser applicant = userMapper.selectById(request.getApplicantId());
                        if (applicant != null) {
                            chain.setApplicantName(applicant.getRealName());
                            chain.setDeptName(deptName(request.getDeptId()));
                        }
                    }
                }
            }
        }
        return chain;
    }

    /**
     * 部门名走部门字典（dict type=dept）：数据里的 dept_id 就是字典项 id
     * （1101=采购部），与数据范围配置的解析口径一致——按项 id 匹配，
     * 不是按项编码（D001 那套编码只在导入模板里用）。
     */
    private String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        for (DictItem item : dictItemMapper.selectByTypeCode("dept")) {
            if (Objects.equals(item.getId(), deptId)) {
                return item.getItemName();
            }
        }
        return null;
    }

    // ================= M5-4-3 来源单据回溯 =================

    /**
     * 流水分录的来源入库单详情（M5-4-3 回溯跳转）：
     * 流水 → source_id → 入库单。流水不存在时 NotFound。
     */
    public InboundVO flowSource(Long flowId) {
        if (flowId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "缺少流水id");
        }
        StockFlow flow = stockFlowMapper.selectById(flowId);
        if (flow == null) {
            throw new BizException(ResultCode.NOT_FOUND, "库存流水不存在");
        }
        return assembleBill(requireBill(flow.getSourceId()), true, true);
    }

    // ================= M5-2-1 批次号规则生成 =================

    /**
     * 按规则生成批次号：B + yyyyMMdd + 3 位当日流水。
     * 规则参数化在参数表无对应项（批次不是单据号），按仓储惯例取
     * 固定格式；同前缀取最大流水 + 1（含历史行，避免回收撞号）。
     */
    public String nextBatchNo() {
        String prefix = "B" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        Integer max = inboundItemMapper.maxBatchSeqByPrefix(prefix, prefix.length() + 1);
        int seq = (max == null ? 0 : max) + 1;
        return prefix + String.format("%03d", seq);
    }

    // ================= 私有：主表与明细 =================

    /** 校验并回填主表字段；editingId 非空表示编辑（配额校验要排除本单已计量行）。 */
    private void fillBillFromRequest(InboundOrder bill, InboundSaveRequest request, Long editingId) {
        if (request.getWarehouseId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择入库仓库");
        }
        Warehouse warehouse = warehouseMapper.selectById(request.getWarehouseId());
        if (warehouse == null || !Objects.equals(warehouse.getStatus(), 1)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "入库仓库不存在或已停用");
        }
        if (!StringUtils.hasText(request.getInboundDate())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择入库日期");
        }
        LocalDate inboundDate;
        try {
            inboundDate = LocalDate.parse(request.getInboundDate().trim());
        } catch (Exception e) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "入库日期格式应为 yyyy-MM-dd");
        }
        bill.setWarehouseId(request.getWarehouseId());
        bill.setInboundDate(inboundDate);
        bill.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
    }

    /**
     * 明细行整体替换：旧行逻辑删除后重插。三类校验：
     * 行数据完整（物料/单位/数量）、库位归属本单仓库（M5-2-2）、
     * 带订单行的受入库配额约束（与 M4 同口径，编辑时排除本单）。
     */
    private void saveItems(InboundOrder bill, List<InboundSaveRequest.Item> requests, Long editingId) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请至少登记一行入库明细");
        }
        List<Long> orderItemIds = requests.stream()
                .map(InboundSaveRequest.Item::getOrderItemId).filter(Objects::nonNull).distinct().toList();
        if (orderItemIds.size() != requests.stream()
                .filter(r -> r.getOrderItemId() != null).count()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "同一订单行在一次登记里只能出现一次");
        }
        boolean allWithOrder = orderItemIds.size() == requests.size();
        boolean allWithoutOrder = orderItemIds.isEmpty();
        if (!allWithOrder && !allWithoutOrder) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "关联单入库与手工入库不能混在一张单里：要么全部选订单行，要么全部不选");
        }

        Map<Long, PurchaseOrderItem> orderItems = Map.of();
        Map<Long, PurchaseOrder> orders = Map.of();
        if (allWithOrder) {
            List<PurchaseOrderItem> rows = orderItemMapper.selectBatchIds(orderItemIds);
            orderItems = rows.stream().collect(Collectors.toMap(PurchaseOrderItem::getId,
                    Function.identity(), (a, b) -> a));
            for (Long id : orderItemIds) {
                if (!orderItems.containsKey(id)) {
                    throw new BizException(ResultCode.NOT_FOUND, "订单明细行不存在");
                }
            }
            Set<Long> orderIds = orderItems.values().stream()
                    .map(PurchaseOrderItem::getOrderId).collect(Collectors.toSet());
            orders = orderMapper.selectBatchIds(orderIds).stream()
                    .collect(Collectors.toMap(PurchaseOrder::getId, Function.identity(), (a, b) -> a));
            for (PurchaseOrderItem row : orderItems.values()) {
                PurchaseOrder order = orders.get(row.getOrderId());
                if (order == null || !Objects.equals(order.getLifecycleStatus(), 3)) {
                    throw new BizException(ResultCode.UNSUPPORTED, "只有已审核的订单可以入库");
                }
            }
        }

        // 物料与单位存在性
        Set<Long> materialIds = requests.stream()
                .map(InboundSaveRequest.Item::getMaterialId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Material> materials = materialIds.isEmpty() ? Map.of()
                : materialMapper.selectBatchIds(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));

        // 库位归属校验（M5-2-2）
        Set<Long> locationIds = requests.stream()
                .map(InboundSaveRequest.Item::getLocationId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, WarehouseLocation> locations = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(WarehouseLocation::getId, Function.identity(), (a, b) -> a));
        for (InboundSaveRequest.Item row : requests) {
            if (row.getMaterialId() == null || !materials.containsKey(row.getMaterialId())) {
                throw new BizException(ResultCode.NOT_FOUND, "物料不存在");
            }
            if (row.getUnitId() == null) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "请选择计量单位");
            }
            if (row.getQuantity() == null || row.getQuantity().signum() <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "入库数量必须大于 0");
            }
            if (row.getLocationId() != null) {
                WarehouseLocation location = locations.get(row.getLocationId());
                if (location == null || !Objects.equals(location.getWarehouseId(), bill.getWarehouseId())) {
                    throw new BizException(ResultCode.UNSUPPORTED, "库位不属于本单仓库，请重新选择");
                }
            }
        }

        // 入库配额：订单量 − 累计已生成（草稿+已确认，不含作废），编辑时排除本单
        if (allWithOrder) {
            checkInboundQuota(orderItems, requests, editingId);
        }

        // 关联单：主表补订单与供应商（混合单已在上面拦掉）
        if (allWithOrder) {
            PurchaseOrderItem first = orderItems.get(orderItemIds.get(0));
            PurchaseOrder order = orders.get(first.getOrderId());
            bill.setOrderId(order.getId());
            bill.setOrderNo(order.getOrderNo());
            bill.setSupplierId(order.getSupplierId());
            bill.setInspectionId(null);
            bill.setInspectionNo(null);
        } else {
            bill.setOrderId(null);
            bill.setOrderNo(null);
            bill.setSupplierId(null);
            bill.setInspectionId(null);
            bill.setInspectionNo(null);
        }

        if (editingId != null) {
            LambdaUpdateWrapper<InboundItem> clear = new LambdaUpdateWrapper<InboundItem>()
                    .eq(InboundItem::getInboundId, editingId)
                    .set(InboundItem::getIsDeleted, 1);
            inboundItemMapper.update(null, clear);
        }
        for (InboundSaveRequest.Item row : requests) {
            InboundItem item = new InboundItem();
            item.setInboundId(bill.getId());
            item.setOrderItemId(row.getOrderItemId());
            item.setMaterialId(row.getMaterialId());
            item.setQuantity(row.getQuantity().setScale(4, java.math.RoundingMode.HALF_UP));
            item.setUnitId(row.getUnitId());
            item.setBatchNo(StringUtils.hasText(row.getBatchNo()) ? row.getBatchNo().trim() : "");
            item.setLocationId(row.getLocationId());
            resolveOwner(item, row);
            item.setRemark(StringUtils.hasText(row.getRemark()) ? row.getRemark().trim() : null);
            inboundItemMapper.insert(item);
        }
    }

    /**
     * 归属对象解析（库存唯一键的一部分，口径必须与订单/验收单一致）：
     * 公共库存取 owner_object 中 owner_type=1 的「公共库存」行——
     * 订单行、验收行、演示库存都是这个口径（owner_id=1），
     * 早期版本把公共库存硬编码成 owner_id=0，会让同一批公共库存裂成
     * 两个余额键（owner-group 拆行、对账多键、可用量虽合计但归属视图乱），
     * 项目/工单库存必须显式指定归属对象且类型匹配。
     */
    private void resolveOwner(InboundItem item, InboundSaveRequest.Item row) {
        int ownerType = row.getOwnerType() == null ? 1 : row.getOwnerType();
        if (ownerType == 1) {
            OwnerObject publicOwner = publicOwner();
            item.setOwnerType(1);
            item.setOwnerId(publicOwner.getId());
            item.setOwnerNo(publicOwner.getOwnerNo());
            return;
        }
        if (row.getOwnerId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "项目/工单库存必须选择归属对象");
        }
        OwnerObject owner = ownerObjectMapper.selectById(row.getOwnerId());
        if (owner == null || !Objects.equals(owner.getOwnerType(), ownerType)
                || !Objects.equals(owner.getStatus(), 1)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "归属对象不存在、已停用或与归属类型不匹配");
        }
        item.setOwnerType(ownerType);
        item.setOwnerId(owner.getId());
        item.setOwnerNo(owner.getOwnerNo());
    }

    /** 公共归属对象（owner_type=1 的启用行）；基础数据缺失时明确报错，不静默用 0 裂键。 */
    private OwnerObject publicOwner() {
        List<OwnerObject> publics = ownerObjectMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OwnerObject>()
                        .eq(OwnerObject::getOwnerType, 1)
                        .eq(OwnerObject::getStatus, 1)
                        .orderByAsc(OwnerObject::getId));
        if (publics.isEmpty()) {
            throw new BizException(ResultCode.UNSUPPORTED,
                    "系统未配置公共归属对象（owner_object，owner_type=1），无法登记公共库存");
        }
        return publics.get(0);
    }

    /**
     * 入库配额校验（M4-3-4 同口径，M5-1-1 落地）：
     * 累计已生成（草稿+已确认，不含作废）+ 本次 ≤ 订单量。
     * 编辑中的单先把自己旧行的计量减掉（旧行即将被逻辑删除）。
     */
    private void checkInboundQuota(Map<Long, PurchaseOrderItem> orderItems,
                                   List<InboundSaveRequest.Item> requests, Long editingId) {
        List<Long> orderItemIds = new ArrayList<>(orderItems.keySet());
        Map<Long, BigDecimal> generated = inboundItemMapper.sumGeneratedQtyByOrderItems(orderItemIds).stream()
                .collect(Collectors.toMap(InboundItemMapper.GeneratedQtyRow::getOrderItemId,
                        InboundItemMapper.GeneratedQtyRow::getGeneratedQty, (a, b) -> a));
        Map<Long, BigDecimal> mine = new HashMap<>();
        if (editingId != null) {
            List<InboundItem> old = inboundItemMapper.selectByInboundId(editingId);
            for (InboundItem item : old) {
                if (item.getOrderItemId() != null && item.getQuantity() != null) {
                    mine.merge(item.getOrderItemId(), item.getQuantity(), BigDecimal::add);
                }
            }
        }
        for (InboundSaveRequest.Item row : requests) {
            PurchaseOrderItem orderItem = orderItems.get(row.getOrderItemId());
            BigDecimal used = generated.getOrDefault(row.getOrderItemId(), BigDecimal.ZERO)
                    .subtract(mine.getOrDefault(row.getOrderItemId(), BigDecimal.ZERO));
            BigDecimal cap = orderItem.getQuantity().subtract(used);
            if (row.getQuantity().compareTo(cap) > 0) {
                throw new BizException(ResultCode.UNSUPPORTED,
                        "第 " + orderItem.getItemNo() + " 行入库量 " + row.getQuantity().toPlainString()
                                + " 超过剩余可入量 " + cap.toPlainString()
                                + "（订单量 " + orderItem.getQuantity().toPlainString() + "）");
            }
        }
    }

    // ================= 私有：库存写入（唯一写入点） =================

    /**
     * 库存增减 + 流水写入（R2 / M5-4-1）。乐观锁重试与并发首插兜底
     * 见类注释；before/after 以读到的那一版余额为准，保证流水与
     * 余额在最终一致视图里对得上（对账 M5-4-4 的依据）。
     * 归属键在写入前归一化：公共库存恒定取公共归属对象——
     * 验收来源草稿的归属是从订单行复刻来的（M3 建单对公共库存存 null），
     * 不归一化会让同一批公共库存按 owner_id null/1 裂成两个余额键。
     */
    private void applyStockDelta(InboundItem item, Long warehouseId, BigDecimal delta,
                                 InboundOrder bill, int changeType) {
        String batchNo = item.getBatchNo() == null ? "" : item.getBatchNo();
        int ownerType = item.getOwnerType() == null ? 1 : item.getOwnerType();
        Long ownerId = item.getOwnerId();
        String ownerNo = item.getOwnerNo();
        if (ownerType == 1) {
            OwnerObject publicOwner = publicOwner();
            ownerId = publicOwner.getId();
            ownerNo = publicOwner.getOwnerNo();
        }
        for (int attempt = 1; ; attempt++) {
            StockBalance balance = stockBalanceMapper.selectByUk(item.getMaterialId(), warehouseId,
                    batchNo, ownerType, ownerId);
            if (balance == null) {
                if (delta.signum() < 0) {
                    throw new BizException(ResultCode.UNSUPPORTED,
                            "库存记录不存在，无法冲减（物料 " + item.getMaterialId() + "）");
                }
                try {
                    stockBalanceMapper.insertInitial(item.getMaterialId(), warehouseId, batchNo,
                            ownerType, ownerId, ownerNo, delta);
                } catch (DuplicateKeyException e) {
                    if (attempt >= MAX_RETRY) {
                        throw new BizException(ResultCode.UNSUPPORTED, "并发入库冲突，请重试");
                    }
                    continue;
                }
                writeFlow(item, bill, changeType, BigDecimal.ZERO, delta, ownerType, ownerId, ownerNo);
                return;
            }
            int rows = stockBalanceMapper.changeQty(balance.getId(), delta, balance.getVersion());
            if (rows == 0) {
                if (attempt >= MAX_RETRY) {
                    throw new BizException(ResultCode.UNSUPPORTED, "并发更新冲突，请重试");
                }
                continue;
            }
            BigDecimal before = balance.getQuantity() == null ? BigDecimal.ZERO : balance.getQuantity();
            BigDecimal after = before.add(delta);
            if (after.signum() < 0) {
                throw new BizException(ResultCode.UNSUPPORTED,
                        "冲减后库存不能为负：该批次库存已被后续业务占用（当前 " + before.toPlainString() + "）");
            }
            writeFlow(item, bill, changeType, before, after, ownerType, ownerId, ownerNo);
            return;
        }
    }

    private void writeFlow(InboundItem item, InboundOrder bill, int changeType,
                           BigDecimal before, BigDecimal after,
                           int ownerType, Long ownerId, String ownerNo) {
        StockFlow flow = new StockFlow();
        flow.setMaterialId(item.getMaterialId());
        flow.setWarehouseId(bill.getWarehouseId());
        flow.setBatchNo(item.getBatchNo() == null ? "" : item.getBatchNo());
        flow.setOwnerType(ownerType);
        flow.setOwnerId(ownerId);
        flow.setOwnerNo(ownerNo);
        flow.setChangeType(changeType);
        flow.setChangeQty(changeType == 1 ? item.getQuantity() : item.getQuantity().negate());
        flow.setBeforeQty(before);
        flow.setAfterQty(after);
        flow.setSourceType(1);
        flow.setSourceId(bill.getId());
        flow.setSourceNo(bill.getInboundNo());
        flow.setOperatorId(currentUserId());
        flow.setFlowTime(LocalDateTime.now());
        stockFlowMapper.insert(flow);
    }

    // ================= 私有：装配 =================

    private List<InboundVO> assembleList(List<InboundOrder> bills) {
        List<Long> ids = bills.stream().map(InboundOrder::getId).toList();
        Map<Long, BigDecimal> totals = inboundItemMapper.selectByInboundIds(ids).stream()
                .collect(Collectors.groupingBy(InboundItem::getInboundId,
                        Collectors.reducing(BigDecimal.ZERO,
                                i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity(),
                                BigDecimal::add)));
        Map<Long, String> warehouseNames = lookupNames(bills.stream()
                        .map(InboundOrder::getWarehouseId).filter(Objects::nonNull).distinct().toList(),
                warehouseMapper::selectBatchIds, Warehouse::getId, Warehouse::getWarehouseName);
        Map<Long, String> supplierNames = lookupNames(bills.stream()
                        .map(InboundOrder::getSupplierId).filter(Objects::nonNull).distinct().toList(),
                supplierMapper::selectBatchIds, Supplier::getId, Supplier::getSupplierName);
        Map<Long, String> creatorNames = lookupNames(bills.stream()
                        .map(InboundOrder::getCreatedBy).filter(Objects::nonNull).distinct().toList(),
                userMapper::selectBatchIds, SysUser::getId, SysUser::getRealName);
        List<InboundVO> result = new ArrayList<>();
        for (InboundOrder bill : bills) {
            InboundVO vo = new InboundVO();
            vo.setId(bill.getId());
            vo.setInboundNo(bill.getInboundNo());
            vo.setInspectionId(bill.getInspectionId());
            vo.setInspectionNo(bill.getInspectionNo());
            vo.setOrderId(bill.getOrderId());
            vo.setOrderNo(bill.getOrderNo());
            vo.setSupplierId(bill.getSupplierId());
            vo.setSupplierName(supplierNames.get(bill.getSupplierId()));
            vo.setWarehouseId(bill.getWarehouseId());
            vo.setWarehouseName(warehouseNames.get(bill.getWarehouseId()));
            vo.setInboundDate(bill.getInboundDate());
            vo.setStatus(bill.getStatus());
            vo.setStatusName(statusName(bill.getStatus()));
            vo.setConfirmTime(bill.getConfirmTime());
            vo.setConfirmByName(lookupName(bill.getConfirmBy()));
            vo.setVoidTime(bill.getVoidTime());
            vo.setVoidReason(bill.getVoidReason());
            vo.setRemark(bill.getRemark());
            vo.setTotalQty(totals.getOrDefault(bill.getId(), BigDecimal.ZERO)
                    .setScale(4, java.math.RoundingMode.HALF_UP));
            vo.setCreatedAt(bill.getCreatedAt());
            vo.setCreatedByName(creatorNames.get(bill.getCreatedBy()));
            result.add(vo);
        }
        return result;
    }

    /** 详情装配：主表 + 明细 + 溯源链 + 流水摘要。 */
    private InboundVO assembleBill(InboundOrder bill, boolean withItems, boolean withFlows) {
        InboundVO vo = assembleList(List.of(bill)).get(0);
        vo.setConfirmByName(lookupName(bill.getConfirmBy()));
        vo.setCreatedByName(lookupName(bill.getCreatedBy()));
        if (withItems) {
            List<InboundItem> items = inboundItemMapper.selectByInboundId(bill.getId());
            vo.setItems(assembleItems(items));
        }
        if (withFlows) {
            List<StockFlow> flows = stockFlowMapper.selectBySourceId(bill.getId());
            List<InboundVO.FlowBrief> briefs = new ArrayList<>();
            Map<Long, String> operatorNames = lookupNames(flows.stream()
                            .map(StockFlow::getOperatorId).filter(Objects::nonNull).distinct().toList(),
                    userMapper::selectBatchIds, SysUser::getId, SysUser::getRealName);
            for (StockFlow flow : flows) {
                InboundVO.FlowBrief brief = new InboundVO.FlowBrief();
                brief.setId(flow.getId());
                brief.setBatchNo(flow.getBatchNo());
                brief.setChangeType(flow.getChangeType());
                brief.setChangeTypeName(flow.getChangeType() != null && flow.getChangeType() == 2
                        ? "作废冲减" : "入库增加");
                brief.setChangeQty(flow.getChangeQty());
                brief.setBeforeQty(flow.getBeforeQty());
                brief.setAfterQty(flow.getAfterQty());
                brief.setFlowTime(flow.getFlowTime());
                brief.setOperatorName(operatorNames.get(flow.getOperatorId()));
                briefs.add(brief);
            }
            vo.setFlows(briefs);
        }
        vo.setTrace(trace(bill.getId()));
        return vo;
    }

    private List<InboundVO.Item> assembleItems(List<InboundItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, String> materialNames = lookupNames(items.stream()
                        .map(InboundItem::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialName);
        Map<Long, String> materialNos = lookupNames(items.stream()
                        .map(InboundItem::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getMaterialNo);
        Map<Long, String> specs = lookupNames(items.stream()
                        .map(InboundItem::getMaterialId).filter(Objects::nonNull).distinct().toList(),
                materialMapper::selectBatchIds, Material::getId, Material::getSpec);
        Map<Long, String> locationNames = lookupNames(items.stream()
                        .map(InboundItem::getLocationId).filter(Objects::nonNull).distinct().toList(),
                locationMapper::selectBatchIds, WarehouseLocation::getId, WarehouseLocation::getLocationName);
        Map<Long, String> locationNos = lookupNames(items.stream()
                        .map(InboundItem::getLocationId).filter(Objects::nonNull).distinct().toList(),
                locationMapper::selectBatchIds, WarehouseLocation::getId, WarehouseLocation::getLocationNo);
        List<InboundVO.Item> result = new ArrayList<>();
        for (InboundItem item : items) {
            InboundVO.Item vo = new InboundVO.Item();
            vo.setId(item.getId());
            vo.setOrderItemId(item.getOrderItemId());
            vo.setMaterialId(item.getMaterialId());
            vo.setMaterialNo(materialNos.get(item.getMaterialId()));
            vo.setMaterialName(materialNames.get(item.getMaterialId()));
            vo.setSpec(specs.get(item.getMaterialId()));
            vo.setQuantity(item.getQuantity());
            vo.setUnitId(item.getUnitId());
            vo.setUnitName(unitName(item.getUnitId()));
            vo.setBatchNo(item.getBatchNo());
            vo.setLocationId(item.getLocationId());
            vo.setLocationNo(locationNos.get(item.getLocationId()));
            vo.setLocationName(locationNames.get(item.getLocationId()));
            vo.setOwnerType(item.getOwnerType());
            vo.setOwnerId(item.getOwnerId());
            vo.setOwnerNo(item.getOwnerNo());
            vo.setRemark(item.getRemark());
            result.add(vo);
        }
        return result;
    }

    // ================= 私有：工具 =================

    private InboundOrder requireBill(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "缺少入库单id");
        }
        InboundOrder bill = inboundOrderMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购入库单不存在");
        }
        return bill;
    }

    private InboundOrder requireEditableBill(Long id) {
        InboundOrder bill = requireBill(id);
        if (!Objects.equals(bill.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, "只有草稿状态的入库单可以编辑");
        }
        return bill;
    }

    /** 列表的仓库范围注入：只对拥有仓库范围的用户生效（仓管员口径，见类注释）。 */
    private void applyWarehouseScope(LambdaQueryWrapper<InboundOrder> wrapper) {
        ScopeCondition scope = currentScope();
        if (!scope.isAll() && scope.hasWarehouse()) {
            wrapper.in(InboundOrder::getWarehouseId, scope.getWarehouseIds());
        }
    }

    private ScopeCondition currentScope() {
        CurrentUser user = AuthContext.get();
        return user == null ? new ScopeCondition() : user.getScope();
    }

    private Long currentUserId() {
        return AuthContext.getOrThrow().getUserId();
    }

    private String nextInboundNo() {
        return billNoGenerator.next("bill_no_rule_inbound", inboundOrderMapper::maxSeqByPrefix);
    }

    private String statusName(Integer status) {
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

    /** 单位名称走计量单位字典（dict_unit 的项名），与订单/验收单口径一致。 */
    private String unitName(Long unitId) {
        if (unitId == null) {
            return null;
        }
        DictItem item = dictItemMapper.selectById(unitId);
        return item == null ? null : item.getItemName();
    }

    private String lookupName(Long id) {
        if (id == null) {
            return null;
        }
        SysUser user = userMapper.selectById(id);
        return user == null ? null : user.getRealName();
    }

    private <E> Map<Long, String> lookupNames(List<Long> ids, Function<Collection<Long>, List<E>> loader,
                                              Function<E, Long> idGetter, Function<E, String> nameGetter) {
        // 空结果用 HashMap：Map.of().get(null) 会 NPE（JDK ImmutableCollections 语义），
        // 而 supplierId 等字段可空，get(null) 必须安全返回 null。
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return loader.apply(ids).stream()
                .collect(Collectors.toMap(idGetter, nameGetter, (a, b) -> a));
    }
}
