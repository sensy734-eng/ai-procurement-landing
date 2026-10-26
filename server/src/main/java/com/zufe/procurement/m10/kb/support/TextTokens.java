package com.zufe.procurement.m10.kb.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文字符二元组分词（M10-3-2 索引与检索端共用同一套分词，保证建索引与查询一致）。
 *
 * 规则：
 * - CJK 字符连续段切二元组（单字段落单字），如「采购申请」→ 采购、购申、申请；
 * - ASCII 字母数字连续段整段小装作一个词元，如 ERP 操作 → erp、操作；
 * - 其余字符（空白与标点）为分隔符。
 */
public final class TextTokens {

    private TextTokens() {
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        StringBuilder cjk = new StringBuilder();
        StringBuilder alnum = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                flushAlnum(alnum, tokens);
                cjk.append(c);
            } else if (isAlnum(c)) {
                flushCjk(cjk, tokens);
                alnum.append(c);
            } else {
                flushAlnum(alnum, tokens);
                flushCjk(cjk, tokens);
            }
        }
        flushAlnum(alnum, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static void flushCjk(StringBuilder cjk, List<String> tokens) {
        if (cjk.length() == 0) {
            return;
        }
        String run = cjk.toString();
        if (run.length() == 1) {
            tokens.add(run);
        } else {
            for (int i = 0; i < run.length() - 1; i++) {
                tokens.add(run.substring(i, i + 2));
            }
        }
        cjk.setLength(0);
    }

    private static void flushAlnum(StringBuilder alnum, List<String> tokens) {
        if (alnum.length() > 0) {
            tokens.add(alnum.toString().toLowerCase());
            alnum.setLength(0);
        }
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static boolean isAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
