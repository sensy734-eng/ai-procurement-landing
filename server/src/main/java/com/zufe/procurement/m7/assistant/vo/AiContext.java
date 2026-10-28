package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 多轮上下文（M7-1-3）：上一轮意图、实体与参数。
 *
 * 只用于指代消解（「它的供应商是谁」），不直接当作可信查询条件；
 * 继承来的参数与问句提取参数一样要过白名单校验（M9-2-4）。
 */
@Data
public class AiContext {

    private String intentCode;
    private List<ChatVO.EntityRef> entities;
    private Map<String, Object> params;
    /** 上一轮结果的单据号，供「这张单」类指代 */
    private String lastBillNo;
}
