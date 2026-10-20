package com.zufe.procurement.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求，M11-1-4。
 * 最小长度由参数 pwd_min_length 决定，在 Service 层校验，避免前端与后端两套口径。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(max = 30, message = "新密码长度不超过 30 位")
    private String newPassword;
}
