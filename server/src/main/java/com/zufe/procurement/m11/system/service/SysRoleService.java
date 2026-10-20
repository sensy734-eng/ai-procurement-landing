package com.zufe.procurement.m11.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m11.system.dto.RoleSaveRequest;
import com.zufe.procurement.m11.system.entity.SysPermission;
import com.zufe.procurement.m11.system.entity.SysRole;
import com.zufe.procurement.m11.system.entity.SysRolePermission;
import com.zufe.procurement.m11.system.mapper.SysPermissionMapper;
import com.zufe.procurement.m11.system.mapper.SysRoleMapper;
import com.zufe.procurement.m11.system.mapper.SysRolePermissionMapper;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m11.system.mapper.SysUserRoleMapper;
import com.zufe.procurement.m11.system.vo.RoleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 角色与授权服务。覆盖功能点：
 * M11-2-5 角色列表与维护、M11-3-2 角色授权、M11-3-5 授权变更留痕。
 * 内置四角色（采购员、采购主管、仓管员、系统管理员）不允许删除，编码不允许修改。
 */
@Service
public class SysRoleService {

    private static final int STATUS_ENABLED = 1;

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final OperationLogService operationLogService;

    public SysRoleService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                          SysRolePermissionMapper rolePermissionMapper, SysUserRoleMapper userRoleMapper,
                          SysUserMapper userMapper, OperationLogService operationLogService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * M11-2-5 角色列表，附用户数与权限点数，便于判断角色是否在用。
     */
    public List<RoleVO> list() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId));
        Map<Long, Long> permCounts = rolePermissionMapper.selectList(new QueryWrapper<SysRolePermission>())
                .stream().collect(Collectors.groupingBy(SysRolePermission::getRoleId, Collectors.counting()));
        return roles.stream().map(role -> {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setRoleCode(role.getRoleCode());
            vo.setRoleName(role.getRoleName());
            vo.setDataScopeAll(role.getDataScopeAll());
            vo.setIsSystem(role.getIsSystem());
            vo.setStatus(role.getStatus());
            vo.setRemark(role.getRemark());
            vo.setUserCount(userRoleMapper.countByRoleId(role.getId()));
            vo.setPermCount(permCounts.getOrDefault(role.getId(), 0L).intValue());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * M11-2-5 新增角色。编码唯一，数据范围全量只允许内置系统管理员持有。
     */
    @Transactional
    public Long create(RoleSaveRequest request) {
        long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, request.getRoleCode()));
        if (exists > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "角色编码已存在：" + request.getRoleCode());
        }
        if (request.getDataScopeAll() != null && request.getDataScopeAll() == 1) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "数据范围全量仅内置系统管理员持有，新角色请用维度授权");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDataScopeAll(0);
        role.setIsSystem(0);
        role.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        role.setRemark(request.getRemark());
        roleMapper.insert(role);
        operationLogService.record("M11", "create", "sys_role", role.getId(), role.getRoleCode(),
                "新增角色 " + role.getRoleName());
        return role.getId();
    }

    /**
     * M11-2-5 编辑角色。内置角色编码不可改，避免权限点与代码中的角色编码失配。
     */
    @Transactional
    public void update(RoleSaveRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "编辑时角色id不能为空");
        }
        SysRole exists = requireRole(request.getId());
        if (exists.getIsSystem() != null && exists.getIsSystem() == 1
                && !Objects.equals(exists.getRoleCode(), request.getRoleCode())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "内置角色编码不可修改");
        }
        if (exists.getIsSystem() != null && exists.getIsSystem() == 0) {
            long duplicate = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, request.getRoleCode())
                    .ne(SysRole::getId, exists.getId()));
            if (duplicate > 0) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "角色编码已存在：" + request.getRoleCode());
            }
        }
        Integer targetStatus = request.getStatus() == null ? exists.getStatus() : request.getStatus();
        if (targetStatus != null && targetStatus != STATUS_ENABLED
                && userMapper.countActiveUsersByRole(exists.getId()) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "该角色下仍有启用用户，请先调整用户角色");
        }

        String before = exists.getRoleName() + " 状态=" + exists.getStatus();
        SysRole update = new SysRole();
        update.setId(exists.getId());
        update.setRoleCode(exists.getIsSystem() != null && exists.getIsSystem() == 1
                ? exists.getRoleCode() : request.getRoleCode());
        update.setRoleName(request.getRoleName());
        update.setStatus(targetStatus);
        update.setRemark(request.getRemark());
        roleMapper.updateById(update);
        operationLogService.record("M11", "update", "sys_role", exists.getId(), exists.getRoleCode(),
                "修改前 " + before + "；修改后 " + request.getRoleName() + " 状态=" + targetStatus);
    }

    /**
     * M11-3-2 角色授权。整体重建，物理删除后插入。
     */
    @Transactional
    public void grantPermissions(Long roleId, List<Long> permIds) {
        SysRole role = requireRole(roleId);
        List<Long> normalized = permIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (normalized.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "至少勾选一个权限点");
        }
        List<SysPermission> permissions = permissionMapper.selectByIds(normalized);
        if (permissions.size() != normalized.size()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "存在无效权限点");
        }
        boolean writableAi = permissions.stream().anyMatch(p -> p.getModuleCode() != null
                && p.getModuleCode().startsWith("M") && isAiModule(p.getModuleCode())
                && !READ_ONLY_ACTIONS.contains(p.getActionType()));
        if (writableAi) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "智能模块权限点只允许 list、view、query、audit 四类只读动作");
        }

        List<String> before = rolePermissionMapper.selectPermIds(roleId).stream()
                .map(String::valueOf).collect(Collectors.toList());
        rolePermissionMapper.deletePhysicallyByRoleId(roleId);
        for (Long permId : normalized) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermId(permId);
            relation.setIsDeleted(0);
            relation.setVersion(0);
            rolePermissionMapper.insert(relation);
        }
        List<String> after = normalized.stream().map(String::valueOf).collect(Collectors.toList());
        operationLogService.record("M11", "grant", "sys_role", roleId, role.getRoleCode(),
                "授权变更 " + role.getRoleName() + "：权限点 " + before + " -> " + after);
    }

    /**
     * 查询角色已持有的权限点id，供授权页回填勾选状态。
     */
    public List<Long> permissionIdsOf(Long roleId) {
        requireRole(roleId);
        return rolePermissionMapper.selectPermIds(roleId);
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private boolean isAiModule(String moduleCode) {
        return Stream.of("M7", "M8", "M9").anyMatch(moduleCode::equals);
    }

    /** 智能模块允许的动作，与权限点表的约定一致 */
    private static final Set<String> READ_ONLY_ACTIONS = Set.of("list", "view", "query", "audit");
}
