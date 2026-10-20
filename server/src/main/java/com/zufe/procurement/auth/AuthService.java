package com.zufe.procurement.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m11.system.entity.SysRole;
import com.zufe.procurement.m11.system.entity.SysSession;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysSessionMapper;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证与会话服务。
 * 覆盖功能点：M11-1-1 登录建立会话、M11-1-2 会话保持与超时、M11-1-3 退出、
 * M11-1-4 修改密码；M11-3 权限点与数据范围装载。
 */
@Service
public class AuthService {

    private static final int SESSION_ACTIVE = 1;
    private static final int SESSION_LOGOUT = 2;
    private static final int SESSION_EXPIRED = 3;

    private static final int LOGOUT_MANUAL = 1;
    private static final int LOGOUT_TIMEOUT = 2;
    private static final int LOGOUT_KICKED = 3;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysUserMapper userMapper;
    private final SysSessionMapper sessionMapper;
    private final DictItemMapper dictItemMapper;
    private final DataScopeService dataScopeService;

    /** 只用 BCrypt 校验，不引入 Spring Security 过滤器链 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.session.timeout-minutes:480}")
    private long timeoutMinutes;

    public AuthService(SysUserMapper userMapper,
                       SysSessionMapper sessionMapper,
                       DictItemMapper dictItemMapper,
                       DataScopeService dataScopeService) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.dictItemMapper = dictItemMapper;
        this.dataScopeService = dataScopeService;
    }

    /**
     * M11-1-1 登录：校验账号密码，建立会话，返回权限点与数据范围。
     */
    @Transactional
    public LoginVO login(LoginRequest request, String ip, String userAgent) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.NO_PERMISSION, "账号已停用，请联系管理员");
        }

        LocalDateTime now = LocalDateTime.now();
        SysSession session = new SysSession();
        session.setSessionToken(newToken());
        session.setUserId(user.getId());
        session.setLoginIp(ip);
        session.setUserAgent(trim(userAgent, 300));
        session.setLoginAt(now);
        session.setLastActiveAt(now);
        session.setExpireAt(now.plusMinutes(timeoutMinutes));
        session.setStatus(SESSION_ACTIVE);
        sessionMapper.insert(session);

        SysUser loginUpdate = new SysUser();
        loginUpdate.setId(user.getId());
        loginUpdate.setLastLoginTime(now);
        loginUpdate.setLastLoginIp(ip);
        userMapper.updateById(loginUpdate);

        CurrentUser currentUser = buildCurrentUser(user, session.getSessionToken());
        LoginVO vo = toLoginVO(currentUser);
        vo.setToken(session.getSessionToken());
        vo.setExpiresAt(session.getExpireAt());
        vo.setPwdNeedChange(user.getPwdNeedChange() != null && user.getPwdNeedChange() == 1);
        return vo;
    }

    /**
     * M11-1-2 会话保持：校验有效期并顺延，超时置为已过期。
     */
    public CurrentUser loadCurrentUser(String token) {
        SysSession session = sessionMapper.selectOne(new LambdaQueryWrapper<SysSession>()
                .eq(SysSession::getSessionToken, token));
        if (session == null) {
            throw new BizException(ResultCode.NO_PERMISSION, "未登录或会话不存在");
        }
        if (session.getStatus() == null || session.getStatus() != SESSION_ACTIVE) {
            throw new BizException(ResultCode.NO_PERMISSION, "会话已失效，请重新登录");
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getExpireAt() == null || session.getExpireAt().isBefore(now)) {
            markExpired(session.getId(), now);
            throw new BizException(ResultCode.NO_PERMISSION, "会话已超时，请重新登录");
        }

        SysUser user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.NO_PERMISSION, "账号不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.NO_PERMISSION, "账号已停用，请联系管理员");
        }

        renewIfNeeded(session, now);
        return buildCurrentUser(user, token);
    }

    public LoginVO currentUserView(CurrentUser currentUser) {
        LoginVO vo = toLoginVO(currentUser);
        vo.setToken(currentUser.getToken());
        return vo;
    }

    /**
     * M11-1-3 退出：会话置为已退出，保留记录可查。
     */
    public void logout(String token) {
        sessionMapper.update(null, new LambdaUpdateWrapper<SysSession>()
                .eq(SysSession::getSessionToken, token)
                .eq(SysSession::getStatus, SESSION_ACTIVE)
                .set(SysSession::getStatus, SESSION_LOGOUT)
                .set(SysSession::getLogoutAt, LocalDateTime.now())
                .set(SysSession::getLogoutType, LOGOUT_MANUAL));
    }

    /**
     * M11-1-4 修改密码：校验原密码，改后其它会话强制下线。
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String currentToken) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "新密码不能与原密码相同");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        update.setPwdNeedChange(0);
        update.setPwdUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);

        sessionMapper.update(null, new LambdaUpdateWrapper<SysSession>()
                .eq(SysSession::getUserId, userId)
                .eq(SysSession::getStatus, SESSION_ACTIVE)
                .ne(SysSession::getSessionToken, currentToken)
                .set(SysSession::getStatus, SESSION_LOGOUT)
                .set(SysSession::getLogoutAt, LocalDateTime.now())
                .set(SysSession::getLogoutType, LOGOUT_KICKED));
    }

    private CurrentUser buildCurrentUser(SysUser user, String token) {
        List<SysRole> roles = userMapper.selectRoles(user.getId());
        List<Long> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toList());
        boolean scopeAll = roles.stream()
                .anyMatch(r -> r.getDataScopeAll() != null && r.getDataScopeAll() == 1);
        List<String> permCodes = userMapper.selectPermCodes(user.getId());
        ScopeCondition scope = dataScopeService.resolve(user.getId(), roleIds, scopeAll);

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(user.getId());
        currentUser.setUsername(user.getUsername());
        currentUser.setRealName(user.getRealName());
        currentUser.setDeptId(user.getDeptId());
        DictItem dept = user.getDeptId() == null ? null : dictItemMapper.selectById(user.getDeptId());
        currentUser.setDeptName(dept == null ? null : dept.getItemName());
        currentUser.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet()));
        currentUser.setRoleNames(roles.stream().map(SysRole::getRoleName).collect(Collectors.toSet()));
        currentUser.setPermissions(permCodes.stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
        currentUser.setScope(scope);
        currentUser.setToken(token);
        return currentUser;
    }

    private LoginVO toLoginVO(CurrentUser currentUser) {
        LoginVO.UserVO userVO = new LoginVO.UserVO();
        userVO.setId(currentUser.getUserId());
        userVO.setUsername(currentUser.getUsername());
        userVO.setRealName(currentUser.getRealName());
        userVO.setDeptId(currentUser.getDeptId());
        userVO.setDeptName(currentUser.getDeptName());

        ScopeCondition scope = currentUser.getScope();
        LoginVO.ScopeVO scopeVO = new LoginVO.ScopeVO();
        scopeVO.setAll(scope.isAll());
        scopeVO.setDeptIds(List.copyOf(scope.getDeptIds()));
        scopeVO.setWarehouseIds(List.copyOf(scope.getWarehouseIds()));
        scopeVO.setOwnerNos(List.copyOf(scope.getOwnerNos()));

        LoginVO vo = new LoginVO();
        vo.setUser(userVO);
        vo.setRoles(List.copyOf(currentUser.getRoles()));
        vo.setRoleNames(List.copyOf(currentUser.getRoleNames()));
        vo.setPermissions(List.copyOf(currentUser.getPermissions()));
        vo.setDataScope(scopeVO);
        return vo;
    }

    /**
     * 滑动续期：剩余不足一半有效期时顺延，避免每次请求都写会话表。
     */
    private void renewIfNeeded(SysSession session, LocalDateTime now) {
        boolean needRenew = session.getExpireAt() == null
                || session.getExpireAt().isBefore(now.plusMinutes(Math.max(1, timeoutMinutes / 2)));
        LambdaUpdateWrapper<SysSession> update = new LambdaUpdateWrapper<SysSession>()
                .eq(SysSession::getId, session.getId())
                .set(SysSession::getLastActiveAt, now);
        if (needRenew) {
            update.set(SysSession::getExpireAt, now.plusMinutes(timeoutMinutes));
        }
        sessionMapper.update(null, update);
    }

    private void markExpired(Long sessionId, LocalDateTime now) {
        sessionMapper.update(null, new LambdaUpdateWrapper<SysSession>()
                .eq(SysSession::getId, sessionId)
                .set(SysSession::getStatus, SESSION_EXPIRED)
                .set(SysSession::getLogoutAt, now)
                .set(SysSession::getLogoutType, LOGOUT_TIMEOUT));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 供后续模块在会话失效时清理使用 */
    public Set<String> permissionsOf(Long userId) {
        return userMapper.selectPermCodes(userId).stream().collect(Collectors.toSet());
    }
}
