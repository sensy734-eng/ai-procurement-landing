package com.zufe.procurement.m7.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息（M7-1-1、M7-4），对应 ai_message。
 *
 * 一条用户问句与一条助手回答各存一行；
 * 结构化结果与引用文档留在助手行，便于复盘与论文取证（M7-4-1、M7-4-2）。
 *
 * msg_type：1 知识类、2 教程类、3 数据类、4 澄清、5 拒答、6 纯文本。
 */
@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    /** 1 用户、2 助手 */
    private Integer role;
    private String content;
    private Integer msgType;
    private String intentCode;
    private String paramsJson;
    private String resultJson;
    /** 引用文档 id，逗号分隔 */
    private String refDocIds;
    private String resultCode;
    private Integer elapsedMs;
    private Integer isDeleted;
    private LocalDateTime createdAt;
}
