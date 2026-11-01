package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 知识与教程问答结果（M8 返回给 M7 的契约）。
 *
 * 回答一律基于检索片段组织（M8-1-4、M8-2-2），refs 非空是有效回答的必要条件（M8-3-1）；
 * refusalReason 非空表示依据不足，此时 refs 为空且 resultCode 为 NoData（M8-5-1）。
 */
@Data
public class KnowledgeAnswer {

    private String answer;
    private List<ChatVO.Citation> refs;
    private List<ChatVO.DualPathItem> dualPath;
    private String refusalReason;
    private ChatVO.VersionConflict versionConflict;
    private String resultCode;
    /** 向量路最高余弦，拒答判定的标尺（M8-5-1） */
    private BigDecimal topScore;
    /** 教程类答案的步骤数，用于断言后端确实做了步骤规整（M8-2-3） */
    private Integer stepCount;
    private List<String> notices;
    /**
     * 答案由谁产出：llm 大模型按片段组织、template 模板摘录、
     * template-guard 生成后被数字/引用校验拦下回退模板、refuse 直接拒答。
     */
    private String generator;
}
