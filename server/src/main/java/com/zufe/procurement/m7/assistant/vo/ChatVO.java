package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 一次问答的完整结果（M7-4、M8-3、M9-5）。
 *
 * 七类结果码放在外层 Result.code 上，本对象始终返回，便于前端把
 * 澄清选项、拒答理由、被拒参数一并展示，而不是只弹一句错误。
 *
 * 字段与功能点对应：
 * - route / confidence：M7-2-1 三类问题分类、M9-1-2 意图置信度
 * - card：M7-3-1 解析结果展示（意图、实体、时间范围、筛选条件）
 * - table / answer：M7-4-1 表格化结果与自然语言说明（M9-5-3 只转述不改写）
 * - refs：M7-4-2、M8-3-1 来源引用
 * - clarify：M7-2-2 置信度不足澄清、M9-2-5 参数缺失澄清、M9-3-2 多候选消歧
 * - refusalReason：M8-5-1/2 依据不足拒答
 * - versionConflict：M8-4-2 多版本并存提示
 * - dualPath：M8-1-3 双路结果并列，供对照实验
 * - compare：M7-3-4 重跑与结果对比
 */
@Data
public class ChatVO {

    private Long sessionId;
    private Long questionMessageId;
    private Long answerMessageId;

    /** 1 知识类、2 教程类、3 数据类 */
    private Integer route;
    private String routeName;

    private String intentCode;
    private String intentName;
    private BigDecimal confidence;

    private IntentCard card;
    /** 本轮识别到的实体，多轮对话时作为下一轮的指代来源（M7-1-3） */
    private List<EntityRef> entities;
    private String answer;
    private ResultTable table;
    private List<Citation> refs;
    private Clarify clarify;
    private String refusalReason;
    private VersionConflict versionConflict;
    private List<DualPathItem> dualPath;

    private String resultCode;
    /** M7-4-3 按七种结果码给出差异化提示 */
    private String resultCodeHint;
    private Integer elapsedMs;
    private RerunCompare compare;
    private List<String> notices;
    /**
     * 本条回答的产出方：llm / rule / template / template-guard / refuse。
     * 评测脚本据它统计「模型接管率」——分数高但全走模板，说明模型根本没生效。
     */
    private String generator;
    /** Knowledge answer mode; absent on historical messages and data queries. */
    private String answerMode;
    private Integer answerTokens;
    private Long embeddingTokens;
    private String degradedReason;
    private String retrievalConfigId;
    private List<Long> evidenceChunkIds;
    /** 调用模型时使用的模型名，便于在报告里注明版本 */
    private String llmModel;

    // ---------------- 意图与参数核对卡片 M7-3-1 ----------------

    @Data
    public static class IntentCard {
        private String intentCode;
        private String intentName;
        private String serviceCode;
        private List<EntityRef> entities;
        private TimeRange timeRange;
        private List<ParamItem> filters;
        private List<ParamItem> missing;
        private List<ParamItem> rejected;
        /** 当前用户是否持有 m7:query:retry，决定前端是否允许改参数重跑 */
        private Boolean editable;
    }

    @Data
    public static class EntityRef {
        /** 1 物料、2 供应商、3 仓库、4 订单、5 申请单 */
        private Integer type;
        private String typeName;
        private Long id;
        private String no;
        private String name;
        /** 归一来源：alias 别名、name 名称、no 编号、context 上文指代 */
        private String matchedBy;
    }

    @Data
    public static class TimeRange {
        private String from;
        private String to;
        /** 原始表述，如「近 30 天」「上个月」 */
        private String label;
    }

    @Data
    public static class ParamItem {
        private String code;
        private String name;
        private String op;
        private Object value;
        private String dataType;
        private List<String> enumValues;
        private Boolean editable;
        /** 参数来源：question 问句提取、override 人工修正、context 上文继承 */
        private String source;
        /** 被拒参数的原因（M9-2-4），仅 rejected 列表使用 */
        private String reason;
    }

    // ---------------- 结构化结果 M7-4-1 ----------------

    @Data
    public static class ResultTable {
        private List<Column> columns;
        private List<Map<String, Object>> rows;
        private long total;
        /** 结果口径说明，如「可用量 = 数量 − 冻结 − 预留」 */
        private String caption;
    }

    @Data
    public static class Column {
        private String key;
        private String label;
        private String type;
    }

    // ---------------- 来源引用 M8-3 ----------------

    @Data
    public static class Citation {
        private Long docId;
        private String docName;
        private String docVersion;
        private String effectiveDate;
        private String sectionNo;
        private String sectionTitle;
        private Long chunkId;
        private String snippet;
        private BigDecimal score;
        /** sparse 稀疏路、vector 向量路 */
        private String path;
        /** M8-3-3 引用有效性校验结果 */
        private Boolean valid;
        private String invalidReason;
    }

    @Data
    public static class DualPathItem {
        private String path;
        private Integer rank;
        private Long chunkId;
        private Long docId;
        private String docName;
        private String sectionNo;
        private BigDecimal score;
        private String snippet;
    }

    // ---------------- 澄清与拒答 ----------------

    @Data
    public static class Clarify {
        /** route 三类路由、intent 意图、entity 实体消歧、param 必填参数 */
        private String type;
        private String question;
        private List<Option> options;
    }

    @Data
    public static class Option {
        private String label;
        private String detail;
        /** 三类路由选项使用：1 知识、2 教程、3 数据 */
        private Integer route;
        private String intentCode;
        private Map<String, Object> params;
    }

    @Data
    public static class VersionConflict {
        private String docName;
        /** 本次采用的版本 */
        private String usedVersion;
        private String usedEffectiveDate;
        private List<String> otherVersions;
        private String message;
    }

    @Data
    public static class RerunCompare {
        private String previousResultCode;
        private String previousSummary;
        private Integer previousRowCount;
        private List<String> changedParams;
    }
}
