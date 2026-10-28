package com.zufe.procurement.m9.query.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 五类意图词表（M9-1-1）。
 *
 * 词表只做召回，不做判定：最终得分 = 词表命中权重 + 实体类型先验 + 2×示例问句相似度，
 * 置信度 = 最高分 ÷ 全部意图得分之和。这样「订单交付到哪了」这类同时命中订单与交付的问句
 * 会得到偏低的置信度并转澄清（M9-1-2），而不是硬猜一个。
 *
 * 词表与 ai_intent.intent_code 一一对应；新增意图需同时补词表与种子数据。
 */
public final class IntentLexicon {

    private IntentLexicon() {
    }

    public static final Map<String, Map<String, Integer>> BY_INTENT = new LinkedHashMap<>();

    static {
        Map<String, Integer> orderStatus = new LinkedHashMap<>();
        orderStatus.put("订单状态", 3);
        orderStatus.put("什么状态", 3);
        orderStatus.put("到哪一步", 3);
        orderStatus.put("走到哪", 3);
        orderStatus.put("审批通过", 2);
        orderStatus.put("审批结果", 2);
        orderStatus.put("审核", 1);
        orderStatus.put("订单", 1);
        orderStatus.put("状态", 1);
        BY_INTENT.put("order_status", orderStatus);

        Map<String, Integer> deliveryProgress = new LinkedHashMap<>();
        deliveryProgress.put("还没交货", 3);
        deliveryProgress.put("没交货", 3);
        deliveryProgress.put("未交付", 3);
        deliveryProgress.put("超期", 3);
        deliveryProgress.put("逾期", 3);
        deliveryProgress.put("交货", 2);
        deliveryProgress.put("交付", 2);
        deliveryProgress.put("到货", 1);
        deliveryProgress.put("交货期", 2);
        BY_INTENT.put("delivery_progress", deliveryProgress);

        Map<String, Integer> priceHistory = new LinkedHashMap<>();
        priceHistory.put("成交价", 3);
        priceHistory.put("均价", 3);
        priceHistory.put("采购价", 3);
        priceHistory.put("多少钱", 2);
        priceHistory.put("价格", 2);
        priceHistory.put("报价", 1);
        priceHistory.put("买过", 2);
        priceHistory.put("贵", 1);
        priceHistory.put("便宜", 1);
        BY_INTENT.put("purchase_price_history", priceHistory);

        Map<String, Integer> stockAvailable = new LinkedHashMap<>();
        stockAvailable.put("库存", 3);
        stockAvailable.put("可用量", 3);
        stockAvailable.put("余量", 3);
        stockAvailable.put("存货", 2);
        stockAvailable.put("还有多少", 2);
        stockAvailable.put("剩余", 1);
        stockAvailable.put("在库", 2);
        BY_INTENT.put("stock_available", stockAvailable);

        Map<String, Integer> supplierDelivery = new LinkedHashMap<>();
        supplierDelivery.put("准时率", 3);
        supplierDelivery.put("延期", 3);
        supplierDelivery.put("延迟", 3);
        supplierDelivery.put("交付情况", 3);
        supplierDelivery.put("履约", 2);
        supplierDelivery.put("供应商", 1);
        supplierDelivery.put("厂家", 2);
        BY_INTENT.put("supplier_delivery", supplierDelivery);
    }

    /** 实体类型先验：问句里出现某类实体时，相关意图加权（0 物料、2 供应商、3 仓库） */
    public static final Map<Integer, Map<String, Double>> ENTITY_PRIOR = Map.of(
            1, Map.of("stock_available", 1.5, "purchase_price_history", 1.5),
            2, Map.of("supplier_delivery", 2.0, "delivery_progress", 0.5),
            3, Map.of("stock_available", 1.5)
    );

    /** 问句里出现「供应商 + 交付」类词时，供应商交付意图再加权，避免与交付进度打平 */
    public static final List<String> SUPPLIER_HINTS = List.of("供应商", "厂家", "这家", "厂商");

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
