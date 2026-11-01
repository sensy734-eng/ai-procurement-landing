package com.zufe.procurement.m8.qa.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成后校验（M8-3-3 的生成侧延伸）：模型说了片段里没有的数字，就不许出口。
 *
 * 为什么非要这一层：接入大模型后最典型的失真不是「答非所问」，而是
 * 「把 90 字的条款扩写成 300 字，多出来的数字看着很像真的」。
 * 事实点命中率对这种答案毫无办法——关键词都在，编的数字也在。
 *
 * 只查两个可机械判定的维度：
 * 1. **数字一致性**：回答里每个数字（先剥掉「1. 2.」这类步骤序号）都必须在片段原文里出现过；
 * 2. **敏感串**：表名、字段名、SQL 关键字、提示词片段一律不许出现在面向用户的回答里。
 *
 * 不查「语义是否等价」——那需要人或第二个模型，属于抽检范围，不假装能自动判定。
 */
public final class AnswerFidelityGuard {

    /** 步骤序号：行首的 1. / 2、/（3） 不参与数字校验 */
    private static final Pattern STEP_INDEX = Pattern.compile("(?m)^\\s*(?:[0-9]{1,2}[.、)]|[（(][0-9]{1,2}[)）])\\s*");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    private static final List<String> SENSITIVE = List.of(
            "password_hash", "sys_user", "sys_role", "ai_query_audit", "kb_chunk",
            "select ", "insert ", "update ", "delete ", "drop table",
            "系统提示词", "system prompt", "sk-");

    private AnswerFidelityGuard() {
    }

    /**
     * @param answer  模型生成的回答
     * @param context 送进模型的所有片段原文拼接
     * @return 违规原因列表，空表示通过
     */
    public static List<String> check(String answer, String context) {
        List<String> violations = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return violations;
        }
        String lower = answer.toLowerCase();
        for (String token : SENSITIVE) {
            if (lower.contains(token)) {
                violations.add("回答中出现敏感串「" + token.trim() + "」");
            }
        }
        String body = STEP_INDEX.matcher(answer).replaceAll("");
        String flatAnswer = body.replaceAll("\\s+", "");
        String flatContext = (context == null ? "" : context).replaceAll("\\s+", "");
        Set<String> invented = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(flatAnswer);
        while (matcher.find()) {
            String number = matcher.group();
            if (!flatContext.contains(number)) {
                invented.add(number);
            }
        }
        if (!invented.isEmpty()) {
            violations.add("回答中出现片段里没有的数字：" + String.join("、", invented));
        }
        return violations;
    }
}
