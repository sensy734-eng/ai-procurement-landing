package com.zufe.procurement.m8.qa.service;

import com.zufe.procurement.m7.assistant.mapper.AiMessageMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 引用覆盖率与拒答统计（M8-3-4、M8-5-3）。
 *
 * 分母是「基于知识库的有效回答」（msg_type 1/2 且结果码 Success），
 * 不是全部消息——把数据查询和澄清也塞进分母会让覆盖率失去意义。
 */
@Service
public class CitationStatsService {

    private final AiMessageMapper messageMapper;

    public CitationStatsService(AiMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public Map<String, Object> coverage() {
        long effective = messageMapper.countEffectiveAnswers();
        long cited = messageMapper.countCitedAnswers();
        long refusals = messageMapper.countRefusals();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("effectiveAnswers", effective);
        stats.put("citedAnswers", cited);
        stats.put("coverage", rate(cited, effective));
        stats.put("refusalCount", refusals);
        stats.put("refusalRate", rate(refusals, effective + refusals));
        stats.put("note", "覆盖率分母为知识类有效回答，拒答单独计数，不混入分母");
        return stats;
    }

    private double rate(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round(part * 10000.0 / total) / 10000.0;
    }
}
