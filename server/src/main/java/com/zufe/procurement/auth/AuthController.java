package com.zufe.procurement.auth;

import com.zufe.procurement.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 * M11-1-1 登录、M11-1-3 退出、M11-1-4 修改密码，以及当前用户信息（权限点与数据范围）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        return Result.ok(authService.login(request, ip, userAgent));
    }

    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.ok(authService.currentUserView(AuthContext.getOrThrow()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(AuthContext.getOrThrow().getToken());
        return Result.ok();
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        CurrentUser currentUser = AuthContext.getOrThrow();
        authService.changePassword(currentUser.getUserId(), request.getOldPassword(),
                request.getNewPassword(), currentUser.getToken());
        return Result.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
