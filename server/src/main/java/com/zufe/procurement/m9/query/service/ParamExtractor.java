package com.zufe.procurement.m9.query.service;

import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m9.query.entity.AiIntentParam;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数提取（M9-2-1 时间、M9-2-3 数值条件、单据号与状态词）。
 *
 * 提取只是「候选值」，是否被接受由 ParamValidator 按 ai_intent_param 判定。
 * 问句里出现而当前意图白名单没有的参数，在这里记为 rejected 而不是丢掉——
 * 用户应该知道「你问的条件系统不支持」，而不是拿到一个悄悄忽略了条件的答案。
 */
@Service
public class ParamExtractor {

    private static final Pattern ORDER_NO = Pattern.compile("[A-Za-z]{2}-[A-Za-z]{2}-\\d{8}-\\d{4}");
    private static final Pattern DATE_FULL = Pattern.compile("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?");
    private static final Pattern DATE_MONTH = Pattern.compile("(\\d{4})年(\\d{1,2})月");
    private static final Pattern RECENT_DAYS = Pattern.compile("(?:近|最近|过去)\\s*(\\d+)\\s*天");
    private static final Pattern RECENT_WEEKS = Pattern.compile("(?:近|最近|过去)\\s*(\\d+)\\s*(?:周|个?星期)");
    private static final Pattern RECENT_MONTHS = Pattern.compile("(?:近|最近|过去)\\s*(\\d+)\\s*个?月");
    private static final Pattern AMOUNT_CMP = Pattern.compile(
            "(?:金额|总额|总价|价款|合计)?\\s*(大于|超过|高于|不少于|不低于|不超过|低于|小于)\\s*(\\d+(?:\\.\\d+)?)\\s*(万元|万|元|块)?");

    public Extraction extract(String question, List<AiIntentParam> specs) {
        Extraction extraction = new Extraction();
        if (question == null) {
            return extraction;
        }
        String text = question.trim();
        extractTimeRange(text, specs, extraction);
        extractOrderNo(text, specs, extraction);
        extractAmount(text, specs, extraction);
        extractFlags(text, specs, extraction);
        return extraction;
    }

    // ---------------- M9-2-1 时间范围 ----------------

    private void extractTimeRange(String text, List<AiIntentParam> specs, Extraction extraction) {
        LocalDate today = LocalDate.now();
        LocalDate from = null;
        LocalDate to = null;
        String label = null;

        Matcher days = RECENT_DAYS.matcher(text);
        Matcher weeks = RECENT_WEEKS.matcher(text);
        Matcher months = RECENT_MONTHS.matcher(text);
        Matcher full = DATE_FULL.matcher(text);
        Matcher month = DATE_MONTH.matcher(text);

        if (days.find()) {
            int n = Integer.parseInt(days.group(1));
            from = today.minusDays(n - 1L);
            to = today;
            label = "近 " + n + " 天";
        } else if (weeks.find()) {
            int n = Integer.parseInt(weeks.group(1));
            from = today.minusWeeks(n).plusDays(1);
            to = today;
            label = "近 " + n + " 周";
        } else if (months.find()) {
            int n = Integer.parseInt(months.group(1));
            from = today.minusMonths(n).plusDays(1);
            to = today;
            label = "近 " + n + " 个月";
        } else if (full.find()) {
            from = LocalDate.of(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)),
                    Integer.parseInt(full.group(3)));
            to = from;
            label = from.toString();
        } else if (month.find()) {
            from = LocalDate.of(Integer.parseInt(month.group(1)), Integer.parseInt(month.group(2)), 1);
            to = from.with(TemporalAdjusters.lastDayOfMonth());
            label = month.group(1) + "年" + month.group(2) + "月";
        } else if (text.contains("今天") || text.contains("今日")) {
            from = today;
            to = today;
            label = "今天";
        } else if (text.contains("昨天")) {
            from = today.minusDays(1);
            to = from;
            label = "昨天";
        } else if (text.contains("本周") || text.contains("这周")) {
            from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            to = today;
            label = "本周";
        } else if (text.contains("上周")) {
            to = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
            from = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            label = "上周";
        } else if (text.contains("本月") || text.contains("这个月")) {
            from = today.withDayOfMonth(1);
            to = today;
            label = "本月";
        } else if (text.contains("上个月") || text.contains("上月")) {
            to = today.withDayOfMonth(1).minusDays(1);
            from = to.withDayOfMonth(1);
            label = "上个月";
        } else if (text.contains("今年")) {
            from = LocalDate.of(today.getYear(), 1, 1);
            to = today;
            label = "今年";
        } else if (text.contains("去年")) {
            from = LocalDate.of(today.getYear() - 1, 1, 1);
            to = LocalDate.of(today.getYear() - 1, 12, 31);
            label = "去年";
        } else if (text.contains("近半年") || text.contains("最近半年")) {
            from = today.minusMonths(6).plusDays(1);
            to = today;
            label = "近半年";
        }
        if (from == null) {
            return;
        }

        ChatVO.TimeRange range = new ChatVO.TimeRange();
        range.setFrom(from.toString());
        range.setTo(to.toString());
        range.setLabel(label);
        extraction.setTimeRange(range);

        if (has(specs, "date_from")) {
            extraction.put("date_from", from.toString(), label);
        } else {
            extraction.reject("date_from", "开始日期", from.toString(), "当前意图不支持时间范围条件");
        }
        if (has(specs, "date_to")) {
            extraction.put("date_to", to.toString(), label);
        } else {
            extraction.reject("date_to", "结束日期", to.toString(), "当前意图不支持时间范围条件");
        }
    }

    // ---------------- 单据号 ----------------

    private void extractOrderNo(String text, List<AiIntentParam> specs, Extraction extraction) {
        Matcher matcher = ORDER_NO.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String no = matcher.group().toUpperCase();
        if (has(specs, "order_no")) {
            extraction.put("order_no", no, no);
        } else {
            extraction.reject("order_no", "订单号", no, "当前意图不支持按单据号查询");
        }
    }

    // ---------------- M9-2-3 数值条件 ----------------

    private void extractAmount(String text, List<AiIntentParam> specs, Extraction extraction) {
        Matcher matcher = AMOUNT_CMP.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String operator = matcher.group(1);
        BigDecimal value = new BigDecimal(matcher.group(2));
        String unit = matcher.group(3);
        if ("万元".equals(unit) || "万".equals(unit)) {
            value = value.multiply(new BigDecimal("10000"));
        }
        boolean greater = List.of("大于", "超过", "高于", "不少于", "不低于").contains(operator);
        String code = greater ? "amount_gte" : "amount_lte";
        String name = greater ? "金额下限" : "金额上限";
        if (has(specs, code)) {
            extraction.put(code, value.setScale(2, java.math.RoundingMode.HALF_UP), matcher.group());
        } else {
            extraction.reject(code, name, value.setScale(2, java.math.RoundingMode.HALF_UP),
                    "当前意图不支持金额条件");
        }
    }

    // ---------------- 布尔条件与状态词 ----------------

    private void extractFlags(String text, List<AiIntentParam> specs, Extraction extraction) {
        if (text.matches(".*(超期|逾期|没交货|未交付|还没交|没交完|延期未交).*")) {
            if (has(specs, "overdue_only")) {
                extraction.put("overdue_only", Boolean.TRUE, "仅看超期");
            }
        }
        Integer lifecycle = lifecycleStatus(text);
        if (lifecycle != null) {
            if (has(specs, "lifecycle_status")) {
                extraction.put("lifecycle_status", lifecycle, lifecycleName(lifecycle));
            } else {
                extraction.reject("lifecycle_status", "生命周期状态", lifecycleName(lifecycle),
                        "当前意图不支持按生命周期状态过滤");
            }
        }
        Integer audit = auditStatus(text);
        if (audit != null) {
            if (has(specs, "audit_status")) {
                extraction.put("audit_status", audit, auditName(audit));
            } else {
                extraction.reject("audit_status", "审批结果状态", auditName(audit),
                        "当前意图不支持按审批状态过滤");
            }
        }
        Integer fulfill = fulfillStatus(text);
        if (fulfill != null) {
            if (has(specs, "fulfill_status")) {
                extraction.put("fulfill_status", fulfill, fulfillName(fulfill));
            } else {
                extraction.reject("fulfill_status", "履约状态", fulfillName(fulfill),
                        "当前意图不支持按履约状态过滤");
            }
        }
    }

    private Integer lifecycleStatus(String text) {
        if (text.contains("草稿")) {
            return 1;
        }
        if (text.contains("已提交") || text.contains("待审批")) {
            return 2;
        }
        if (text.contains("已审核") || text.contains("审批通过")) {
            return 3;
        }
        if (text.contains("已关闭")) {
            return 4;
        }
        if (text.contains("已作废") || text.contains("作废")) {
            return 5;
        }
        return null;
    }

    private Integer auditStatus(String text) {
        if (text.contains("驳回")) {
            return 3;
        }
        if (text.contains("审批通过") || text.contains("已通过")) {
            return 2;
        }
        if (text.contains("未审核") || text.contains("待审核")) {
            return 1;
        }
        return null;
    }

    private Integer fulfillStatus(String text) {
        if (text.contains("部分交付") || text.contains("部分交货")) {
            return 2;
        }
        if (text.contains("全部交付") || text.contains("已交付完") || text.contains("交付完成")) {
            return 3;
        }
        if (text.contains("待交付") || text.contains("还没交付")) {
            return 1;
        }
        return null;
    }

    public static String lifecycleName(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "草稿";
            case 2 -> "已提交";
            case 3 -> "已审核";
            case 4 -> "已关闭";
            case 5 -> "已作废";
            default -> "未知";
        };
    }

    public static String auditName(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "未审核";
            case 2 -> "审批通过";
            case 3 -> "已驳回";
            default -> "未知";
        };
    }

    public static String fulfillName(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "待交付";
            case 2 -> "部分交付";
            case 3 -> "全部交付";
            case 4 -> "已关闭";
            default -> "未知";
        };
    }

    private boolean has(List<AiIntentParam> specs, String code) {
        return specs.stream().anyMatch(spec -> code.equals(spec.getParamCode()));
    }

    /** 提取结果：候选参数值 + 时间范围 + 被拒候选 */
    @Data
    public static class Extraction {
        private Map<String, Object> values = new LinkedHashMap<>();
        private Map<String, String> labels = new LinkedHashMap<>();
        private ChatVO.TimeRange timeRange;
        private List<ChatVO.ParamItem> rejected = new ArrayList<>();

        void put(String code, Object value, String label) {
            values.put(code, value);
            labels.put(code, label);
        }

        void reject(String code, String name, Object value, String reason) {
            boolean exists = rejected.stream().anyMatch(item -> code.equals(item.getCode()));
            if (exists) {
                return;
            }
            ChatVO.ParamItem item = new ChatVO.ParamItem();
            item.setCode(code);
            item.setName(name);
            item.setValue(value);
            item.setReason(reason);
            item.setSource("question");
            rejected.add(item);
        }
    }
}
