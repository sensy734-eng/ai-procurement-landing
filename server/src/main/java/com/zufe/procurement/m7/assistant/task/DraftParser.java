package com.zufe.procurement.m7.assistant.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.llm.LlmJson;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.*;

/** Model extracts exact source spans; numeric and relative-date normalization is deterministic. */
public final class DraftParser {
    private DraftParser() {}
    public record Extracted(String source, String material, String quantity, String unit, String date, String owner) {}

    public static List<Extracted> model(String output, String source, ObjectMapper json) {
        try {
            if (output.length() > 16000) throw new IllegalArgumentException();
            JsonNode items = json.readTree(LlmJson.extractObject(output)).path("items");
            if (!items.isArray() || items.isEmpty() || items.size() > 10) throw new IllegalArgumentException();
            List<Extracted> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonNode item : items) {
                String passage = exact(item, "source", source);
                if (passage.isBlank() || !seen.add(passage)) throw new IllegalArgumentException();
                String material = exact(item, "materialText", passage);
                if (material.isBlank()) throw new IllegalArgumentException();
                String quantity = exact(item,"quantityText",passage);
                String numeric = "0-9零〇一二两三四五六七八九十百千万亿点.,";
                if (!quantity.isEmpty() && !Pattern.compile("(?<![" + numeric + "])" + Pattern.quote(quantity)
                        + "(?![" + numeric + "])").matcher(passage).find()) throw new IllegalArgumentException();
                result.add(new Extracted(passage, material, quantity, exact(item,"unitText",passage),
                        exact(item,"dateText",source), exact(item,"ownerText",source)));
            }
            return result;
        } catch (Exception error) { throw new IllegalArgumentException("模型抽取内容与原文不符", error); }
    }
    private static String exact(JsonNode item, String key, String source) {
        JsonNode value = item.get(key);
        if (value == null || value.isNull()) return "";
        if (!value.isTextual()) throw new IllegalArgumentException();
        String text = value.asText().strip();
        if (text.length() > 800 || (!text.isEmpty() && !source.contains(text))) throw new IllegalArgumentException();
        return text;
    }

    /** Explicitly labeled offline template parsing. No claim of natural-language/model understanding. */
    public static List<Extracted> template(String text) {
        List<Extracted> rows = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            if (rows.size() >= 10) return List.of();
            Map<String,String> fields = new HashMap<>();
            for (String part : line.split("[；;]")) {
                String[] pair = part.strip().split("[:：]", 2);
                if (pair.length != 2 || !Set.of("物料","数量","单位","需求日期","归属").contains(pair[0].strip())
                        || fields.put(pair[0].strip(), pair[1].strip()) != null) return List.of();
            }
            if (!fields.containsKey("物料") || fields.get("物料").isBlank()) return List.of();
            rows.add(new Extracted(line, fields.get("物料"), fields.getOrDefault("数量",""), fields.getOrDefault("单位",""),
                    fields.getOrDefault("需求日期",""), fields.getOrDefault("归属","")));
        }
        return rows;
    }

    public static BigDecimal quantity(String text) {
        if (text == null || text.isBlank()) return null;
        String value = text.strip();
        if (value.contains(",")) {
            if (!value.matches("[1-9][0-9]{0,2}(?:,[0-9]{3})+(?:\\.[0-9]+)?")) return null;
            value = value.replace(",", "");
        }
        if (value.length() > 40) return null;
        try {
            BigDecimal number;
            if (value.matches("[0-9]+(?:\\.[0-9]+)?")) number = new BigDecimal(value);
            else if (value.matches("[零〇一二两三四五六七八九十百千万]+")) {
                long total=0, section=0, digit=0;
                for (char c : value.toCharArray()) {
                    int d="零一二三四五六七八九".indexOf(c);
                    if (c=='〇') d=0; if (c=='两') d=2;
                    if (d>=0) digit=d;
                    else if(c=='万') { total+=(section+digit)*10000;section=0;digit=0; }
                    else { int unit=c=='十'?10:c=='百'?100:1000;section+=(digit==0?1:digit)*unit;digit=0; }
                }
                number=BigDecimal.valueOf(total+section+digit);
                // Reject ambiguous shorthand (一百二) and malformed digit strings (一二).
                String normalized = value.replace('两','二').replace('〇','零');
                if (normalized.startsWith("一十")) normalized = normalized.substring(1);
                if (number.signum() <= 0 || number.compareTo(new BigDecimal("10000000")) > 0
                        || !chinese(number.intValue()).equals(normalized)) return null;
            } else return null;
            return number.signum()>0 && number.compareTo(new BigDecimal("10000000"))<=0 && number.scale()<=4 ? number : null;
        } catch(NumberFormatException error) { return null; }
    }

    private static String chinese(int number) {
        if (number >= 10000) {
            int rest = number % 10000;
            return chinese(number / 10000) + "万" + (rest == 0 ? "" : (rest < 1000 ? "零" : "") + sectionChinese(rest));
        }
        String value = sectionChinese(number);
        return value.startsWith("一十") ? value.substring(1) : value;
    }
    private static String sectionChinese(int number) {
        StringBuilder text = new StringBuilder(); boolean zero = false;
        int[] divisors = {1000,100,10,1}; String[] units = {"千","百","十",""};
        for(int i=0;i<divisors.length;i++) {
            int digit = number / divisors[i]; number %= divisors[i];
            if(digit > 0) {
                if(zero) text.append('零');
                text.append("零一二三四五六七八九".charAt(digit)).append(units[i]); zero=false;
            } else if(text.length()>0 && number>0) zero=true;
        }
        return text.toString();
    }

    public static LocalDate date(String text, LocalDate today) {
        if (text == null || text.isBlank()) return null;
        String s=text.strip();
        try {
            if(s.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(s);
            Matcher full=Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日?").matcher(s);
            if(full.matches()) return LocalDate.of(Integer.parseInt(full.group(1)),Integer.parseInt(full.group(2)),Integer.parseInt(full.group(3)));
            if(s.equals("今天")) return today;
            if(s.equals("明天")) return today.plusDays(1);
            if(s.equals("后天")) return today.plusDays(2);
            Matcher week=Pattern.compile("(下周|本周|这周)([一二三四五六日天])").matcher(s);
            if(week.matches()) {
                int day="一二三四五六日".indexOf(week.group(2));if(day<0)day=6;
                return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(day+(week.group(1).equals("下周")?7:0));
            }
        } catch(DateTimeException ignored) { }
        return null;
    }
}
