package com.zufe.procurement.m8.qa.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mechanical checks for numeric claims and internal data leakage in generated answers. */
public final class AnswerFidelityGuard {

    private static final Pattern STEP_INDEX = Pattern.compile("(?m)^\\s*(?:[0-9]{1,2}[.、)](?![0-9])|[（(][0-9]{1,2}[)）])\\s*");
    private static final String DIGITS = "零〇一二两三四五六七八九十百千万亿点半";
    private static final String UNITS = "个工作日|个自然日|工作日|自然日|万元|百分比|平方米|立方米|千克|公斤|厘米|毫米|分钟|小时|吨|元|天|日|年|月|秒|件|个|条|次|人|家|克|米|箱|批|单|%|％";
    private static final Pattern ARABIC = Pattern.compile(
            "(?<!\\d)([+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*((?:万|亿)?(?:" + UNITS + "))?");
    private static final Pattern CHINESE = Pattern.compile("([" + DIGITS + "]+)\\s*(" + UNITS + ")");
    private static final Pattern CLAUSE = Pattern.compile("第\\s*([0-9]+|[零〇一二两三四五六七八九十百千]+)\\s*条");
    private static final Pattern PERCENT_PREFIX = Pattern.compile(
            "百分之\\s*(?:([" + DIGITS + "]+)|([0-9]+(?:\\.[0-9]+)?))");
    private static final List<String> SENSITIVE = List.of(
            "password_hash", "sys_user", "sys_role", "ai_query_audit", "kb_chunk",
            "select ", "insert ", "update ", "delete ", "drop table",
            "系统提示词", "system prompt", "sk-");

    private AnswerFidelityGuard() {
    }

    public static List<String> check(String answer, String context) {
        List<String> violations = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return violations;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        for (String token : SENSITIVE) {
            if (lower.contains(token)) {
                violations.add("回答中出现敏感串「" + token.trim() + "」");
            }
        }

        String answerBody = STEP_INDEX.matcher(answer).replaceAll("");
        Set<NumberFact> source = facts(STEP_INDEX.matcher(context == null ? "" : context).replaceAll(""));
        Set<NumberFact> invented = new LinkedHashSet<>();
        for (NumberFact claim : facts(answerBody)) {
            boolean supported = source.stream().anyMatch(fact -> claim.matches(fact));
            if (!supported) {
                invented.add(claim);
            }
        }
        if (!invented.isEmpty()) {
            violations.add("回答中出现片段里没有或单位不匹配的数值："
                    + String.join("、", invented.stream().map(NumberFact::display).toList()));
        }
        return violations;
    }

    private static Set<NumberFact> facts(String text) {
        Set<NumberFact> facts = new LinkedHashSet<>();
        boolean[] consumed = new boolean[text.length()];
        Matcher clause = CLAUSE.matcher(text);
        while (clause.find()) {
            BigDecimal value = parseNumber(clause.group(1));
            if (value == null) value = parseChinese(clause.group(1));
            facts.add(new NumberFact(value, "CLAUSE", "条（编号）"));
            mark(consumed, clause.start(), clause.end());
        }
        Matcher percent = PERCENT_PREFIX.matcher(text);
        while (percent.find()) {
            BigDecimal value = percent.group(1) == null
                    ? parseNumber(percent.group(2)) : parseChinese(percent.group(1));
            if (value != null) {
                facts.add(new NumberFact(value, "PERCENT", "%"));
                mark(consumed, percent.start(), percent.end());
            }
        }
        collect(ARABIC.matcher(text), consumed, facts, false);
        collect(CHINESE.matcher(text), consumed, facts, true);
        return facts;
    }

    private static void collect(Matcher matcher, boolean[] consumed, Set<NumberFact> facts, boolean chinese) {
        while (matcher.find()) {
            if (overlaps(consumed, matcher.start(1), matcher.end(1))) {
                continue;
            }
            BigDecimal value = chinese ? parseChinese(matcher.group(1)) : parseNumber(matcher.group(1));
            if (value != null) {
                String rawUnit = matcher.group(2);
                facts.add(normalize(value, rawUnit));
                mark(consumed, matcher.start(), matcher.end());
            }
        }
    }

    private static BigDecimal parseNumber(String value) {
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal parseChinese(String value) {
        if (value.contains("点") && (value.endsWith("万") || value.endsWith("亿"))) {
            BigDecimal base = parseChinese(value.substring(0, value.length() - 1));
            return base == null ? null : base.multiply(new BigDecimal(value.endsWith("万") ? "10000" : "100000000"));
        }
        if ("半".equals(value)) {
            return new BigDecimal("0.5");
        }
        String[] decimalParts = value.split("点", -1);
        if (decimalParts.length > 2) {
            return null;
        }
        BigDecimal integer = new BigDecimal(parseChineseInteger(decimalParts[0]));
        if (decimalParts.length == 1) {
            return integer;
        }
        StringBuilder fraction = new StringBuilder("0.");
        for (int i = 0; i < decimalParts[1].length(); i++) {
            int digit = chineseDigit(decimalParts[1].charAt(i));
            if (digit < 0) {
                return null;
            }
            fraction.append(digit);
        }
        return integer.add(new BigDecimal(fraction.toString()));
    }

    private static long parseChineseInteger(String value) {
        if (value.isEmpty()) {
            return 0;
        }
        if (value.chars().allMatch(ch -> chineseDigit((char) ch) >= 0)) {
            long sequence = 0;
            for (int i = 0; i < value.length(); i++) {
                sequence = sequence * 10 + chineseDigit(value.charAt(i));
            }
            return sequence;
        }
        long total = 0;
        long section = 0;
        long digit = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            int mapped = chineseDigit(ch);
            if (mapped >= 0) {
                digit = mapped;
            } else if (ch == '十' || ch == '百' || ch == '千') {
                long unit = ch == '十' ? 10 : ch == '百' ? 100 : 1_000;
                section += (digit == 0 && ch == '十' ? 1 : digit) * unit;
                digit = 0;
            } else if (ch == '万' || ch == '亿') {
                section += digit;
                long unit = ch == '万' ? 10_000 : 100_000_000;
                total = ch == '亿' ? (total + section) * unit : total + section * unit;
                section = 0;
                digit = 0;
            } else {
                return -1;
            }
        }
        return total + section + digit;
    }

    private static int chineseDigit(char value) {
        return switch (value) {
            case '零', '〇' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private static NumberFact normalize(BigDecimal value, String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            return new NumberFact(value.stripTrailingZeros(), null, "");
        }
        if (rawUnit.startsWith("万") || rawUnit.startsWith("亿")) {
            value = value.multiply(new BigDecimal(rawUnit.startsWith("万") ? "10000" : "100000000"));
            rawUnit = rawUnit.substring(1);
        }
        return switch (rawUnit) {
            case "万元" -> new NumberFact(value.multiply(new BigDecimal("10000")).stripTrailingZeros(), "CNY", "元");
            case "元" -> new NumberFact(value.stripTrailingZeros(), "CNY", "元");
            case "%", "％", "百分比" -> new NumberFact(value.stripTrailingZeros(), "PERCENT", "%");
            case "工作日" -> new NumberFact(value.stripTrailingZeros(), "BUSINESS_DAY", "工作日");
            case "自然日", "天", "日" -> new NumberFact(value.stripTrailingZeros(), "CALENDAR_DAY", "自然日");
            case "个工作日" -> new NumberFact(value.stripTrailingZeros(), "BUSINESS_DAY", "工作日");
            case "个自然日" -> new NumberFact(value.stripTrailingZeros(), "CALENDAR_DAY", "自然日");
            default -> new NumberFact(value.stripTrailingZeros(), rawUnit, rawUnit);
        };
    }

    private static boolean overlaps(boolean[] consumed, int start, int end) {
        for (int i = start; i < end; i++) {
            if (consumed[i]) {
                return true;
            }
        }
        return false;
    }

    private static void mark(boolean[] consumed, int start, int end) {
        for (int i = start; i < end; i++) {
            consumed[i] = true;
        }
    }

    private record NumberFact(BigDecimal value, String unit, String displayUnit) {
        private boolean matches(NumberFact other) {
            return value.compareTo(other.value) == 0
                    && java.util.Objects.equals(unit, other.unit);
        }

        private String display() {
            return value.toPlainString() + displayUnit;
        }
    }
}
