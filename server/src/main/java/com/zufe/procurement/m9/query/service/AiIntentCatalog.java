package com.zufe.procurement.m9.query.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m11.param.entity.SysParam;
import com.zufe.procurement.m11.param.mapper.SysParamMapper;
import com.zufe.procurement.m9.query.entity.AiEntityAlias;
import com.zufe.procurement.m9.query.entity.AiIntent;
import com.zufe.procurement.m9.query.entity.AiIntentParam;
import com.zufe.procurement.m9.query.mapper.AiEntityAliasMapper;
import com.zufe.procurement.m9.query.mapper.AiIntentMapper;
import com.zufe.procurement.m9.query.mapper.AiIntentParamMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图白名单目录（M9-1-3、M9-2-4）。
 *
 * 意图、参数规格、实体别名全部从库表读取，不在代码里写死：
 * 新增一类查询只需插 ai_intent 与 ai_intent_param 两行数据 + 一个执行服务，
 * 分类词表按意图编码挂接（IntentLexicon）。
 */
@Service
public class AiIntentCatalog {

    private static final String PARAM_CONFIDENCE = "ai_intent_confidence_threshold";
    private static final String PARAM_HISTORY_ROUND = "ai_history_round";

    private final AiIntentMapper intentMapper;
    private final AiIntentParamMapper paramMapper;
    private final AiEntityAliasMapper aliasMapper;
    private final SysParamMapper sysParamMapper;

    public AiIntentCatalog(AiIntentMapper intentMapper, AiIntentParamMapper paramMapper,
                           AiEntityAliasMapper aliasMapper, SysParamMapper sysParamMapper) {
        this.intentMapper = intentMapper;
        this.paramMapper = paramMapper;
        this.aliasMapper = aliasMapper;
        this.sysParamMapper = sysParamMapper;
    }

    /** 启用的意图，按 sort_no 排序 */
    public List<AiIntent> enabledIntents() {
        return intentMapper.selectList(new LambdaQueryWrapper<AiIntent>()
                .eq(AiIntent::getEnabled, 1)
                .orderByAsc(AiIntent::getSortNo));
    }

    public Map<String, AiIntent> enabledByCode() {
        Map<String, AiIntent> map = new LinkedHashMap<>();
        for (AiIntent intent : enabledIntents()) {
            map.put(intent.getIntentCode(), intent);
        }
        return map;
    }

    public AiIntent byCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return intentMapper.selectOne(new LambdaQueryWrapper<AiIntent>()
                .eq(AiIntent::getIntentCode, code)
                .eq(AiIntent::getEnabled, 1)
                .last("LIMIT 1"));
    }

    public List<AiIntentParam> paramsOf(Long intentId) {
        return paramMapper.selectList(new LambdaQueryWrapper<AiIntentParam>()
                .eq(AiIntentParam::getIntentId, intentId)
                .orderByAsc(AiIntentParam::getSortNo));
    }

    /** 全部别名，供口语化名称归一（M9-3-1） */
    public List<AiEntityAlias> aliases() {
        return new ArrayList<>(aliasMapper.selectList(new LambdaQueryWrapper<>()));
    }

    /** 意图置信度阈值（M9-1-2），读数失败时回落到 0.60 */
    public double confidenceThreshold() {
        String value = readParam(PARAM_CONFIDENCE, "0.60");
        try {
            return new BigDecimal(value).doubleValue();
        } catch (NumberFormatException e) {
            return 0.60;
        }
    }

    /** 多轮上下文保留轮数（M7-1-3），当前按会话最近 N 条消息取上下文 */
    public int historyRound() {
        String value = readParam(PARAM_HISTORY_ROUND, "10");
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private String readParam(String code, String fallback) {
        SysParam param = sysParamMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamCode, code)
                .last("LIMIT 1"));
        return param == null || param.getParamValue() == null ? fallback : param.getParamValue();
    }
}
