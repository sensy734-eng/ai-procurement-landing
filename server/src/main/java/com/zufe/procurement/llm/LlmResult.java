package com.zufe.procurement.llm;

import lombok.Data;

/**
 * 一次模型调用的结果。
 *
 * 刻意不抛异常：调用点全部是「模型不可用就退回规则式」的语义，
 * 抛异常会让每个调用点都包一层 try-catch，反而容易漏掉回退分支。
 * 失败原因放在 errorKind 里，健康检查与报告直接读它。
 */
@Data
public class LlmResult {

    /** DISABLED / NO_KEY / AUTH / RATE_LIMIT / SERVER / TIMEOUT / NETWORK / EMPTY / BAD_JSON */
    private String errorKind;
    private String text;
    private int latencyMs;
    private String model;
    private int promptChars;
    private Integer totalTokens;

    public boolean isOk() {
        return errorKind == null && text != null && !text.isBlank();
    }

    public static LlmResult ok(String text, int latencyMs, String model, int promptChars, Integer tokens) {
        LlmResult result = new LlmResult();
        result.text = text;
        result.latencyMs = latencyMs;
        result.model = model;
        result.promptChars = promptChars;
        result.totalTokens = tokens;
        return result;
    }

    public static LlmResult fail(String kind, int latencyMs, String model) {
        LlmResult result = new LlmResult();
        result.errorKind = kind;
        result.latencyMs = latencyMs;
        result.model = model;
        return result;
    }
}
