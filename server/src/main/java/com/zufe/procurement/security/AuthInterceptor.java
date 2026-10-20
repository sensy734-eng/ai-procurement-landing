package com.zufe.procurement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.AuthService;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 认证与动作级权限拦截器。
 * 先认证（401），再校验 @RequirePermission（403），最后放行。
 * 未登录与越权均不进入业务逻辑，避免半途抛错造成脏数据。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或会话已过期");
            return false;
        }
        CurrentUser currentUser;
        try {
            currentUser = authService.loadCurrentUser(token);
        } catch (BizException e) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return false;
        }
        AuthContext.set(currentUser);

        if (handler instanceof HandlerMethod handlerMethod) {
            RequirePermission required = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), RequirePermission.class);
            if (required == null) {
                required = AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), RequirePermission.class);
            }
            if (required != null) {
                boolean allowed = Arrays.stream(required.value()).allMatch(currentUser::hasPermission);
                if (!allowed) {
                    AuthContext.clear();
                    write(response, HttpServletResponse.SC_FORBIDDEN,
                            "无权限：" + String.join(",", required.value()));
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        String headerToken = request.getHeader("X-Token");
        return headerToken == null || headerToken.isBlank() ? null : headerToken.trim();
    }

    private void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.fail(ResultCode.NO_PERMISSION, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
