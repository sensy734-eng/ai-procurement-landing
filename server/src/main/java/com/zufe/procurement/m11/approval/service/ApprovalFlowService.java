package com.zufe.procurement.m11.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m11.approval.dto.ApprovalFlowSaveRequest;
import com.zufe.procurement.m11.approval.entity.ApprovalFlowNode;
import com.zufe.procurement.m11.approval.mapper.ApprovalFlowNodeMapper;
import com.zufe.procurement.m11.approval.vo.ApprovalFlowNodeVO;
import com.zufe.procurement.m11.system.entity.SysRole;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysRoleMapper;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m11.system.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批流程配置服务，功能点 M11-5-1 审批流程配置、M11-5-2 审批人指定。
 * 采购专用简化审批：按单据类型配置固定节点，节点顺序固定，
 * 不做金额区间条件分支；审批人按角色或具体用户指定，不做发起人自选与转办。
 * 审批任务的生成与流转在轮次 4 随 M2 一并实现（M11-5-3、M11-5-4、M11-5-5）。
 */
@Service
public class ApprovalFlowService {

    private static final int BILL_TYPE_REQUEST = 1;
    private static final int BILL_TYPE_ORDER = 2;

    private static final int APPROVER_BY_ROLE = 1;
    private static final int APPROVER_BY_USER = 2;

    private final ApprovalFlowNodeMapper nodeMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final OperationLogService operationLogService;

    public ApprovalFlowService(ApprovalFlowNodeMapper nodeMapper, SysRoleMapper roleMapper,
                               SysUserMapper userMapper, OperationLogService operationLogService) {
        this.nodeMapper = nodeMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * M11-5-1 查询某单据类型的节点配置，按节点序号升序，节点内审批人合并为一个列表。
     */
    public List<ApprovalFlowNodeVO> listByBillType(Integer billType) {
        requireBillType(billType);
        List<ApprovalFlowNode> rows = nodeMapper.selectList(new LambdaQueryWrapper<ApprovalFlowNode>()
                .eq(ApprovalFlowNode::getBillType, billType)
                .orderByAsc(ApprovalFlowNode::getNodeSeq)
                .orderByAsc(ApprovalFlowNode::getId));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> roleIds = rows.stream().map(ApprovalFlowNode::getRoleId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> userIds = rows.stream().map(ApprovalFlowNode::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysRole> roleById = roleIds.isEmpty() ? Map.of()
                : roleMapper.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity(), (a, b) -> a));
        Map<Long, SysUser> userById = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        Map<Integer, ApprovalFlowNodeVO> bySeq = new LinkedHashMap<>();
        for (ApprovalFlowNode row : rows) {
            ApprovalFlowNodeVO vo = bySeq.computeIfAbsent(row.getNodeSeq(), seq -> {
                ApprovalFlowNodeVO created = new ApprovalFlowNodeVO();
                created.setNodeSeq(seq);
                created.setNodeName(row.getNodeName());
                created.setSignType(row.getSignType());
                created.setRemark(row.getRemark());
                created.setApprovers(new ArrayList<>());
                return created;
            });
            ApprovalFlowNodeVO.ApproverVO approver = new ApprovalFlowNodeVO.ApproverVO();
            approver.setApproverType(row.getApproverType());
            approver.setRoleId(row.getRoleId());
            approver.setUserId(row.getUserId());
            if (row.getApproverType() != null && row.getApproverType() == APPROVER_BY_ROLE) {
                SysRole role = roleById.get(row.getRoleId());
                approver.setDisplayName(role == null ? "角色已删除" : "角色：" + role.getRoleName());
            } else {
                SysUser user = userById.get(row.getUserId());
                approver.setDisplayName(user == null ? "用户已删除"
                        : "用户：" + user.getRealName() + "（" + user.getUsername() + "）");
            }
            vo.getApprovers().add(approver);
        }
        return new ArrayList<>(bySeq.values());
    }

    /**
     * M11-5-1、M11-5-2 整体保存某单据类型的节点配置。
     * 节点序号必须从 1 连续，每个节点至少一个审批人，避免生成无人可审的节点。
     */
    @Transactional
    public void save(Integer billType, ApprovalFlowSaveRequest request) {
        requireBillType(billType);
        List<ApprovalFlowSaveRequest.NodeItem> nodes = request.getNodes();
        validateNodeSequence(nodes);

        String before = describe(billType);

        // 先整体校验审批人，全部通过后再落库，避免半途失败留下残缺配置
        for (ApprovalFlowSaveRequest.NodeItem node : nodes) {
            validateApprovers(node);
        }

        nodeMapper.deletePhysicallyByBillType(billType);
        for (ApprovalFlowSaveRequest.NodeItem node : nodes) {
            for (ApprovalFlowSaveRequest.ApproverItem approver : dedupeApprovers(node.getApprovers())) {
                ApprovalFlowNode entity = new ApprovalFlowNode();
                entity.setBillType(billType);
                entity.setNodeSeq(node.getNodeSeq());
                entity.setNodeName(node.getNodeName().trim());
                entity.setApproverType(approver.getApproverType());
                entity.setRoleId(approver.getApproverType() == APPROVER_BY_ROLE ? approver.getRoleId() : null);
                entity.setUserId(approver.getApproverType() == APPROVER_BY_USER ? approver.getUserId() : null);
                entity.setSignType(node.getSignType());
                entity.setStatus(1);
                entity.setRemark(node.getNodeName());
                nodeMapper.insert(entity);
            }
        }

        List<String> after = nodes.stream()
                .map(n -> n.getNodeSeq() + "." + n.getNodeName() + "（"
                        + (n.getSignType() == 2 ? "会签" : "或签") + "，"
                        + n.getApprovers().size() + " 人）")
                .collect(Collectors.toList());
        operationLogService.record("M11", "config", "approval_flow_node", null,
                billType == BILL_TYPE_REQUEST ? "采购申请" : "采购订单",
                "审批流程变更：" + before + " -> " + after);
    }

    /**
     * 审批人候选：可指定的角色与可指定到人的用户，供配置页下拉。
     */
    public Map<String, Object> approverOptions() {
        List<Map<String, Object>> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1).orderByAsc(SysRole::getId))
                .stream().map(r -> Map.<String, Object>of(
                        "id", r.getId(), "code", r.getRoleCode(), "name", r.getRoleName()))
                .collect(Collectors.toList());
        List<Map<String, Object>> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1).orderByAsc(SysUser::getId))
                .stream().map(u -> Map.<String, Object>of(
                        "id", u.getId(), "username", u.getUsername(),
                        "realName", u.getRealName(), "deptId", u.getDeptId()))
                .collect(Collectors.toList());
        return Map.of("roles", roles, "users", users);
    }

    private void requireBillType(Integer billType) {
        if (billType == null || (billType != BILL_TYPE_REQUEST && billType != BILL_TYPE_ORDER)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "单据类型只能为 1 采购申请或 2 采购订单");
        }
    }

    private void validateNodeSequence(List<ApprovalFlowSaveRequest.NodeItem> nodes) {
        Set<Integer> seqs = new LinkedHashSet<>();
        for (ApprovalFlowSaveRequest.NodeItem node : nodes) {
            if (!seqs.add(node.getNodeSeq())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "节点序号重复：" + node.getNodeSeq());
            }
        }
        int expected = 1;
        for (Integer seq : seqs.stream().sorted().toList()) {
            if (seq != expected) {
                throw new BizException(ResultCode.INVALID_PARAMETER,
                        "节点序号必须从 1 连续，缺少序号 " + expected);
            }
            expected++;
        }
    }

    private void validateApprovers(ApprovalFlowSaveRequest.NodeItem node) {
        if (node.getApprovers() == null || node.getApprovers().isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "节点 " + node.getNodeSeq() + " 至少指定一个审批人");
        }
        if (node.getSignType() != null && node.getSignType() == 2 && node.getApprovers().size() < 2) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "节点 " + node.getNodeSeq() + " 设为会签时至少需要两个审批人");
        }
        for (ApprovalFlowSaveRequest.ApproverItem approver : node.getApprovers()) {
            if (approver.getApproverType() == APPROVER_BY_ROLE) {
                if (approver.getRoleId() == null) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "按角色指定时必须选择角色");
                }
                SysRole role = roleMapper.selectById(approver.getRoleId());
                if (role == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "角色不存在：" + approver.getRoleId());
                }
                if (role.getStatus() == null || role.getStatus() != 1) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "角色已停用：" + role.getRoleName());
                }
            } else if (approver.getApproverType() == APPROVER_BY_USER) {
                if (approver.getUserId() == null) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "按用户指定时必须选择用户");
                }
                SysUser user = userMapper.selectById(approver.getUserId());
                if (user == null) {
                    throw new BizException(ResultCode.NOT_FOUND, "用户不存在：" + approver.getUserId());
                }
                if (user.getStatus() == null || user.getStatus() != 1) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "用户已停用：" + user.getRealName());
                }
            } else {
                throw new BizException(ResultCode.INVALID_PARAMETER, "审批人指定方式只能为 1 或 2");
            }
        }
    }

    private List<ApprovalFlowSaveRequest.ApproverItem> dedupeApprovers(
            List<ApprovalFlowSaveRequest.ApproverItem> approvers) {
        Map<String, ApprovalFlowSaveRequest.ApproverItem> unique = new LinkedHashMap<>();
        for (ApprovalFlowSaveRequest.ApproverItem approver : approvers) {
            String key = approver.getApproverType() + ":"
                    + (approver.getApproverType() == APPROVER_BY_ROLE ? approver.getRoleId() : approver.getUserId());
            unique.putIfAbsent(key, approver);
        }
        return new ArrayList<>(unique.values());
    }

    private String describe(Integer billType) {
        List<ApprovalFlowNodeVO> existing = listByBillType(billType);
        if (existing.isEmpty()) {
            return "未配置";
        }
        return existing.stream()
                .map(n -> n.getNodeSeq() + "." + n.getNodeName())
                .collect(Collectors.joining("，"));
    }
}
