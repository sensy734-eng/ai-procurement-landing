package com.zufe.procurement.llm;

/**
 * 从模型输出里抠出 JSON 对象。
 *
 * 现实情况是：即使要求在提示里「只输出 JSON」，模型仍会加 ```json 围栏、
 * 加一句「好的，结果如下」，或在末尾补解释。这里做的是容错而不是宽松：
 * 只接受**第一个完整的花括号块**，且必须能被解析，否则调用方按失败回退。
 * 不做「猜字段」「补括号」这类补救——那会让判分结果不可信。
 */
public final class LlmJson {

    private LlmJson() {
    }

    /** 抽取第一个平衡的 JSON 对象，失败返回 null */
    public static String extractObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
