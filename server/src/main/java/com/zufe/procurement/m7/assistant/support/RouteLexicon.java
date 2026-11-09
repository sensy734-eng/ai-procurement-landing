package com.zufe.procurement.m7.assistant.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三类问题路由词表（M7-2-1）。
 *
 * 一版没有可用的大模型，分类走规则词表 + 示例问句相似度，
 * 好处是可复现、可解释、可断言：同一问句每次判定一致，可通过回归用例核对。
 * 权重用于区分强信号（「怎么」几乎只出现在操作类问句）与弱信号（「流程」两类都可能出现）。
 */
public final class RouteLexicon {

    private RouteLexicon() {
    }

    /** 教程类：问「怎么做」，答案是步骤 */
    public static final Map<String, Integer> TUTORIAL = new LinkedHashMap<>();
    /** 知识类：问「怎么规定」，答案是条款 */
    public static final Map<String, Integer> KNOWLEDGE = new LinkedHashMap<>();
    /** 数据类：问「现在是什么」，答案是表 */
    public static final Map<String, Integer> DATA = new LinkedHashMap<>();

    static {
        // 强信号：出现即倾向教程类
        TUTORIAL.put("怎么", 3);
        TUTORIAL.put("如何", 3);
        TUTORIAL.put("怎样", 2);
        TUTORIAL.put("步骤", 3);
        TUTORIAL.put("在哪", 2);
        TUTORIAL.put("入口", 2);
        TUTORIAL.put("操作", 2);
        TUTORIAL.put("怎么弄", 3);
        TUTORIAL.put("界面", 1);
        TUTORIAL.put("菜单", 1);
        TUTORIAL.put("填写", 1);
        TUTORIAL.put("录入", 1);
        TUTORIAL.put("新增", 1);
        TUTORIAL.put("提交", 1);

        KNOWLEDGE.put("制度", 3);
        KNOWLEDGE.put("规定", 3);
        KNOWLEDGE.put("规范", 2);
        KNOWLEDGE.put("办法", 2);
        KNOWLEDGE.put("政策", 2);
        KNOWLEDGE.put("条款", 3);
        KNOWLEDGE.put("标准", 2);
        KNOWLEDGE.put("期限", 2);
        KNOWLEDGE.put("几天", 2);
        KNOWLEDGE.put("多久", 2);
        KNOWLEDGE.put("多少天", 2);
        KNOWLEDGE.put("职责", 2);
        KNOWLEDGE.put("是否允许", 2);
        KNOWLEDGE.put("可以吗", 2);
        KNOWLEDGE.put("原则", 2);
        KNOWLEDGE.put("要求", 1);
        KNOWLEDGE.put("流程", 1);
        // 「供应商准入需要提供哪些资质材料」这类问规则的句子，主体名词落在数据域的
        // 「供应商」上，但问的是制度要求。不加这几个词，整句会被判成数据查询，
        // 进而拿某家供应商的交付统计去回答一个问资质的问题（实测就这么错过）。
        KNOWLEDGE.put("准入", 2);
        KNOWLEDGE.put("资质", 2);
        KNOWLEDGE.put("条件", 2);
        KNOWLEDGE.put("具备", 1);
        KNOWLEDGE.put("需要", 1);

        DATA.put("订单", 2);
        DATA.put("库存", 2);
        DATA.put("可用量", 2);
        DATA.put("余量", 2);
        DATA.put("价格", 2);
        DATA.put("成交", 2);
        DATA.put("均价", 2);
        DATA.put("供应商", 2);
        DATA.put("申请单", 2);
        DATA.put("入库", 2);
        DATA.put("验收", 2);
        DATA.put("超期", 2);
        DATA.put("交付", 1);
        DATA.put("多少", 1);
        DATA.put("几个", 1);
        DATA.put("几张", 1);
        DATA.put("统计", 2);
        DATA.put("查一下", 2);
        DATA.put("查询", 1);
        DATA.put("列表", 1);
        DATA.put("状态", 1);
    }

    /** 写操作词：智能助手只读，命中即拒绝（M7-2-4、M9-5-4） */
    public static final Map<String, Integer> WRITE_INTENT = new LinkedHashMap<>();

    static {
        WRITE_INTENT.put("删除", 1);
        WRITE_INTENT.put("删掉", 1);
        WRITE_INTENT.put("修改", 1);
        WRITE_INTENT.put("改成", 1);
        WRITE_INTENT.put("新增", 1);
        WRITE_INTENT.put("创建", 1);
        WRITE_INTENT.put("下单", 1);
        WRITE_INTENT.put("审批通过", 1);
        WRITE_INTENT.put("驳回", 1);
        WRITE_INTENT.put("作废", 1);
        WRITE_INTENT.put("入库确认", 1);
        WRITE_INTENT.put("清空", 1);
        WRITE_INTENT.put("帮我提交", 1);
    }

    /**
     * 问信息标记：出现这些词说明用户在要信息，不是在发指令。
     *
     * 不能只放「吗/没有」这类疑问词——「查一下这张单的作废情况」一个疑问词都没有，
     * 但显然是在问。「查一下/看看/统计」这些查询动词同样是「要信息」的信号。
     * 放宽是安全的：真指令通常带命令标记（帮我/请/把），命令标记会优先判成指令。
     */
    public static final String[] INFO_MARKERS = {
            "吗", "没有", "了没", "是否", "是不是", "有没有", "多少", "哪些", "哪个",
            "几个", "几张", "哪一", "哪家", "什么时候",
            "情况", "进度", "状态", "查一下", "查查", "看看", "看一下", "列出", "统计"};

    /** 命令标记：这些词后面接的才是「让我去做」 */
    public static final String[] COMMAND_MARKERS = {"帮我", "请", "替我", "给我", "把", "将"};

    /** 命令标记后紧跟这些动词时，说明是在让我「查」而不是让我「改」 */
    public static final String[] QUERY_VERBS = {"查", "看", "找", "列出", "统计", "对比", "告诉", "问", "说"};

    /**
     * 这是在下写操作指令，还是在问某张单的状态？
     *
     * 为什么要单独一个方法：写操作词表里「审批通过」「入库确认」同时也是状态名，
     * 只靠 {@link #score} 的 contains 命中，会把「这张订单审批通过没有」判成
     * 「用户在让我审批通过」。判据必须是语气：
     *   有命令标记 → 指令（「帮我把订单作废」）；
     *   无命令标记 + 有问信息标记 → 问状态（「审批通过没有」「哪些订单作废了」）；
     *   两者都无 → 仍按指令处理，宁可多回一句「我不做写操作」，也不对直白命令装没看见。
     * 「命令标记 + 查询动词」不算命令：「帮我查一下…审批通过没有」里的「帮我」后面接「查」。
     */
    public static boolean isWriteInstruction(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        if (score(question, WRITE_INTENT) == 0) {
            return false;
        }
        return hasCommandMarker(question) || !hasInfoMarker(question);
    }

    public static boolean hasInfoMarker(String question) {
        for (String marker : INFO_MARKERS) {
            if (question.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCommandMarker(String question) {
        for (String marker : COMMAND_MARKERS) {
            int at = question.indexOf(marker);
            while (at >= 0) {
                String after = question.substring(Math.min(at + marker.length(), question.length()));
                boolean followedByQuery = false;
                for (String verb : QUERY_VERBS) {
                    if (after.startsWith(verb)) {
                        followedByQuery = true;
                        break;
                    }
                }
                if (!followedByQuery) {
                    return true;
                }
                at = question.indexOf(marker, at + 1);
            }
        }
        return false;
    }

    /** 指代词：命中且上一轮有上下文时做实体继承（M7-1-3） */
    public static final String[] PRONOUNS = {"它", "这个", "该", "这家", "这张", "这只", "此"};

    public static int score(String question, Map<String, Integer> lexicon) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : lexicon.entrySet()) {
            if (question.contains(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }
}
