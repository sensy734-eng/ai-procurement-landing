package com.zufe.procurement.m9.query.support;

import com.zufe.procurement.m10.kb.support.TextTokens;

import java.util.HashSet;
import java.util.Set;

/**
 * 问句相似度（M9-1-2 意图置信度、M7-2-2 路由置信度）。
 *
 * 口径：字符二元组集合的 Dice 系数 2|A∩B| / (|A|+|B|)，
 * 取值 0~1，与问句长度无关，可直接与 sys_param 的置信度阈值比较。
 * 复用 M10 的同一套分词，保证「问句与示例问句」「问句与切块」两种比较口径一致。
 */
public final class QuestionSimilarity {

    private QuestionSimilarity() {
    }

    public static double dice(String left, String right) {
        Set<String> a = bigrams(left);
        Set<String> b = bigrams(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String token : a) {
            if (b.contains(token)) {
                hit++;
            }
        }
        return 2.0 * hit / (a.size() + b.size());
    }

    /** 归一：去空白与标点，ASCII 转小写，避免全角空格影响分词 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static Set<String> bigrams(String text) {
        return new HashSet<>(TextTokens.tokenize(normalize(text)));
    }

    private static boolean isPunctuation(char c) {
        if (c >= 0x3000 && c <= 0x303F) {
            return true;
        }
        if (c >= 0xFF00 && c <= 0xFF65) {
            return true;
        }
        return "，。！？；：、,.!?;:\"'()（）《》【】".indexOf(c) >= 0;
    }
}
