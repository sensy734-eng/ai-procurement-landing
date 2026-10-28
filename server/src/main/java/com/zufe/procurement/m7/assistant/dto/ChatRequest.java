package com.zufe.procurement.m7.assistant.dto;

import lombok.Data;

import java.util.Map;

/**
 * 提问请求（M7-1-1），同时承载参数修正后的重跑（M7-3-2、M7-3-4）。
 *
 * overrideParams 只在用户于意图卡片上手工改过参数时出现；
 * 覆盖值仍要走白名单校验，不因为是人工输入就跳过（M9-2-4）。
 */
@Data
public class ChatRequest {

    private Long sessionId;
    private String question;
    /** 澄清选项里点选的问题方向，1 知识、2 教程、3 数据（M7-2-2） */
    private Integer overrideRoute;
    /** 人工指定的意图编码，为空则由问句分类 */
    private String overrideIntentCode;
    /** 人工修正的参数，键为 param_code */
    private Map<String, Object> overrideParams;
    /** 澄清选项里用户点选的实体 id（M9-3-3） */
    private Long confirmedEntityId;
}
