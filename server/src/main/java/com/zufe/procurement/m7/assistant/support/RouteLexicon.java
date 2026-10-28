package com.zufe.procurement.m7.assistant.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三类问题路由词表（M7-2-1）。
 *
 * 一版没有可用的大模型，分类走规则词表 + 示例问句相似度，
 * 好处是可复现、可解释、可断言：同一问句每次判定一致，答辩时可逐条举证。
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
        WRITE_INTENT.put("帮我提交", 1);
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
