package com.zufe.procurement.m11.system.vo;

import lombok.Data;

/**
 * 重置密码结果，功能点 M11-2-4。
 * 初始密码只在本次响应返回，不落库、不进日志，由管理员当面告知用户，
 * 用户首次登录后系统要求立即修改。
 */
@Data
public class ResetPwdVO {

    private Long userId;
    private String username;
    private String initialPassword;
}
