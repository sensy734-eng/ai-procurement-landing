package com.zufe.procurement.auth;

import com.zufe.procurement.scope.ScopeCondition;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * 当前登录用户上下文，含权限点与数据范围，由认证拦截器放置到线程变量。
 */
@Data
public class CurrentUser {

    private Long userId;
    private String username;
    private String realName;
    private Long deptId;
    private String deptName;

    private Set<String> roles = new HashSet<>();
    private Set<String> roleNames = new HashSet<>();
    private Set<String> permissions = new HashSet<>();

    /** 数据范围，页面查询与智能查询共用同一份条件 M11-3-3 */
    private ScopeCondition scope = new ScopeCondition();

    private String token;

    public boolean hasPermission(String permCode) {
        return permissions.contains(permCode);
    }
}
