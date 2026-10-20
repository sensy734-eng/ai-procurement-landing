package com.zufe.procurement.auth;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录与当前用户返回体。前端据此渲染菜单、按钮与数据范围提示。
 */
@Data
public class LoginVO {

    private String token;

    private LocalDateTime expiresAt;

    private UserVO user;

    private List<String> roles;

    private List<String> roleNames;

    private List<String> permissions;

    private ScopeVO dataScope;

    private boolean pwdNeedChange;

    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String realName;
        private Long deptId;
        private String deptName;
    }

    /**
     * 数据范围摘要，只用于界面提示，不参与后端过滤。
     * 后端过滤以 DataScopeService 解析结果为准，防止前端篡改。
     */
    @Data
    public static class ScopeVO {
        private boolean all;
        private List<Long> deptIds;
        private List<Long> warehouseIds;
        private List<String> ownerNos;
    }
}
