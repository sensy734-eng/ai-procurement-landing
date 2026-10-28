package com.zufe.procurement.m8.qa.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 教程步骤规整（M8-2-2、M8-2-3）。
 *
 * 步骤顺序由后端按编号或原文顺序确定，不由模型自行排序：
 * 1. 文本自带编号（1. / 1、/ ① / 第一步 / 步骤一）→ 按编号数值升序；
 * 2. 无编号但含「然后 / 接着 / 再」等承接词 → 按中文逗号切分动作短语，保持原文顺序；
 * 3. 都没有 → 按句号切分，最多 8 条。
 *
 * 保留原文顺序这一点很重要：模型改序在操作类问题上会直接导致演示失败。
 */
@Component
public class StepExtractor {

    private static final Pattern NUMBERED_LINE = Pattern.compile(
            "^\\s*(?:第?\\s*(\\d+)\\s*[.、)）步]|([①-⑩])|步骤\\s*(\\d+))\\s*");
    /** 编号后紧跟数字的是章节号（2.1），不是步骤序号，用负向断言排除 */
    private static final Pattern INLINE_NUMBER = Pattern.compile(
            "(?:^|[。；;])\\s*(\\d+)\\s*[.、)）]\\s*(?!\\d)");
    /** 章节号前缀，如「2.1 新增采购申请」中的 2.1 */
    private static final Pattern SECTION_PREFIX = Pattern.compile("^\\s*\\d+(?:\\.\\d+)*\\s+");
    private static final Pattern LEADING_CONNECTIVE = Pattern.compile("^(然后|接着|之后|最后|再|并且|同时)");

    private static final String[] CONNECTIVES = {"然后", "接着", "之后", "再", "最后"};

    public List<String> extract(String text) {
        List<String> steps = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return steps;
        }
        // 去掉章节号前缀（2.1、3.2.1），否则会被误当成第一个步骤序号
        String body = SECTION_PREFIX.matcher(text).replaceFirst("");
        // 首句是章节标题（「新增采购申请。」），不是动作，去掉后再拆步骤
        int firstStop = body.indexOf('。');
        int firstComma = body.indexOf('，');
        if (firstStop > 0 && firstStop <= 12 && (firstComma < 0 || firstComma > firstStop)) {
            body = body.substring(firstStop + 1);
        }
        List<String> numbered = extractNumbered(body);
        if (numbered.size() >= 2) {
            return numbered;
        }
        if (containsConnective(body)) {
            for (String fragment : body.split("[，,；;]")) {
                String step = clean(fragment);
                if (step.length() >= 4) {
                    steps.add(step);
                }
            }
            if (steps.size() >= 2) {
                return steps;
            }
        }
        steps.clear();
        for (String sentence : body.split("[。！？]")) {
            String step = clean(sentence);
            if (step.length() >= 4) {
                steps.add(step);
            }
            if (steps.size() >= 8) {
                break;
            }
        }
        return steps;
    }

    /** 去掉步骤开头的序号与承接词，让展示出来的步骤是纯动作描述 */
    private String clean(String fragment) {
        String step = fragment.trim();
        step = step.replaceAll("^\\d+\\s*[.、)）]\\s*", "");
        step = LEADING_CONNECTIVE.matcher(step).replaceFirst("");
        return step.trim();
    }

    private List<String> extractNumbered(String text) {
        List<NumberedStep> ordered = new ArrayList<>();
        int position = 0;
        for (String line : text.split("\\n")) {
            Matcher matcher = NUMBERED_LINE.matcher(line);
            if (matcher.find()) {
                int number = parseNumber(matcher);
                String content = clean(line.substring(matcher.end()));
                if (!content.isEmpty()) {
                    ordered.add(new NumberedStep(number, position++, content));
                }
            }
        }
        if (ordered.size() < 2) {
            // 单行内的编号：1. xxx。2. yyy。
            ordered.clear();
            Matcher inline = INLINE_NUMBER.matcher(text);
            int lastEnd = -1;
            int number = 0;
            int index = 0;
            while (inline.find()) {
                if (lastEnd >= 0) {
                    String content = clean(text.substring(lastEnd, inline.start()));
                    if (content.length() >= 4) {
                        ordered.add(new NumberedStep(number, index++, content));
                    }
                }
                number = Integer.parseInt(inline.group(1));
                lastEnd = inline.end();
            }
            if (lastEnd >= 0) {
                String tail = clean(text.substring(lastEnd));
                if (tail.length() >= 4) {
                    ordered.add(new NumberedStep(number, index, tail));
                }
            }
        }
        ordered.sort((a, b) -> {
            if (a.number == b.number) {
                return Integer.compare(a.position, b.position);
            }
            return Integer.compare(a.number, b.number);
        });
        return ordered.stream().map(item -> item.content).toList();
    }

    private int parseNumber(Matcher matcher) {
        if (matcher.group(1) != null) {
            return Integer.parseInt(matcher.group(1));
        }
        if (matcher.group(3) != null) {
            return Integer.parseInt(matcher.group(3));
        }
        String circle = matcher.group(2);
        return circle == null ? 0 : circle.charAt(0) - '①' + 1;
    }

    private boolean containsConnective(String text) {
        for (String word : CONNECTIVES) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private record NumberedStep(int number, int position, String content) {
    }
}
