package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史消息（M7-1-2）。
 * 助手行的 resultJson 原样回放，前端据此重建结果表格与引用块，
 * 保证刷新页面后看到的与当时一致（不重新执行查询）。
 */
@Data
public class ChatMessageVO {

    private Long id;
    /** 1 用户、2 助手 */
    private Integer role;
    private String content;
    private Integer msgType;
    private String intentCode;
    private String intentName;
    private String paramsJson;
    private String resultJson;
    private String refDocIds;
    private String resultCode;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
}
