package com.zufe.procurement.m11.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能查询审计，对应 ai_query_audit，功能点 M11-4-5、M11-4-6。
 * 每次智能请求写一行，含原始问句、解析参数、被拒参数、注入的数据范围与结果码。
 * 被拒参数必须留痕，这是受控执行边界第五条的可查证据。
 */
@Data
@TableName("ai_query_audit")
public class AiQueryAudit {

    @TableId(type = IdType.AUTO)
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

    private Integer isDeleted;

    private LocalDateTime createdAt;
}
