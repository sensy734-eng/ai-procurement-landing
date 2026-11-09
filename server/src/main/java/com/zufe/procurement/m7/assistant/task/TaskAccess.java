package com.zufe.procurement.m7.assistant.task;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.AuthService;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m7.assistant.service.AccessSnapshot;
import org.springframework.stereotype.Component;

@Component
public class TaskAccess {
    private final AuthService auth;
    public TaskAccess(AuthService auth) { this.auth = auth; }

    public void require(String... permissions) {
        var user = AuthContext.getOrThrow();
        for (String permission : permissions) {
            if (!user.hasPermission(permission)) throw new BizException(ResultCode.NO_PERMISSION, "缺少操作权限：" + permission);
        }
    }

    public String fingerprint() { return AccessSnapshot.fingerprint(AuthContext.getOrThrow()); }

    /** Re-read session permissions after a potentially slow model call; revoked access cannot return stale evidence. */
    public void ensureUnchanged(String expected) {
        var current = AuthContext.getOrThrow();
        if (current.getToken() != null) {
            var fresh = auth.loadCurrentUser(current.getToken());
            if (fresh == null || !expected.equals(AccessSnapshot.fingerprint(fresh))) {
                throw new BizException(ResultCode.NO_PERMISSION, "权限或会话已变化，请重新登录后重试");
            }
        }
        if (!expected.equals(fingerprint())) throw new BizException(ResultCode.NO_PERMISSION, "权限已变化，请重新生成结果");
    }

    public static String text(String value, int limit, String label) {
        if (value == null || value.isBlank() || value.length() > limit)
            throw new BizException(ResultCode.INVALID_PARAMETER, label + "不能为空且不能超过" + limit + "字");
        return value.strip();
    }
}
