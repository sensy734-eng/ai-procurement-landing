package com.zufe.procurement.m11.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志展示对象，功能点 M11-4-4。
 */
@Data
public class OperationLogVO {

    private Long id;
    private String username;
    private String realName;
    private String moduleCode;
    private String action;
    private String targetType;
    private Long targetId;
    private String targetNo;
    private String detail;
    private String resultCode;
    private String ip;
    private LocalDateTime createdAt;
}
