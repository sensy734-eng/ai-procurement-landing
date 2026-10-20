package com.zufe.procurement.m11.audit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能查询审计展示对象，功能点 M11-4-5、M11-4-6。
 * rejectedParamsJson 与 dataScopeJson 一并返回，用于证明被拒参数留痕、范围过滤已注入。
 */
@Data
public class AiQueryAuditVO {

    private Long id;
    private Long sessionId;
    private Long userId;
    private String username;
    private String question;
    private String intentCode;
    private String paramsJson;
    private String rejectedParamsJson;
    private String dataScopeJson;
    private String resultCode;
    private Integer rowCount;
    private Integer elapsedMs;
    private String ip;
    private LocalDateTime createdAt;
}
