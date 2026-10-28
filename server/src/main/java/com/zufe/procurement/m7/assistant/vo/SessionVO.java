package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 会话列表项（M7-1-2） */
@Data
public class SessionVO {

    private Long id;
    private String title;
    private Integer messageCount;
    private String lastIntentCode;
    private String lastIntentName;
    private LocalDateTime lastTime;
    private LocalDateTime createdAt;
}
