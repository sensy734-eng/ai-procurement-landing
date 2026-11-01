package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 受控查询结果（M9 返回给 M7 的契约）。
 *
 * 由 M9 填充、M7 原样放进 ChatVO，因此字段与 ChatVO 的内部结构同源，
 * 不再做一次逐字段搬运，避免两处结构漂移。
 */
@Data
public class DataAnswer {

    private String intentCode;
    private String intentName;
    private String serviceCode;
    private BigDecimal confidence;

    private List<ChatVO.EntityRef> entities;
    private ChatVO.TimeRange timeRange;
    private List<ChatVO.ParamItem> filters;
    private List<ChatVO.ParamItem> missing;
    private List<ChatVO.ParamItem> rejected;

    private ChatVO.ResultTable table;
    private String summary;
    private Integer rowCount;

    /** 非 Success 时给前端展示的澄清入口 */
    private String clarifyType;
    private String clarifyQuestion;
    private List<ChatVO.Option> clarifyOptions;

    private String resultCode;
    private List<String> notices = new ArrayList<>();
    /** 本次注入的数据范围条件，写进审计（M9-4-2 可验证凭据） */
    private Map<String, Object> scopeSnapshot;
    /**
     * 结果由谁产出：llm 大模型判定、rule 词表规则。
     * 评测时据它核对「模型是否真的接管」，避免全部静默回退却看不出来。
     */
    private String generator;
}
