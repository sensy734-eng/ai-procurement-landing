package com.zufe.procurement.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求，M11-1-1。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
