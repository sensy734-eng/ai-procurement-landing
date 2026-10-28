package com.zufe.procurement.m8.qa.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 关联功能入口（M8-2-4）。
 *
 * 命中操作类章节后，顺带给出去哪个页面做这件事、该章节适用于哪个文档版本。
 * 映射表按「最长关键词优先」匹配，避免「采购入库」被「采购」抢先命中。
 */
@Component
public class MenuHintResolver {

    private static final Map<String, String[]> HINTS = new LinkedHashMap<>();

    static {
        HINTS.put("采购申请", new String[]{"/request", "采购申请"});
        HINTS.put("采购订单", new String[]{"/order", "采购订单"});
        HINTS.put("到货验收", new String[]{"/arrival", "到货验收"});
        HINTS.put("采购入库", new String[]{"/inbound", "采购入库"});
        HINTS.put("入库确认", new String[]{"/inbound", "采购入库"});
        HINTS.put("库存流水", new String[]{"/stock/flow", "库存流水"});
        HINTS.put("库存", new String[]{"/stock", "库存台账"});
        HINTS.put("参考价", new String[]{"/price", "价格参考"});
        HINTS.put("价格偏离", new String[]{"/price/deviation", "价格偏离关注"});
        HINTS.put("供应商", new String[]{"/master/supplier", "供应商档案"});
        HINTS.put("物料", new String[]{"/master/material", "物料档案"});
        HINTS.put("知识库", new String[]{"/system/kb", "知识库"});
    }

    /** 返回 {path, label}，没命中返回 null */
    public String[] resolve(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String bestKey = null;
        for (String key : HINTS.keySet()) {
            if (text.contains(key) && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        return bestKey == null ? null : HINTS.get(bestKey);
    }
}
