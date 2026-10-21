package com.zufe.procurement.m11.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m11.approval.entity.ApprovalFlowNode;
import com.zufe.procurement.m11.approval.entity.ApprovalTask;
import com.zufe.procurement.m11.approval.mapper.ApprovalFlowNodeMapper;
import com.zufe.procurement.m11.approval.mapper.ApprovalTaskMapper;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m2.request.vo.ApprovalHistoryVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批引擎，功能点 M11-5-3 或签与会签规则、M11-5-4 审批任务生成与流转、
 * M11-5-5 审批结果回写单据。采购申请与采购订单共用，不开发通用工作流引擎。
 *
 * 口径（与功能模块清单 M11-5-3 逐条对齐）：
 * - 或签：任一通过即推进，其余待处理任务转已失效；
 * - 会签：全部通过才推进，任一驳回则整单驳回；
 * - 或签节点的单次驳回不否决整单，仅记录意见，仍等待其他审批人；
 *   全部处理完毕且无人通过时整单驳回（避免或签节点被单人否死后锁死）；
 * - 任务按节点顺序惰性生成：提交时只生成首节点任务，节点完成才生成下一节点，
 *   避免后续节点审批人提前看到待办；
 * - 处理动作在同一事务内完成：任务状态更新、节点推进判断、单据回写、日志。
 *
 * 单据侧的状态回写通过 BillApprovalHandler 回调完成，引擎不感知具体单据表，
 * M2 的采购申请与 M3 的采购订单各自实现回调，复用同一套流转规则。
 */
@Service
public class ApprovalEngineService {

    /** 1 采购申请、2 采购订单 */
    public static final int BILL_TYPE_REQUEST = 1;
    public static final int BILL_TYPE_ORDER = 2;

    private static final int SIGN_TYPE_ANY = 1;
    private static final int SIGN_TYPE_ALL = 2;

    private static final int TASK_PENDING = 1;
    private static final int TASK_PASSED = 2;
    private static final int TASK_REJECTED = 3;
    private static final int TASK_VOIDED = 4;

    private static final DateTimeFormatter TASK_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int TASK_NO_ATTEMPTS = 3;

    private final ApprovalFlowNodeMapper nodeMapper;
    private final ApprovalTaskMapper taskMapper;
    private final SysUserMapper userMapper;

    public ApprovalEngineService(ApprovalFlowNodeMapper nodeMapper,
                                 ApprovalTaskMapper taskMapper,
                                 SysUserMapper userMapper) {
        this.nodeMapper = nodeMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
    }

    /**
     * 单据侧审批结果回写回调。实现方在调用方事务内执行，保证同事务回写。
     */
    public interface BillApprovalHandler {
        /** 末节点全部通过：审批结果状态置通过，单据进入可转订单（或可执行）状态 */
        void onPassed(Long billId);

        /** 审批驳回：审批结果状态置驳回，单据生命周期回落草稿 */
        void onRejected(Long billId);
    }

    // ---------------- M11-5-4 任务生成 ----------------

    /**
     * 提交审批时生成首节点任务。节点内按审批人配置展开为多条任务，
     * 一人一条；或签会签方式从节点配置快照到任务，单据归档后仍可读。
     */
    @Transactional
    public void generateFirstNodeTasks(int billType, Long billId, String billNo) {
        List<ApprovalFlowNode> nodes = activeNodes(billType);
        if (nodes.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    billType == BILL_TYPE_REQUEST ? "采购申请审批流程未配置，无法提交" : "采购订单审批流程未配置，无法提交");
        }
        generateNodeTasks(billType, billId, billNo, nodes.get(0).getNodeSeq(), nodes);
    }

    private void generateNodeTasks(int billType, Long billId, String billNo,
                                   int nodeSeq, List<ApprovalFlowNode> nodes) {
        List<ApprovalFlowNode> rows = nodes.stream()
                .filter(n -> Objects.equals(n.getNodeSeq(), nodeSeq))
                .collect(Collectors.toList());
        ApprovalFlowNode first = rows.get(0);
        Set<Long> approverIds = resolveApprovers(rows);
        if (approverIds.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "审批节点「" + first.getNodeName() + "」无可用审批人，请先调整审批配置");
        }
        for (Long approverId : approverIds) {
            insertTask(billType, billId, billNo, nodeSeq, first.getNodeName(),
                    approverId, first.getSignType());
        }
    }

    /**
     * 插入一条待处理任务。任务编号并发下可能撞唯一键，
     * 失败后重新取最大流水重试，重试上限 3 次（并发提交只成功一次的另一半保证）。
     */
    private void insertTask(int billType, Long billId, String billNo, int nodeSeq,
                            String nodeName, Long approverId, Integer signType) {
        for (int attempt = 1; attempt <= TASK_NO_ATTEMPTS; attempt++) {
            ApprovalTask task = new ApprovalTask();
            task.setTaskNo(nextTaskNo());
            task.setBillType(billType);
            task.setBillId(billId);
            task.setBillNo(billNo);
            task.setNodeSeq(nodeSeq);
            task.setNodeName(nodeName);
            task.setApproverId(approverId);
            task.setSignType(signType);
            task.setTaskStatus(TASK_PENDING);
            try {
                taskMapper.insert(task);
                return;
            } catch (DuplicateKeyException ex) {
                if (attempt == TASK_NO_ATTEMPTS) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "任务编号生成冲突，请重试");
                }
            }
        }
    }

    /**
     * 解析节点审批人：按角色取该角色下全部启用用户，按用户取指定用户；
     * 同一人多行配置只生成一条任务（去重），避免重复待办。
     */
    private Set<Long> resolveApprovers(List<ApprovalFlowNode> rows) {
        Set<Long> approverIds = new LinkedHashSet<>();
        for (ApprovalFlowNode row : rows) {
            if (Objects.equals(row.getApproverType(), 1)) {
                approverIds.addAll(userMapper.selectActiveUserIdsByRole(row.getRoleId()));
            } else if (Objects.equals(row.getApproverType(), 2)) {
                SysUser user = userMapper.selectById(row.getUserId());
                if (user != null && Objects.equals(user.getStatus(), 1)) {
                    approverIds.add(user.getId());
                }
            }
        }
        return approverIds;
    }

    private List<ApprovalFlowNode> activeNodes(int billType) {
        return nodeMapper.selectList(new LambdaQueryWrapper<ApprovalFlowNode>()
                .eq(ApprovalFlowNode::getBillType, billType)
                .eq(ApprovalFlowNode::getStatus, 1)
                .orderByAsc(ApprovalFlowNode::getNodeSeq)
                .orderByAsc(ApprovalFlowNode::getId));
    }

    // ---------------- M11-5-3 / M11-5-5 任务处理与结果回写 ----------------

    /**
     * 处理一个审批任务。通过时按或签会签规则判断节点推进；
     * 驳回时会签任一驳回整单驳回，或签记录后继续等待其他审批人。
     * 任务更新带乐观锁，并发处理同一任务时只有一人成功。
     */
    @Transactional
    public void process(Long taskId, Long userId, boolean pass, String opinion,
                        int billType, Long billId, String billNo, BillApprovalHandler handler) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "审批任务不存在");
        }
        if (!Objects.equals(task.getBillId(), billId) || !Objects.equals(task.getBillType(), billType)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "任务与单据不匹配");
        }
        if (!Objects.equals(task.getTaskStatus(), TASK_PENDING)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "任务已被处理，请刷新后重试");
        }
        if (!Objects.equals(task.getApproverId(), userId)) {
            throw new BizException(ResultCode.NO_PERMISSION, "当前用户不是该任务的审批人");
        }
        if (!pass && (opinion == null || opinion.isBlank())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "驳回必须填写审批意见");
        }

        task.setTaskStatus(pass ? TASK_PASSED : TASK_REJECTED);
        task.setOpinion(opinion == null ? null : opinion.trim());
        task.setHandleTime(LocalDateTime.now());
        if (taskMapper.updateById(task) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "任务已被处理，请刷新后重试");
        }

        List<ApprovalFlowNode> nodes = activeNodes(billType);
        if (pass) {
            if (Objects.equals(task.getSignType(), SIGN_TYPE_ANY)) {
                // 或签：任一通过即推进，其余待处理任务失效
                voidPendingInNode(billType, billId, task.getNodeSeq(), task.getId());
                advance(billType, billId, billNo, task.getNodeSeq() + 1, nodes, handler);
            } else {
                // 会签：全部通过才推进
                evaluateNode(billType, billId, billNo, task.getNodeSeq(), nodes, handler);
            }
        } else {
            if (Objects.equals(task.getSignType(), SIGN_TYPE_ALL)) {
                // 会签：任一驳回则整单驳回
                voidPendingInNode(billType, billId, task.getNodeSeq(), task.getId());
                handler.onRejected(billId);
            } else {
                // 或签：单人驳回不否决整单，仍等待其他审批人
                evaluateNode(billType, billId, billNo, task.getNodeSeq(), nodes, handler);
            }
        }
    }

    /**
     * 节点推进判断：仍有待处理任务则等待；全部处理完毕且至少一人通过则进入
     * 下一节点（无下一节点则回写审批通过）；无人通过则整单驳回。
     */
    private void evaluateNode(int billType, Long billId, String billNo, int nodeSeq,
                              List<ApprovalFlowNode> nodes, BillApprovalHandler handler) {
        List<ApprovalTask> tasks = taskMapper.selectByBillAndNode(billType, billId, nodeSeq);
        long pending = tasks.stream().filter(t -> Objects.equals(t.getTaskStatus(), TASK_PENDING)).count();
        if (pending > 0) {
            return;
        }
        long passed = tasks.stream().filter(t -> Objects.equals(t.getTaskStatus(), TASK_PASSED)).count();
        if (passed > 0) {
            advance(billType, billId, billNo, nodeSeq + 1, nodes, handler);
        } else {
            handler.onRejected(billId);
        }
    }

    /** 推进到下一节点：生成该节点任务；无下一节点则回写审批通过（M11-5-5）。 */
    private void advance(int billType, Long billId, String billNo, int nextSeq,
                         List<ApprovalFlowNode> nodes, BillApprovalHandler handler) {
        boolean hasNext = nodes.stream().anyMatch(n -> Objects.equals(n.getNodeSeq(), nextSeq));
        if (hasNext) {
            generateNodeTasks(billType, billId, billNo, nextSeq, nodes);
        } else {
            handler.onPassed(billId);
        }
    }

    private void voidPendingInNode(int billType, Long billId, int nodeSeq, Long exceptTaskId) {
        List<ApprovalTask> pending = taskMapper.selectByBillAndNode(billType, billId, nodeSeq).stream()
                .filter(t -> Objects.equals(t.getTaskStatus(), TASK_PENDING)
                        && !Objects.equals(t.getId(), exceptTaskId))
                .collect(Collectors.toList());
        for (ApprovalTask task : pending) {
            ApprovalTask update = new ApprovalTask();
            update.setId(task.getId());
            update.setVersion(task.getVersion());
            update.setTaskStatus(TASK_VOIDED);
            taskMapper.updateById(update);
        }
    }

    /**
     * 撤回或按行退回时，将单据下全部待处理任务置已失效（M2-3-4、M2-2-4）。
     */
    @Transactional
    public void invalidateTasks(int billType, Long billId) {
        List<ApprovalTask> pending = taskMapper.selectByBill(billType, billId).stream()
                .filter(t -> Objects.equals(t.getTaskStatus(), TASK_PENDING))
                .collect(Collectors.toList());
        for (ApprovalTask task : pending) {
            ApprovalTask update = new ApprovalTask();
            update.setId(task.getId());
            update.setVersion(task.getVersion());
            update.setTaskStatus(TASK_VOIDED);
            taskMapper.updateById(update);
        }
    }

    /**
     * 按行退回专用：将当前审批人的任务标记已驳回并留痕，但不触发节点推进判断
     * （按行退回是行级动作，不是整单否决，剩余行由其余审批人或节点重启继续处理）。
     */
    @Transactional
    public void markTaskRejectedForReturn(Long taskId, Long userId, String opinion) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "审批任务不存在");
        }
        if (!Objects.equals(task.getTaskStatus(), TASK_PENDING)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "任务已被处理，请刷新后重试");
        }
        if (!Objects.equals(task.getApproverId(), userId)) {
            throw new BizException(ResultCode.NO_PERMISSION, "当前用户不是该任务的审批人");
        }
        task.setTaskStatus(TASK_REJECTED);
        task.setOpinion(opinion.trim());
        task.setHandleTime(LocalDateTime.now());
        if (taskMapper.updateById(task) <= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "任务已被处理，请刷新后重试");
        }
    }

    /**
     * 按行退回后当前节点无待处理任务时，按原配置重启该节点任务，
     * 保证剩余行仍有审批人可处理（简化审批下的节点重启口径）。
     */
    @Transactional
    public void restartNodeIfIdle(int billType, Long billId, String billNo, int nodeSeq) {
        if (taskMapper.countPendingByNode(billType, billId, nodeSeq) > 0) {
            return;
        }
        List<ApprovalFlowNode> nodes = activeNodes(billType);
        boolean exists = nodes.stream().anyMatch(n -> Objects.equals(n.getNodeSeq(), nodeSeq));
        if (exists) {
            generateNodeTasks(billType, billId, billNo, nodeSeq, nodes);
        }
    }

    // ---------------- 查询：审批历史与待办 ----------------

    /** M2-3-6 审批历史，按节点序与任务序排列，含已失效任务。 */
    public List<ApprovalHistoryVO> history(int billType, Long billId) {
        List<ApprovalTask> tasks = taskMapper.selectByBill(billType, billId);
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> userById = userMapper.selectByIds(tasks.stream()
                        .map(ApprovalTask::getApproverId).filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        List<ApprovalHistoryVO> result = new ArrayList<>();
        for (ApprovalTask task : tasks) {
            ApprovalHistoryVO vo = new ApprovalHistoryVO();
            vo.setTaskId(task.getId());
            vo.setTaskNo(task.getTaskNo());
            vo.setNodeSeq(task.getNodeSeq());
            vo.setNodeName(task.getNodeName());
            vo.setApproverId(task.getApproverId());
            SysUser user = userById.get(task.getApproverId());
            vo.setApproverName(user == null ? "用户已删除" : user.getRealName());
            vo.setSignType(task.getSignType());
            vo.setSignTypeName(Objects.equals(task.getSignType(), SIGN_TYPE_ALL) ? "会签" : "或签");
            vo.setTaskStatus(task.getTaskStatus());
            vo.setTaskStatusName(taskStatusName(task.getTaskStatus()));
            vo.setOpinion(task.getOpinion());
            vo.setHandleTime(task.getHandleTime());
            result.add(vo);
        }
        return result;
    }

    /**
     * M2-3-8 我的待办：当前用户的待处理任务。分页与数据范围过滤由业务服务
     * 按单据表完成（引擎不感知具体单据表），此处只按审批人取任务。
     */
    public List<ApprovalTask> pendingTasksForApprover(Long userId, int billType) {
        return taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getBillType, billType)
                .eq(ApprovalTask::getApproverId, userId)
                .eq(ApprovalTask::getTaskStatus, TASK_PENDING)
                .orderByDesc(ApprovalTask::getId));
    }

    /** 当前用户在单据上的待处理任务，没有则返回 null（供业务服务定位处理人）。 */
    public ApprovalTask pendingTaskForApprover(int billType, Long billId, Long userId) {
        return taskMapper.selectByBill(billType, billId).stream()
                .filter(t -> Objects.equals(t.getTaskStatus(), TASK_PENDING))
                .filter(t -> Objects.equals(t.getApproverId(), userId))
                .findFirst().orElse(null);
    }

    /**
     * 单据是否还有待处理任务（供提交入口做重复提交防护）。
     * 反审后原批次任务已全部处理完成（留历史），此时无待处理任务，允许重新提交；
     * 审批进行中（有 1 待处理任务）时重复提交会生成第二批任务，必须拦掉。
     */
    public boolean hasPendingTask(int billType, Long billId) {
        return taskMapper.selectByBill(billType, billId).stream()
                .anyMatch(t -> Objects.equals(t.getTaskStatus(), TASK_PENDING));
    }

    /**
     * 撤回前校验（M2-3-4）：本次提交生成的任务中是否已有人处理（通过或驳回）。
     * 提交时任务先插入、提交日志后写入，故本批任务 id 落在调用方给的
     * (lowerExclusive, upperInclusive] 区间内；驳回后重新提交的历史处理记录不在区间，不会误拦。
     */
    public boolean hasHandledInIdRange(int billType, Long billId, long lowerExclusive, long upperInclusive) {
        return taskMapper.selectByBill(billType, billId).stream()
                .filter(t -> Objects.equals(t.getTaskStatus(), TASK_PASSED)
                        || Objects.equals(t.getTaskStatus(), TASK_REJECTED))
                .anyMatch(t -> t.getId() > lowerExclusive && t.getId() <= upperInclusive);
    }

    /** 单据是否已有该用户的处理记录（通过或驳回），供审批入口区分"已处理过"与"非审批人"。 */
    public boolean hasHandledByApprover(int billType, Long billId, Long userId) {
        return taskMapper.selectByBill(billType, billId).stream()
                .anyMatch(t -> Objects.equals(t.getApproverId(), userId)
                        && (Objects.equals(t.getTaskStatus(), TASK_PASSED)
                        || Objects.equals(t.getTaskStatus(), TASK_REJECTED)));
    }

    private String taskStatusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case TASK_PENDING -> "待处理";
            case TASK_PASSED -> "已通过";
            case TASK_REJECTED -> "已驳回";
            case TASK_VOIDED -> "已失效";
            default -> "";
        };
    }

    /**
     * 任务编号：TASK-{yyyyMMdd}-{seq:4}。uk_task_no 不含 is_deleted，
     * 续号连同已删除记录取最大，单号一旦生成不回收。
     */
    private String nextTaskNo() {
        String prefix = "TASK-" + LocalDate.now().format(TASK_DATE) + "-";
        Integer maxSeq = taskMapper.maxSeqByPrefix(prefix);
        long next = (maxSeq == null ? 0L : maxSeq) + 1L;
        return prefix + String.format("%04d", next);
    }
}
