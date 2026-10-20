package com.zufe.procurement.m11.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m11.system.dto.UserSaveRequest;
import com.zufe.procurement.m11.system.entity.SysRole;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.entity.SysUserRole;
import com.zufe.procurement.m11.system.mapper.SysRoleMapper;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m11.system.mapper.SysUserRoleMapper;
import com.zufe.procurement.m11.system.vo.ResetPwdVO;
import com.zufe.procurement.m11.system.vo.UserVO;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务。覆盖功能点：
 * M11-2-1 用户列表与检索、M11-2-2 新增与编辑、M11-2-3 启用与停用、
 * M11-2-4 重置密码、M11-2-6 用户角色分配。
 * 部门维度数据范围在此注入，体现“涉及部门的查询必须带范围条件”的约定。
 */
@Slf4j
@Service
public class SysUserService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 2;

    /** 初始密码字符集，去掉 0O1lI 等易混字符，便于管理员口头告知 */
    private static final char[] PWD_CHARS =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789@#$".toCharArray();
    private static final int INITIAL_PWD_LENGTH = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final DictItemMapper dictItemMapper;
    private final DataScopeService dataScopeService;
    private final OperationLogService operationLogService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SysUserService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                          SysRoleMapper roleMapper, DictItemMapper dictItemMapper,
                          DataScopeService dataScopeService, OperationLogService operationLogService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.dictItemMapper = dictItemMapper;
        this.dataScopeService = dataScopeService;
        this.operationLogService = operationLogService;
    }

    /**
     * M11-2-1 用户列表与检索。keyword 匹配账号与姓名，deptId、status 为精确筛选。
     */
    public PageResult<UserVO> page(long pageNo, long pageSize, String keyword,
                                   Long deptId, Integer status, CurrentUser currentUser) {
        Page<SysUser> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100));
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like("username", trimmed).or().like("real_name", trimmed));
        }
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        ScopeCondition scope = currentUser.getScope() == null ? new ScopeCondition() : currentUser.getScope();
        dataScopeService.applyDept(wrapper, "dept_id", scope);
        wrapper.orderByDesc("id");

        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        Map<Long, String> deptNames = loadDeptNames(result.getRecords());
        Map<Long, List<SysRole>> roleMap = loadRoles(result.getRecords());
        List<UserVO> records = result.getRecords().stream()
                .map(user -> toVO(user, deptNames.get(user.getDeptId()), roleMap.get(user.getId())))
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * M11-2-2 新增用户。账号唯一性校验连逻辑删除的账号一起查，
     * 因为唯一键 uk_username 不含 is_deleted。
     * 初始密码只在本次响应返回一次，不落库、不写日志。
     */
    @Transactional
    public ResetPwdVO create(UserSaveRequest request) {
        if (userMapper.countByUsernameIncludeDeleted(request.getUsername()) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "账号已存在：" + request.getUsername());
        }
        requireDeptExists(request.getDeptId());
        List<Long> roleIds = normalizeRoleIds(request.getRoleIds());
        if (roleIds.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "新增用户至少分配一个角色");
        }
        validateRoles(roleIds);

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        // 新建即给一个初始密码并要求首次登录修改，避免出现无密码账号
        String initialPassword = randomPassword();
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setPwdNeedChange(1);
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setRealName(request.getRealName());
        user.setDeptId(request.getDeptId());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRemark(request.getRemark());
        user.setStatus(STATUS_ENABLED);
        userMapper.insert(user);

        rebuildUserRoles(user.getId(), roleIds);
        operationLogService.record("M11", "create", "sys_user", user.getId(), user.getUsername(),
                "新增用户 " + user.getUsername() + "，角色 " + roleIds);

        ResetPwdVO vo = new ResetPwdVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setInitialPassword(initialPassword);
        return vo;
    }

    /**
     * M11-2-2 编辑用户。账号不可修改，角色随用户一并保存（M11-2-6）。
     */
    @Transactional
    public void update(UserSaveRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "编辑时用户id不能为空");
        }
        SysUser exists = requireUser(request.getId());
        requireDeptExists(request.getDeptId());
        List<Long> roleIds = normalizeRoleIds(request.getRoleIds());

        String before = snapshot(exists);
        SysUser update = new SysUser();
        update.setId(exists.getId());
        update.setRealName(request.getRealName());
        update.setDeptId(request.getDeptId());
        update.setPhone(request.getPhone());
        update.setEmail(request.getEmail());
        update.setRemark(request.getRemark());
        userMapper.updateById(update);

        List<Long> beforeRoles = userRoleMapper.selectRoleIds(exists.getId());
        if (!roleIds.isEmpty()) {
            validateRoles(roleIds);
            rebuildUserRoles(exists.getId(), roleIds);
        }
        operationLogService.record("M11", "update", "sys_user", exists.getId(), exists.getUsername(),
                "修改前 " + before + "；修改后 姓名=" + request.getRealName() + " 部门=" + request.getDeptId()
                        + " 角色 " + beforeRoles + " -> " + roleIds);
    }

    /**
     * M11-2-3 启用与停用。停用后不可登录，已建立会话在下次请求时被拒绝。
     * 禁止停用自己，避免管理员把自己锁在系统外。
     */
    @Transactional
    public void toggleStatus(Long userId, Integer status, CurrentUser operator) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "状态只能为 1 启用或 2 停用");
        }
        SysUser user = requireUser(userId);
        if (status == STATUS_DISABLED && Objects.equals(operator.getUserId(), userId)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "不能停用当前登录账号");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
        operationLogService.record("M11", status == STATUS_ENABLED ? "enable" : "disable",
                "sys_user", userId, user.getUsername(),
                "状态 " + user.getStatus() + " -> " + status);
    }

    /**
     * M11-2-4 重置密码。生成初始密码并置首登必改，返回给管理员的明文只此一次。
     */
    @Transactional
    public ResetPwdVO resetPassword(Long userId) {
        SysUser user = requireUser(userId);
        String initialPassword = randomPassword();
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(initialPassword));
        update.setPwdNeedChange(1);
        update.setPwdUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        operationLogService.record("M11", "resetpwd", "sys_user", userId, user.getUsername(),
                "重置密码并置首次登录必须修改");

        ResetPwdVO vo = new ResetPwdVO();
        vo.setUserId(userId);
        vo.setUsername(user.getUsername());
        vo.setInitialPassword(initialPassword);
        return vo;
    }

    /**
     * M11-2-6 用户角色分配，多角色取并集。关联表物理删除后重建。
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = requireUser(userId);
        List<Long> normalized = normalizeRoleIds(roleIds);
        if (normalized.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "至少保留一个角色");
        }
        validateRoles(normalized);
        List<Long> before = userRoleMapper.selectRoleIds(userId);
        rebuildUserRoles(userId, normalized);
        operationLogService.record("M11", "grant_role", "sys_user", userId, user.getUsername(),
                "角色 " + before + " -> " + normalized);
    }

    /**
     * 用户详情，供编辑表单回填。
     */
    public UserVO detail(Long userId) {
        SysUser user = requireUser(userId);
        Map<Long, String> deptNames = loadDeptNames(List.of(user));
        Map<Long, List<SysRole>> roleMap = loadRoles(List.of(user));
        return toVO(user, deptNames.get(user.getDeptId()), roleMap.get(user.getId()));
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void requireDeptExists(Long deptId) {
        DictItem dept = deptId == null ? null : dictItemMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "部门不存在");
        }
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        if (roleIds == null) {
            return List.of();
        }
        return roleIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private void validateRoles(List<Long> roleIds) {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds));
        if (roles.size() != roleIds.size()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "存在无效角色");
        }
        boolean disabled = roles.stream().anyMatch(r -> r.getStatus() == null || r.getStatus() != 1);
        if (disabled) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "存在已停用角色，不能分配");
        }
    }

    /** 关联表唯一键不含 is_deleted，故物理删除后重建 */
    private void rebuildUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deletePhysicallyByUserId(userId);
        for (Long roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setIsDeleted(0);
            relation.setVersion(0);
            userRoleMapper.insert(relation);
        }
    }

    private String snapshot(SysUser user) {
        return "姓名=" + user.getRealName() + " 部门=" + user.getDeptId()
                + " 电话=" + user.getPhone() + " 邮箱=" + user.getEmail();
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(INITIAL_PWD_LENGTH);
        for (int i = 0; i < INITIAL_PWD_LENGTH; i++) {
            sb.append(PWD_CHARS[RANDOM.nextInt(PWD_CHARS.length)]);
        }
        return sb.toString();
    }

    private Map<Long, String> loadDeptNames(List<SysUser> users) {
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        return dictItemMapper.selectByIds(deptIds).stream()
                .collect(Collectors.toMap(DictItem::getId, DictItem::getItemName, (a, b) -> a));
    }

    /** 批量装配角色，避免列表页逐行查询 */
    private Map<Long, List<SysRole>> loadRoles(List<SysUser> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        List<SysUserRole> relations = userRoleMapper.selectList(new QueryWrapper<SysUserRole>()
                .in("user_id", userIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = relations.stream().map(SysUserRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysRole> roleById = roleMapper.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<SysRole>> result = new java.util.HashMap<>();
        for (SysUserRole relation : relations) {
            SysRole role = roleById.get(relation.getRoleId());
            if (role != null) {
                result.computeIfAbsent(relation.getUserId(), k -> new ArrayList<>()).add(role);
            }
        }
        return result;
    }

    private UserVO toVO(SysUser user, String deptName, List<SysRole> roles) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(deptName);
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRemark(user.getRemark());
        vo.setStatus(user.getStatus());
        vo.setPwdNeedChange(user.getPwdNeedChange());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreatedAt(user.getCreatedAt());
        if (roles != null && !roles.isEmpty()) {
            vo.setRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toList()));
            vo.setRoleNames(roles.stream().map(SysRole::getRoleName).collect(Collectors.toList()));
        } else {
            vo.setRoleIds(List.of());
            vo.setRoleNames(List.of());
        }
        return vo;
    }
}
