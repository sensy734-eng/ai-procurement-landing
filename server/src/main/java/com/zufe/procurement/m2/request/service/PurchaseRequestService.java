package com.zufe.procurement.m2.request.service;

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
import com.zufe.procurement.m2.request.dto.CloseRequest;
import com.zufe.procurement.m2.request.dto.RejectRequest;
import com.zufe.procurement.m2.request.dto.RequestItemSaveRequest;
import com.zufe.procurement.m2.request.dto.RequestQuery;
import com.zufe.procurement.m2.request.dto.RequestSaveRequest;
import com.zufe.procurement.m2.request.dto.ReturnItemsRequest;
import com.zufe.procurement.m2.request.entity.PurchaseRequest;
import com.zufe.procurement.m2.request.entity.PurchaseRequestItem;
import com.zufe.procurement.m2.request.mapper.PurchaseRequestItemMapper;
import com.zufe.procurement.m2.request.mapper.PurchaseRequestMapper;
import com.zufe.procurement.m2.request.vo.ApprovalHistoryVO;
import com.zufe.procurement.m2.request.vo.OwnerOptionVO;
import com.zufe.procurement.m2.request.vo.RequestItemVO;
import com.zufe.procurement.m2.request.vo.RequestVO;
import com.zufe.procurement.m2.request.vo.TodoVO;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购申请服务，覆盖功能点 M2-1-1 至 M2-4-4。
 * 业务口径集中在本层：三状态分三个独立字段互不推导；需求日期显式优先、
 * 缺省取明细行最早需求日期；预估金额与合计由服务层计算；停用物料与停用
 * 供应商不可选；供货范围按物料或分类校验；按行退回拆分为通过部分与退回部分；
 * 已转数量只由转订单动作回写（M2-4-3，见 RequestTransferService）。
 */
@Service
public class PurchaseRequestService {

    /** 单据生命周期状态 */
    private static final int LIFECYCLE_DRAFT = 1;
    private static final int LIFECYCLE_SUBMITTED = 2;
    private static final int LIFECYCLE_APPROVED = 3;
    private static final int LIFECYCLE_CLOSED = 4;

    /** 审批结果状态 */
    private static final int AUDIT_PENDING = 1;
    private static final int AUDIT_PASSED = 2;
    private static final int AUDIT_REJECTED = 3;

    /** 明细行状态 */
    private static final int ITEM_PENDING = 1;
    private static final int ITEM_PARTIAL = 2;
    private static final int ITEM_TRANSFERRED = 3;
    private static final int ITEM_RETURNED = 4;
    private static final int ITEM_CLOSED = 5;

    /** 归属类型 */
    private static final int OWNER_PUBLIC = 1;

    private static final int BILL_TYPE_REQUEST = ApprovalEngineService.BILL_TYPE_REQUEST;
    private static final int NO_ATTEMPTS = 3;

    private static final String PERM_ITEM_SUGGEST = "m2:request_item:suggest";
    private static final String PERM_ITEM_OWNER = "m2:request_item:owner";

    private final PurchaseRequestMapper requestMapper;
    private final PurchaseRequestItemMapper itemMapper;
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

    public PurchaseRequestService(PurchaseRequestMapper requestMapper,
                                  PurchaseRequestItemMapper itemMapper,
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
                                  SysOperationLogMapper operationLogMapper) {
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
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
    }

    // ================= M2-1-1 列表与检索 =================

    /**
     * 分页列表。数据范围按当前用户的部门维度注入（M11-3-3），
     * 范围为空时失败关闭返回空列表，不在内存中筛选。
     */
    public PageResult<RequestVO> page(RequestQuery query) {
        Page<PurchaseRequest> page = new Page<>(Math.max(query.getPageNo(), 1),
                Math.min(Math.max(query.getPageSize(), 1), 100));
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<PurchaseRequest>()
                .like(StringUtils.hasText(query.getRequestNo()), PurchaseRequest::getRequestNo,
                        query.getRequestNo() == null ? null : query.getRequestNo().trim())
                .eq(query.getLifecycleStatus() != null, PurchaseRequest::getLifecycleStatus,
                        query.getLifecycleStatus())
                .eq(query.getAuditStatus() != null, PurchaseRequest::getAuditStatus, query.getAuditStatus())
                .eq(query.getDeptId() != null, PurchaseRequest::getDeptId, query.getDeptId())
                .eq(query.getApplicantId() != null, PurchaseRequest::getApplicantId, query.getApplicantId())
                .ge(query.getStartDate() != null, PurchaseRequest::getRequestDate, query.getStartDate())
                .le(query.getEndDate() != null, PurchaseRequest::getRequestDate, query.getEndDate())
                .orderByDesc(PurchaseRequest::getRequestDate)
                .orderByDesc(PurchaseRequest::getId);
        dataScopeService.applyDept(wrapper, PurchaseRequest::getDeptId, currentScope());
        Page<PurchaseRequest> result = requestMapper.selectPage(page, wrapper);
        List<PurchaseRequest> bills = result.getRecords();
        if (bills.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        return PageResult.of(assembleList(bills), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** M2-1-1 详情，含明细行与审批历史（M2-3-6）。 */
    public RequestVO detail(Long id) {
        PurchaseRequest bill = requestMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购申请单不存在");
        }
        requireBillInScope(bill);
        List<PurchaseRequestItem> items = itemMapper.selectByRequestId(id);
        RequestVO vo = toHeaderVO(bill);
        vo.setItems(enrichItems(items));
        vo.setApprovalHistory(approvalEngine.history(BILL_TYPE_REQUEST, id));
        fillAggregates(vo, items);
        return vo;
    }

    // ================= M2-1-2 新建 =================

    @Transactional
    public Long create(RequestSaveRequest request) {
        // 部门缺省取当前用户部门：数据范围按部门过滤，建单人只能落在自己
        // 可见的部门内（轮次 4 前端未传 deptId，曾报「请选择申请部门」）
        Long deptId = request.getDeptId() != null ? request.getDeptId() : currentDeptId();
        validateDept(deptId);
        Long id = null;
        String requestNo = null;
        for (int attempt = 1; attempt <= NO_ATTEMPTS; attempt++) {
            PurchaseRequest bill = new PurchaseRequest();
            requestNo = billNoGenerator.next("bill_no_rule_request", requestMapper::maxSeqByPrefix);
            bill.setRequestNo(requestNo);
            bill.setRequestDate(request.getRequestDate() != null ? request.getRequestDate() : LocalDate.now());
            bill.setDeptId(deptId);
            bill.setApplicantId(currentUserId());
            bill.setLifecycleStatus(LIFECYCLE_DRAFT);
            bill.setAuditStatus(AUDIT_PENDING);
            bill.setTotalAmount(BigDecimal.ZERO);
            bill.setRemark(request.getRemark());
            try {
                requestMapper.insert(bill);
                id = bill.getId();
                break;
            } catch (DuplicateKeyException ex) {
                if (attempt == NO_ATTEMPTS) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "单号生成冲突，请重试");
                }
            }
        }
        operationLogService.record("M2", "create", "purchase_request", id, requestNo,
                "新建采购申请单，部门 " + request.getDeptId());
        return id;
    }

    // ================= M2-1-3 编辑 =================

    /**
     * 编辑主表。仅草稿 / 已驳回可编辑——两者都表现为 lifecycle_status=1，
     * 由 audit_status=3 区分"已驳回"，不另设状态值（R1）。
     */
    @Transactional
    public void update(Long id, RequestSaveRequest request) {
        PurchaseRequest bill = loadEditableBill(id);
        Long deptId = request.getDeptId() != null ? request.getDeptId() : currentDeptId();
        validateDept(deptId);
        bill.setDeptId(deptId);
        if (request.getRequestDate() != null) {
            bill.setRequestDate(request.getRequestDate());
        }
        bill.setRemark(request.getRemark());
        if (request.getRequireDate() != null) {
            bill.setRequireDate(request.getRequireDate());
        } else {
            bill.setRequireDate(earliestRequireDate(id));
        }
        if (requestMapper.updateById(bill) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        operationLogService.record("M2", "update", "purchase_request", id, bill.getRequestNo(),
                "编辑采购申请单主表信息");
    }

    // ================= M2-1-4 删除 =================

    /** 逻辑删除，仅草稿状态；明细行一并逻辑删除，保持表内数据一致。 */
    @Transactional
    public void delete(Long id) {
        PurchaseRequest bill = loadEditableBill(id);
        // @TableLogic 字段经 updateById(实体) 会被 MP 忽略，逻辑删除必须走 delete(wrapper)
        if (requestMapper.delete(new LambdaQueryWrapper<PurchaseRequest>()
                .eq(PurchaseRequest::getId, id)) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        itemMapper.update(null, new LambdaUpdateWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getRequestId, id)
                .set(PurchaseRequestItem::getIsDeleted, 1));
        operationLogService.record("M2", "delete", "purchase_request", id, bill.getRequestNo(),
                "删除采购申请单（草稿）");
    }

    // ================= M2-1-5 复制申请单 =================

    /**
     * 复制主表与活跃明细行为新草稿。已转完、已退回、已关闭的行不复制
     * （复制进来也没有业务意义），已转数量清零、行状态重置为待转。
     */
    @Transactional
    public Long copy(Long id) {
        PurchaseRequest source = requestMapper.selectById(id);
        if (source == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购申请单不存在");
        }
        requireBillInScope(source);
        List<PurchaseRequestItem> items = itemMapper.selectByRequestId(id).stream()
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL)
                .collect(Collectors.toList());

        Long newId = null;
        String newNo = null;
        for (int attempt = 1; attempt <= NO_ATTEMPTS; attempt++) {
            PurchaseRequest bill = new PurchaseRequest();
            newNo = billNoGenerator.next("bill_no_rule_request", requestMapper::maxSeqByPrefix);
            bill.setRequestNo(newNo);
            bill.setRequestDate(LocalDate.now());
            bill.setDeptId(source.getDeptId());
            bill.setApplicantId(currentUserId());
            bill.setLifecycleStatus(LIFECYCLE_DRAFT);
            bill.setAuditStatus(AUDIT_PENDING);
            bill.setTotalAmount(BigDecimal.ZERO);
            bill.setRequireDate(items.stream().map(PurchaseRequestItem::getRequireDate)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null));
            bill.setRemark("复制自申请单 " + source.getRequestNo());
            try {
                requestMapper.insert(bill);
                newId = bill.getId();
                break;
            } catch (DuplicateKeyException ex) {
                if (attempt == NO_ATTEMPTS) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "单号生成冲突，请重试");
                }
            }
        }
        int itemNo = 0;
        for (PurchaseRequestItem item : items) {
            PurchaseRequestItem copy = new PurchaseRequestItem();
            copy.setRequestId(newId);
            copy.setItemNo(++itemNo);
            copy.setMaterialId(item.getMaterialId());
            copy.setQuantity(item.getQuantity());
            copy.setUnitId(item.getUnitId());
            copy.setEstPrice(item.getEstPrice());
            copy.setEstAmount(item.getEstAmount());
            copy.setSuggestSupplierId(item.getSuggestSupplierId());
            copy.setRequireDate(item.getRequireDate());
            copy.setOwnerType(item.getOwnerType());
            copy.setOwnerId(item.getOwnerId());
            copy.setOwnerNo(item.getOwnerNo());
            copy.setTransferredQty(BigDecimal.ZERO);
            copy.setItemStatus(ITEM_PENDING);
            copy.setRemark(item.getRemark());
            itemMapper.insert(copy);
        }
        recomputeTotal(newId);
        operationLogService.record("M2", "copy", "purchase_request", newId, newNo,
                "复制申请单 " + source.getRequestNo() + " 的 " + items.size() + " 行明细");
        return newId;
    }

    // ================= M2-1-6 打印 / 导出 =================

    /** 导出为 CSV（UTF-8 BOM，Excel 可直接打开），一行主表加多行明细的平表视图。 */
    public byte[] exportCsv(Long id) {
        RequestVO vo = detail(id);
        String[] headers = {"申请单号", "申请日期", "申请部门", "申请人", "需求日期", "预估金额合计",
                "单据状态", "审批结果", "行号", "物料编码", "物料名称", "规格", "申请数量", "单位",
                "预估单价", "行预估金额", "建议供应商", "行需求日期", "归属类型", "归属对象",
                "已转数量", "剩余数量", "行状态", "行备注"};
        List<String[]> rows = new ArrayList<>();
        for (RequestItemVO item : vo.getItems()) {
            rows.add(new String[]{
                    vo.getRequestNo(), String.valueOf(vo.getRequestDate()), vo.getDeptName(),
                    vo.getApplicantName(), String.valueOf(vo.getRequireDate()),
                    vo.getTotalAmount() == null ? "" : vo.getTotalAmount().toPlainString(),
                    vo.getLifecycleStatusName(), vo.getAuditStatusName(),
                    String.valueOf(item.getItemNo()), item.getMaterialNo(), item.getMaterialName(),
                    item.getSpec(), item.getQuantity() == null ? "" : item.getQuantity().toPlainString(),
                    item.getUnitName(), item.getEstPrice() == null ? "" : item.getEstPrice().toPlainString(),
                    item.getEstAmount() == null ? "" : item.getEstAmount().toPlainString(),
                    item.getSuggestSupplierName() == null ? "" : item.getSuggestSupplierName(),
                    String.valueOf(item.getRequireDate()), ownerTypeName(item.getOwnerType()),
                    item.getOwnerName() == null ? "" : item.getOwnerName(),
                    item.getTransferredQty() == null ? "" : item.getTransferredQty().toPlainString(),
                    item.getRemainingQty() == null ? "" : item.getRemainingQty().toPlainString(),
                    item.getItemStatusName(), item.getRemark() == null ? "" : item.getRemark()});
        }
        return CsvWriter.toCsv(headers, rows);
    }

    // ================= M2-2-1 新增明细行 =================

    @Transactional
    public Long addItem(Long requestId, RequestItemSaveRequest request) {
        PurchaseRequest bill = loadEditableBill(requestId);
        PurchaseRequestItem item = buildItem(bill, request);
        item.setItemNo(itemMapper.maxItemNo(requestId) + 1);
        itemMapper.insert(item);
        refreshRequireDate(bill);
        recomputeTotal(requestId);
        operationLogService.record("M2", "create", "purchase_request_item", item.getId(),
                bill.getRequestNo() + "-" + item.getItemNo(), "新增申请明细行，物料 " + request.getMaterialId());
        return item.getId();
    }

    // ================= M2-2-2 编辑 / 删除明细行 =================

    @Transactional
    public void updateItem(Long requestId, Long itemId, RequestItemSaveRequest request) {
        PurchaseRequest bill = loadEditableBill(requestId);
        PurchaseRequestItem item = itemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getRequestId(), requestId)) {
            throw new BizException(ResultCode.NOT_FOUND, "明细行不存在");
        }
        if (item.getTransferredQty() != null && item.getTransferredQty().signum() > 0) {
            throw new BizException(ResultCode.UNSUPPORTED, "明细行已产生转单记录，不能编辑");
        }
        PurchaseRequestItem updated = buildItem(bill, request);
        updated.setId(item.getId());
        updated.setItemNo(item.getItemNo());
        updated.setVersion(item.getVersion());
        updated.setTransferredQty(BigDecimal.ZERO);
        updated.setItemStatus(ITEM_PENDING);
        if (itemMapper.updateById(updated) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "明细行已被变更，请刷新后重试");
        }
        refreshRequireDate(bill);
        recomputeTotal(requestId);
        operationLogService.record("M2", "update", "purchase_request_item", itemId,
                bill.getRequestNo() + "-" + item.getItemNo(), "编辑申请明细行");
    }

    @Transactional
    public void deleteItem(Long requestId, Long itemId) {
        PurchaseRequest bill = loadEditableBill(requestId);
        PurchaseRequestItem item = itemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getRequestId(), requestId)) {
            throw new BizException(ResultCode.NOT_FOUND, "明细行不存在");
        }
        if (item.getTransferredQty() != null && item.getTransferredQty().signum() > 0) {
            throw new BizException(ResultCode.UNSUPPORTED, "明细行已产生转单记录，不能删除");
        }
        // 同 delete()：逻辑删除走 wrapper，实体 updateById 会被 MP 忽略该字段
        if (itemMapper.delete(new LambdaQueryWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getId, itemId)) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "明细行状态已变更，请刷新后重试");
        }
        refreshRequireDate(bill);
        recomputeTotal(requestId);
        operationLogService.record("M2", "delete", "purchase_request_item", itemId,
                bill.getRequestNo() + "-" + item.getItemNo(), "删除申请明细行");
    }

    // ================= M2-3-1 提交审批 =================

    /**
     * 提交审批：草稿 → 已提交，并按 M11-5-4 生成首节点审批任务。
     * 状态更新带乐观锁，并发提交只有一次成功（其余报状态已变更）。
     */
    @Transactional
    public void submit(Long id) {
        PurchaseRequest bill = loadEditableBill(id);
        long activeItems = itemMapper.selectByRequestId(id).stream()
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL)
                .count();
        if (activeItems == 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "至少需要一行未转完的明细行才能提交审批");
        }
        PurchaseRequest update = new PurchaseRequest();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_SUBMITTED);
        if (requestMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        approvalEngine.generateFirstNodeTasks(BILL_TYPE_REQUEST, bill.getId(), bill.getRequestNo());
        operationLogService.record("M2", "submit", "purchase_request", id, bill.getRequestNo(),
                "提交审批，活跃明细行 " + activeItems + " 行");
    }

    // ================= M2-3-2 审批通过 =================

    @Transactional
    public void approve(Long id) {
        PurchaseRequest bill = loadSubmittedBill(id);
        ApprovalTask task = currentPendingTask(bill);
        approvalEngine.process(task.getId(), currentUserId(), true, null,
                BILL_TYPE_REQUEST, bill.getId(), bill.getRequestNo(), approvalHandler);
        operationLogService.record("M2", "approve", "purchase_request", id, bill.getRequestNo(),
                "审批通过（节点 " + task.getNodeSeq() + " " + task.getNodeName() + "）");
    }

    // ================= M2-3-3 审批驳回 =================

    @Transactional
    public void reject(Long id, RejectRequest request) {
        PurchaseRequest bill = loadSubmittedBill(id);
        ApprovalTask task = currentPendingTask(bill);
        approvalEngine.process(task.getId(), currentUserId(), false, request.getOpinion(),
                BILL_TYPE_REQUEST, bill.getId(), bill.getRequestNo(), approvalHandler);
        operationLogService.record("M2", "reject", "purchase_request", id, bill.getRequestNo(),
                "审批驳回（节点 " + task.getNodeSeq() + " " + task.getNodeName() + "）：" + request.getOpinion());
    }

    // ================= M2-3-4 撤回申请 =================

    /** 撤回：已提交且无任何审批处理记录时回到草稿，待处理任务全部失效。 */
    @Transactional
    public void withdraw(Long id) {
        PurchaseRequest bill = loadSubmittedBill(id);
        if (!Objects.equals(bill.getApplicantId(), currentUserId())) {
            throw new BizException(ResultCode.NO_PERMISSION, "只有申请人可以撤回申请");
        }
        // 撤回基准：本次提交生成的任务区间。submit() 内任务先插入、提交日志后写入，
        // 故本批任务 id 落在（上一条 submit 日志 id, 最近一条 submit 日志 id] 内；
        // 驳回后重新提交会写新的提交记录，历史批次的处理记录不会误拦。
        List<SysOperationLog> submits = operationLogMapper.selectList(new LambdaQueryWrapper<SysOperationLog>()
                .eq(SysOperationLog::getModuleCode, "M2")
                .eq(SysOperationLog::getAction, "submit")
                .eq(SysOperationLog::getTargetId, id)
                .orderByDesc(SysOperationLog::getId)
                .last("LIMIT 2"));
        if (submits.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        long upper = submits.get(0).getId();
        long lower = submits.size() > 1 ? submits.get(1).getId() : 0L;
        if (approvalEngine.hasHandledInIdRange(BILL_TYPE_REQUEST, id, lower, upper)) {
            throw new BizException(ResultCode.UNSUPPORTED, "已有审批处理记录，不能撤回");
        }
        PurchaseRequest update = new PurchaseRequest();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_DRAFT);
        if (requestMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        approvalEngine.invalidateTasks(BILL_TYPE_REQUEST, id);
        operationLogService.record("M2", "withdraw", "purchase_request", id, bill.getRequestNo(),
                "撤回审批，回到草稿");
    }

    // ================= M2-3-5 整单关闭 =================

    /**
     * 整单关闭：已审核 → 已关闭，剩余未转订单的活跃行置已关闭。
     * 存在未转数量时前端按 M2-4-4 给出确认提示，服务端记录剩余量备查。
     */
    @Transactional
    public void close(Long id, CloseRequest request) {
        PurchaseRequest bill = requestMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购申请单不存在");
        }
        requireBillInScope(bill);
        if (!Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_APPROVED)) {
            throw new BizException(ResultCode.UNSUPPORTED, "只有已审核的申请单可以关闭");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "关闭原因必填");
        }
        List<PurchaseRequestItem> items = itemMapper.selectByRequestId(id);
        BigDecimal remaining = items.stream()
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL)
                .map(this::remainingQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (PurchaseRequestItem item : items) {
            if (item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL) {
                PurchaseRequestItem update = new PurchaseRequestItem();
                update.setId(item.getId());
                update.setVersion(item.getVersion());
                update.setItemStatus(ITEM_CLOSED);
                itemMapper.updateById(update);
            }
        }
        PurchaseRequest update = new PurchaseRequest();
        update.setId(bill.getId());
        update.setVersion(bill.getVersion());
        update.setLifecycleStatus(LIFECYCLE_CLOSED);
        update.setCloseReason(request.getReason().trim());
        if (requestMapper.updateById(update) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
        }
        operationLogService.record("M2", "close", "purchase_request", id, bill.getRequestNo(),
                "整单关闭，剩余未转数量 " + remaining.toPlainString() + "，原因：" + request.getReason());
    }

    // ================= M2-2-4 按行退回 =================

    /**
     * 按行退回：审批中的单据拆分为「通过部分」与「退回部分」。
     * 被退回行剥离原单生成新的驳回态草稿单（采购员可编辑后重新提交），
     * 原单剩余行继续审批；剩余行为零时原单整体视为驳回，不生成新单。
     * 当前任务标记已驳回留痕；若节点已无待处理任务，按原配置重启节点，
     * 保证剩余行仍有审批人可处理。
     *
     * @return 新生成的退回部分草稿单id；整单全部退回时返回 null
     */
    @Transactional
    public Long returnItems(Long id, ReturnItemsRequest request) {
        PurchaseRequest bill = loadSubmittedBill(id);
        ApprovalTask task = currentPendingTask(bill);
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请至少选择一行退回");
        }
        if (request.getOpinion() == null || request.getOpinion().isBlank()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "按行退回必须填写退回意见");
        }
        List<PurchaseRequestItem> items = itemMapper.selectByRequestId(id);
        Set<Long> returnIds = new HashSet<>(request.getItemIds());
        List<PurchaseRequestItem> returning = items.stream()
                .filter(item -> returnIds.contains(item.getId()))
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL)
                .collect(Collectors.toList());
        if (returning.size() != returnIds.size()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "存在不可退回的明细行（已转完、已退回或已关闭的行不能退回）");
        }
        List<PurchaseRequestItem> remaining = items.stream()
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL)
                .filter(item -> !returnIds.contains(item.getId()))
                .collect(Collectors.toList());

        for (PurchaseRequestItem item : returning) {
            PurchaseRequestItem update = new PurchaseRequestItem();
            update.setId(item.getId());
            update.setVersion(item.getVersion());
            update.setItemStatus(ITEM_RETURNED);
            itemMapper.updateById(update);
        }

        Long newBillId = null;
        if (remaining.isEmpty()) {
            // 全部行被退回：原单整体视为驳回，不再生成拆分单
            PurchaseRequest update = new PurchaseRequest();
            update.setId(bill.getId());
            update.setVersion(bill.getVersion());
            update.setLifecycleStatus(LIFECYCLE_DRAFT);
            update.setAuditStatus(AUDIT_REJECTED);
            if (requestMapper.updateById(update) <= 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
            }
            // 审批人任务记已驳回留痕，其余待处理任务（如会签他人的任务）失效
            approvalEngine.markTaskRejectedForReturn(task.getId(), currentUserId(),
                    request.getOpinion().trim());
            approvalEngine.invalidateTasks(BILL_TYPE_REQUEST, id);
        } else {
            // 生成退回部分草稿单
            LocalDate earliest = returning.stream().map(PurchaseRequestItem::getRequireDate)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
            for (int attempt = 1; attempt <= NO_ATTEMPTS; attempt++) {
                PurchaseRequest split = new PurchaseRequest();
                String newNo = billNoGenerator.next("bill_no_rule_request", requestMapper::maxSeqByPrefix);
                split.setRequestNo(newNo);
                split.setRequestDate(LocalDate.now());
                split.setDeptId(bill.getDeptId());
                split.setApplicantId(bill.getApplicantId());
                split.setRequireDate(earliest);
                split.setLifecycleStatus(LIFECYCLE_DRAFT);
                split.setAuditStatus(AUDIT_REJECTED);
                split.setTotalAmount(BigDecimal.ZERO);
                split.setRemark("由申请单 " + bill.getRequestNo() + " 按行退回拆分生成；退回意见："
                        + (request.getOpinion() == null ? "" : request.getOpinion().trim()));
                try {
                    requestMapper.insert(split);
                    newBillId = split.getId();
                    int itemNo = 0;
                    for (PurchaseRequestItem item : returning) {
                        PurchaseRequestItem copy = new PurchaseRequestItem();
                        copy.setRequestId(split.getId());
                        copy.setItemNo(++itemNo);
                        copy.setMaterialId(item.getMaterialId());
                        copy.setQuantity(item.getQuantity());
                        copy.setUnitId(item.getUnitId());
                        copy.setEstPrice(item.getEstPrice());
                        copy.setEstAmount(item.getEstAmount());
                        copy.setSuggestSupplierId(item.getSuggestSupplierId());
                        copy.setRequireDate(item.getRequireDate());
                        copy.setOwnerType(item.getOwnerType());
                        copy.setOwnerId(item.getOwnerId());
                        copy.setOwnerNo(item.getOwnerNo());
                        copy.setTransferredQty(BigDecimal.ZERO);
                        copy.setItemStatus(ITEM_PENDING);
                        copy.setRemark(item.getRemark());
                        itemMapper.insert(copy);
                    }
                    // 拆分单同样重算合计金额，避免新单头尾不一致
                    recomputeTotal(split.getId());
                    break;
                } catch (DuplicateKeyException ex) {
                    if (attempt == NO_ATTEMPTS) {
                        throw new BizException(ResultCode.INVALID_PARAMETER, "单号生成冲突，请重试");
                    }
                }
            }
            recomputeTotal(id);
            refreshRequireDate(bill);
            approvalEngine.markTaskRejectedForReturn(task.getId(), currentUserId(),
                    request.getOpinion() == null ? "" : request.getOpinion());
            approvalEngine.restartNodeIfIdle(BILL_TYPE_REQUEST, id, bill.getRequestNo(), task.getNodeSeq());
        }
        operationLogService.record("M2", "return", "purchase_request", id, bill.getRequestNo(),
                "按行退回 " + returning.size() + " 行，剩余 " + remaining.size() + " 行继续审批；意见："
                        + request.getOpinion());
        return newBillId;
    }

    // ================= M2-3-8 我的待办审批列表 =================

    /**
     * 我的待办。先取当前用户的待处理任务，再按单据部门数据范围分页查询单据，
     * 与功能点"按数据范围过滤后分页列出待办"一致；范围外单据的待办不出现。
     */
    public PageResult<TodoVO> todoPage(long pageNo, long pageSize) {
        List<ApprovalTask> tasks = approvalEngine.pendingTasksForApprover(currentUserId(), BILL_TYPE_REQUEST);
        if (tasks.isEmpty()) {
            return PageResult.of(List.of(), 0, 1, pageSize);
        }
        Set<Long> billIds = tasks.stream().map(ApprovalTask::getBillId).collect(Collectors.toSet());
        Page<PurchaseRequest> page = new Page<>(Math.max(pageNo, 1),
                Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<PurchaseRequest>()
                .in(PurchaseRequest::getId, billIds)
                .eq(PurchaseRequest::getLifecycleStatus, LIFECYCLE_SUBMITTED)
                .orderByDesc(PurchaseRequest::getRequestDate)
                .orderByDesc(PurchaseRequest::getId);
        dataScopeService.applyDept(wrapper, PurchaseRequest::getDeptId, currentScope());
        Page<PurchaseRequest> result = requestMapper.selectPage(page, wrapper);
        Map<Long, ApprovalTask> taskByBill = tasks.stream()
                .collect(Collectors.toMap(ApprovalTask::getBillId, Function.identity(), (a, b) -> a));
        List<Long> pageBillIds = result.getRecords().stream()
                .map(PurchaseRequest::getId).collect(Collectors.toList());
        Map<Long, Integer> itemCountByBill = pageBillIds.isEmpty() ? Map.of()
                : itemMapper.selectByRequestIds(pageBillIds).stream()
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL)
                .collect(Collectors.groupingBy(PurchaseRequestItem::getRequestId,
                        Collectors.reducing(0, item -> 1, Integer::sum)));
        List<TodoVO> records = new ArrayList<>();
        for (PurchaseRequest bill : result.getRecords()) {
            ApprovalTask task = taskByBill.get(bill.getId());
            if (task == null) {
                continue;
            }
            TodoVO vo = new TodoVO();
            vo.setTaskId(task.getId());
            vo.setTaskNo(task.getTaskNo());
            vo.setBillType(task.getBillType());
            vo.setBillId(bill.getId());
            vo.setBillNo(bill.getRequestNo());
            vo.setNodeSeq(task.getNodeSeq());
            vo.setNodeName(task.getNodeName());
            vo.setSignType(task.getSignType());
            vo.setSignTypeName(Objects.equals(task.getSignType(), 2) ? "会签" : "或签");
            vo.setCreatedTime(task.getCreatedAt());
            vo.setApplicantName(userRealName(bill.getApplicantId()));
            vo.setRequestDate(bill.getRequestDate());
            vo.setTotalAmount(bill.getTotalAmount());
            vo.setItemCount(itemCountByBill.getOrDefault(bill.getId(), 0));
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ================= M2-2-5 归属对象选项 =================

    /** 归属对象下拉数据源。owner_object 无独立维护功能点，此处只读暴露。 */
    public OwnerOptionVO ownerOptions() {
        List<OwnerObject> owners = ownerObjectMapper.selectList(new LambdaQueryWrapper<OwnerObject>()
                .eq(OwnerObject::getStatus, 1)
                .orderByAsc(OwnerObject::getOwnerType)
                .orderByAsc(OwnerObject::getId));
        OwnerOptionVO vo = new OwnerOptionVO();
        for (OwnerObject owner : owners) {
            OwnerOptionVO.Option option = new OwnerOptionVO.Option();
            option.setId(owner.getId());
            option.setOwnerType(owner.getOwnerType());
            option.setOwnerTypeName(ownerTypeName(owner.getOwnerType()));
            option.setOwnerNo(owner.getOwnerNo());
            option.setOwnerName(owner.getOwnerName());
            vo.getOptions().add(option);
        }
        return vo;
    }

    // ================= 审批结果回写（M11-5-5 的单据侧实现） =================

    /**
     * 审批引擎回调：末节点全部通过时置"已审核 + 审批通过"，
     * 单据转为可转订单。与引擎在同一事务内执行。
     */
    private final ApprovalEngineService.BillApprovalHandler approvalHandler =
            new ApprovalEngineService.BillApprovalHandler() {
                @Override
                public void onPassed(Long billId) {
                    PurchaseRequest bill = requestMapper.selectById(billId);
                    if (bill == null) {
                        return;
                    }
                    PurchaseRequest update = new PurchaseRequest();
                    update.setId(billId);
                    update.setVersion(bill.getVersion());
                    update.setLifecycleStatus(LIFECYCLE_APPROVED);
                    update.setAuditStatus(AUDIT_PASSED);
                    if (requestMapper.updateById(update) <= 0) {
                        throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
                    }
                }

                @Override
                public void onRejected(Long billId) {
                    PurchaseRequest bill = requestMapper.selectById(billId);
                    if (bill == null) {
                        return;
                    }
                    // R1：驳回时生命周期回落草稿，由审批结果状态表达"已驳回"
                    PurchaseRequest update = new PurchaseRequest();
                    update.setId(billId);
                    update.setVersion(bill.getVersion());
                    update.setLifecycleStatus(LIFECYCLE_DRAFT);
                    update.setAuditStatus(AUDIT_REJECTED);
                    if (requestMapper.updateById(update) <= 0) {
                        throw new BizException(ResultCode.INVALID_PARAMETER, "单据状态已变更，请刷新后重试");
                    }
                }
            };

    // ================= 私有：单据加载与状态校验 =================

    /** 可编辑单据：lifecycle=1（草稿或已驳回）且在数据范围内。 */
    private PurchaseRequest loadEditableBill(Long id) {
        PurchaseRequest bill = requestMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购申请单不存在");
        }
        requireBillInScope(bill);
        if (!Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_DRAFT)) {
            throw new BizException(ResultCode.UNSUPPORTED, "只有草稿或已驳回的申请单可以编辑");
        }
        return bill;
    }

    /** 审批中单据：lifecycle=2 且在数据范围内。 */
    private PurchaseRequest loadSubmittedBill(Long id) {
        PurchaseRequest bill = requestMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购申请单不存在");
        }
        requireBillInScope(bill);
        if (!Objects.equals(bill.getLifecycleStatus(), LIFECYCLE_SUBMITTED)) {
            throw new BizException(ResultCode.UNSUPPORTED, "申请单不在审批中");
        }
        return bill;
    }

    /** 当前用户在审批中单据上的待处理任务；没有则区分"已处理过"与"非审批人"。 */
    private ApprovalTask currentPendingTask(PurchaseRequest bill) {
        Long userId = currentUserId();
        ApprovalTask pending = approvalEngine.pendingTaskForApprover(BILL_TYPE_REQUEST, bill.getId(), userId);
        if (pending != null) {
            return pending;
        }
        if (approvalEngine.hasHandledByApprover(BILL_TYPE_REQUEST, bill.getId(), userId)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "该单据您已处理过，请刷新后重试");
        }
        throw new BizException(ResultCode.NO_PERMISSION, "当前用户不是该单据当前节点的审批人");
    }

    private void requireBillInScope(PurchaseRequest bill) {
        ScopeCondition scope = currentScope();
        if (scope.isAll()) {
            return;
        }
        if (!scope.hasDept() || !scope.getDeptIds().contains(bill.getDeptId())) {
            throw new BizException(ResultCode.NO_PERMISSION, "单据不在您的数据范围内");
        }
    }

    private ScopeCondition currentScope() {
        CurrentUser user = AuthContext.get();
        return user == null ? new ScopeCondition() : user.getScope();
    }

    private Long currentUserId() {
        return AuthContext.getOrThrow().getUserId();
    }

    /** 当前用户部门，建单部门缺省值；无部门时按参数不合法处理。 */
    private Long currentDeptId() {
        CurrentUser user = AuthContext.get();
        if (user == null || user.getDeptId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择申请部门");
        }
        return user.getDeptId();
    }

    // ================= 私有：明细行构建与校验 =================

    /**
     * 构建明细行实体并逐项校验（M2-2-1、M2-2-3、M2-2-5）：
     * 物料必须存在且启用；数量必须大于 0；建议供应商必须启用且在供货范围内；
     * 归属对象按类型校验；单位取物料档案，预估金额服务层计算。
     */
    private PurchaseRequestItem buildItem(PurchaseRequest bill, RequestItemSaveRequest request) {
        if (request.getMaterialId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择物料");
        }
        Material material = materialMapper.selectById(request.getMaterialId());
        if (material == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物料不存在");
        }
        if (!Objects.equals(material.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, "物料已停用，不能用于新申请");
        }
        if (request.getQuantity() == null || request.getQuantity().signum() <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "申请数量必须大于 0");
        }
        if (request.getRequireDate() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请填写行需求日期");
        }
        Long suggestSupplierId = null;
        if (request.getSuggestSupplierId() != null) {
            requirePermission(PERM_ITEM_SUGGEST);
            suggestSupplierId = validateSupplierScope(request.getSuggestSupplierId(), material);
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

        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setRequestId(bill.getId());
        item.setMaterialId(material.getId());
        item.setQuantity(request.getQuantity());
        item.setUnitId(material.getUnitId());
        item.setEstPrice(request.getEstPrice());
        item.setEstAmount(computeAmount(request.getQuantity(), request.getEstPrice()));
        item.setSuggestSupplierId(suggestSupplierId);
        item.setRequireDate(request.getRequireDate());
        item.setOwnerType(ownerType);
        item.setOwnerId(ownerId);
        item.setOwnerNo(ownerNo);
        item.setTransferredQty(BigDecimal.ZERO);
        item.setItemStatus(ITEM_PENDING);
        item.setRemark(request.getRemark());
        return item;
    }

    /**
     * 校验供应商启用与供货范围（M2-2-3）。供货范围口径：供应商配了范围行时，
     * 物料本身或物料所属分类必须命中其一；未配范围行视为不限。
     */
    private Long validateSupplierScope(Long supplierId, Material material) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BizException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        if (!Objects.equals(supplier.getStatus(), 1)) {
            throw new BizException(ResultCode.UNSUPPORTED, "供应商已停用，不能用于新申请");
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
        return supplier.getId();
    }

    private void validateDept(Long deptId) {
        if (deptId == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "请选择申请部门");
        }
        DictItem dept = dictItemMapper.selectById(deptId);
        if (dept == null || !Objects.equals(dept.getStatus(), 1)) {
            throw new BizException(ResultCode.NOT_FOUND, "申请部门不存在或已停用");
        }
    }

    private void requirePermission(String permCode) {
        CurrentUser user = AuthContext.get();
        if (user == null || !user.hasPermission(permCode)) {
            throw new BizException(ResultCode.NO_PERMISSION, "当前用户无权执行该操作");
        }
    }

    private BigDecimal computeAmount(BigDecimal quantity, BigDecimal estPrice) {
        if (estPrice == null || estPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return quantity.multiply(estPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal remainingQty(PurchaseRequestItem item) {
        BigDecimal transferred = item.getTransferredQty() == null ? BigDecimal.ZERO : item.getTransferredQty();
        return item.getQuantity().subtract(transferred).max(BigDecimal.ZERO);
    }

    /** 重算单据预估金额合计：只合计活跃行（待转、部分已转、已转完），退回与关闭行不计。 */
    private void recomputeTotal(Long requestId) {
        List<PurchaseRequestItem> items = itemMapper.selectByRequestId(requestId);
        BigDecimal total = items.stream()
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL
                        || item.getItemStatus() == ITEM_TRANSFERRED)
                .map(PurchaseRequestItem::getEstAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PurchaseRequest update = new PurchaseRequest();
        update.setId(requestId);
        update.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        requestMapper.updateById(update);
    }

    /**
     * 刷新需求日期：用户显式设置过则保留，未设置过（为空）时取活跃行最早需求日期。
     */
    private void refreshRequireDate(PurchaseRequest bill) {
        if (bill.getRequireDate() != null) {
            return;
        }
        LocalDate earliest = earliestRequireDate(bill.getId());
        if (earliest != null) {
            PurchaseRequest update = new PurchaseRequest();
            update.setId(bill.getId());
            update.setRequireDate(earliest);
            requestMapper.updateById(update);
        }
    }

    private LocalDate earliestRequireDate(Long requestId) {
        return itemMapper.selectByRequestId(requestId).stream()
                .filter(item -> item.getItemStatus() != ITEM_RETURNED && item.getItemStatus() != ITEM_CLOSED)
                .map(PurchaseRequestItem::getRequireDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo).orElse(null);
    }

    // ================= 私有：视图装配 =================

    private List<RequestVO> assembleList(List<PurchaseRequest> bills) {
        List<Long> billIds = bills.stream().map(PurchaseRequest::getId).collect(Collectors.toList());
        List<PurchaseRequestItem> items = itemMapper.selectByRequestIds(billIds);
        Map<Long, List<PurchaseRequestItem>> itemsByBill = items.stream()
                .collect(Collectors.groupingBy(PurchaseRequestItem::getRequestId));
        List<RequestVO> result = new ArrayList<>();
        for (PurchaseRequest bill : bills) {
            RequestVO vo = toHeaderVO(bill);
            List<PurchaseRequestItem> billItems = itemsByBill.getOrDefault(bill.getId(), List.of());
            vo.setItems(enrichItems(billItems));
            fillAggregates(vo, billItems);
            result.add(vo);
        }
        return result;
    }

    private RequestVO toHeaderVO(PurchaseRequest bill) {
        RequestVO vo = new RequestVO();
        vo.setId(bill.getId());
        vo.setRequestNo(bill.getRequestNo());
        vo.setRequestDate(bill.getRequestDate());
        vo.setDeptId(bill.getDeptId());
        vo.setDeptName(dictItemName(bill.getDeptId()));
        vo.setApplicantId(bill.getApplicantId());
        vo.setApplicantName(userRealName(bill.getApplicantId()));
        vo.setRequireDate(bill.getRequireDate());
        vo.setTotalAmount(bill.getTotalAmount());
        vo.setLifecycleStatus(bill.getLifecycleStatus());
        vo.setLifecycleStatusName(lifecycleStatusName(bill.getLifecycleStatus()));
        vo.setAuditStatus(bill.getAuditStatus());
        vo.setAuditStatusName(auditStatusName(bill.getAuditStatus()));
        vo.setCloseReason(bill.getCloseReason());
        vo.setRemark(bill.getRemark());
        return vo;
    }

    private void fillAggregates(RequestVO vo, List<PurchaseRequestItem> items) {
        List<PurchaseRequestItem> active = items.stream()
                .filter(item -> item.getItemStatus() == ITEM_PENDING || item.getItemStatus() == ITEM_PARTIAL
                        || item.getItemStatus() == ITEM_TRANSFERRED)
                .collect(Collectors.toList());
        vo.setActiveItemCount(active.size());
        vo.setRemainingQtyTotal(active.stream()
                .map(this::remainingQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private List<RequestItemVO> enrichItems(List<PurchaseRequestItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> materialIds = items.stream().map(PurchaseRequestItem::getMaterialId).distinct()
                .collect(Collectors.toList());
        List<Long> unitIds = items.stream().map(PurchaseRequestItem::getUnitId).distinct()
                .collect(Collectors.toList());
        List<Long> supplierIds = items.stream().map(PurchaseRequestItem::getSuggestSupplierId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> ownerIds = items.stream().map(PurchaseRequestItem::getOwnerId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 注意：空 map 用 HashMap 而非 Map.of()——Map.of().get(null) 会抛 NPE，
        // 而建议供应商与归属对象都可为空，键查询必然可能为 null。
        Map<Long, Material> materialById = materialMapper.selectByIds(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));
        Map<Long, DictItem> dictById = collectDictItems(unitIds);
        Map<Long, Supplier> supplierById = supplierIds.isEmpty() ? new HashMap<>()
                : supplierMapper.selectByIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity(), (a, b) -> a));
        Map<Long, OwnerObject> ownerById = ownerIds.isEmpty() ? new HashMap<>()
                : ownerObjectMapper.selectByIds(ownerIds).stream()
                .collect(Collectors.toMap(OwnerObject::getId, Function.identity(), (a, b) -> a));

        List<RequestItemVO> result = new ArrayList<>();
        for (PurchaseRequestItem item : items) {
            RequestItemVO vo = new RequestItemVO();
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
            vo.setEstPrice(item.getEstPrice());
            vo.setEstAmount(item.getEstAmount());
            vo.setSuggestSupplierId(item.getSuggestSupplierId());
            Supplier supplier = supplierById.get(item.getSuggestSupplierId());
            vo.setSuggestSupplierName(supplier == null ? null : supplier.getSupplierName());
            vo.setRequireDate(item.getRequireDate());
            vo.setOwnerType(item.getOwnerType());
            vo.setOwnerId(item.getOwnerId());
            OwnerObject owner = ownerById.get(item.getOwnerId());
            vo.setOwnerNo(owner == null ? item.getOwnerNo() : owner.getOwnerNo());
            vo.setOwnerName(owner == null ? null : owner.getOwnerName());
            vo.setTransferredQty(item.getTransferredQty());
            vo.setRemainingQty(remainingQty(item));
            vo.setItemStatus(item.getItemStatus());
            vo.setItemStatusName(itemStatusName(item.getItemStatus()));
            vo.setRemark(item.getRemark());
            result.add(vo);
        }
        return result;
    }

    private Map<Long, DictItem> collectDictItems(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return dictItemMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(DictItem::getId, Function.identity(), (a, b) -> a));
    }

    private String dictName(Map<Long, DictItem> dictById, Long id) {
        DictItem item = dictById.get(id);
        return item == null ? "" : item.getItemName();
    }

    private String dictItemName(Long id) {
        if (id == null) {
            return "";
        }
        DictItem item = dictItemMapper.selectById(id);
        return item == null ? "" : item.getItemName();
    }

    private String userRealName(Long userId) {
        if (userId == null) {
            return "";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? "" : user.getRealName();
    }

    private String lifecycleStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case LIFECYCLE_DRAFT -> "草稿";
            case LIFECYCLE_SUBMITTED -> "已提交";
            case LIFECYCLE_APPROVED -> "已审核";
            case LIFECYCLE_CLOSED -> "已关闭";
            default -> "已作废";
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

    private String itemStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case ITEM_PENDING -> "待转";
            case ITEM_PARTIAL -> "部分已转";
            case ITEM_TRANSFERRED -> "已转完";
            case ITEM_RETURNED -> "已退回";
            case ITEM_CLOSED -> "已关闭";
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
}
