package com.zufe.procurement.m9.query.service;

import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m9.query.entity.AiIntent;
import com.zufe.procurement.m9.query.support.IntentLexicon;
import com.zufe.procurement.m9.query.support.QuestionSimilarity;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 意图分类（M9-1-1、M9-1-2）。
 *
 * 打分 = 词表命中权重 + 实体类型先验 + 2×与示例问句的最大 Dice 相似度。
 * 置信度 = 最高分 ÷ 全部分数之和（「证据占比」），低于 ai_intent_confidence_threshold
 * 即转澄清并列出候选，不选一个最像的去执行——这是受控口径里最容易做错的一步。
 */
@Service
public class IntentClassifier {

    private final AiIntentCatalog catalog;

    public IntentClassifier(AiIntentCatalog catalog) {
        this.catalog = catalog;
    }

    public IntentMatch classify(String question, List<EntityNormalizer.Group> entityGroups) {
        List<AiIntent> intents = catalog.enabledIntents();
        List<Scored> scored = new ArrayList<>();
        for (AiIntent intent : intents) {
            double lexicon = IntentLexicon.score(question,
                    IntentLexicon.BY_INTENT.getOrDefault(intent.getIntentCode(), Map.of()));
            double dice = maxSampleDice(question, intent.getQuestionSamples());
            double prior = entityPrior(intent.getIntentCode(), entityGroups);
            scored.add(new Scored(intent, lexicon + 2 * dice + prior, dice, lexicon));
        }
        // 问句里同时出现供应商与交付词时，供应商交付意图再加权，避免与交付进度平权
        if (containsAny(question, IntentLexicon.SUPPLIER_HINTS) && question.matches(".*(交付|交货|延期|延迟).*")) {
            for (Scored item : scored) {
                if ("supplier_delivery".equals(item.intent.getIntentCode())) {
                    item.score += 1.0;
                }
            }
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());

        IntentMatch match = new IntentMatch();
        double total = scored.stream().mapToDouble(s -> s.score).sum();
        Scored top = scored.isEmpty() ? null : scored.get(0);
        if (top == null || top.score <= 0) {
            match.setConfidence(BigDecimal.ZERO);
            match.setCandidates(List.of());
            return match;
        }
        match.setIntent(top.intent);
        match.setConfidence(BigDecimal.valueOf(total <= 0 ? 0 : top.score / total)
                .setScale(4, RoundingMode.HALF_UP));
        match.setLexiconScore(top.lexicon);
        List<Candidate> candidates = new ArrayList<>();
        for (Scored item : scored) {
            if (item.score <= 0) {
                continue;
            }
            Candidate candidate = new Candidate();
            candidate.setIntentCode(item.intent.getIntentCode());
            candidate.setIntentName(item.intent.getIntentName());
            candidate.setScore(BigDecimal.valueOf(item.score).setScale(4, RoundingMode.HALF_UP));
            candidate.setConfidence(BigDecimal.valueOf(total <= 0 ? 0 : item.score / total)
                    .setScale(4, RoundingMode.HALF_UP));
            candidate.setDice(BigDecimal.valueOf(item.dice).setScale(4, RoundingMode.HALF_UP));
            candidates.add(candidate);
        }
        match.setCandidates(candidates);
        return match;
    }

    private double maxSampleDice(String question, String samples) {
        if (samples == null || samples.isBlank()) {
            return 0;
        }
        double max = 0;
        for (String sample : samples.split("\\|")) {
            if (sample.isBlank()) {
                continue;
            }
            max = Math.max(max, QuestionSimilarity.dice(question, sample));
        }
        return max;
    }

    private double entityPrior(String intentCode, List<EntityNormalizer.Group> groups) {
        double total = 0;
        for (EntityNormalizer.Group group : groups) {
            Map<String, Double> prior = IntentLexicon.ENTITY_PRIOR.getOrDefault(group.getType(), Map.of());
            total += prior.getOrDefault(intentCode, 0.0);
        }
        return total;
    }

    private boolean containsAny(String question, List<String> words) {
        for (String word : words) {
            if (question.contains(word)) {
                return true;
            }
        }
        return false;
    }

    @Data
    public static class IntentMatch {
        private AiIntent intent;
        private BigDecimal confidence = BigDecimal.ZERO;
        private List<Candidate> candidates = new ArrayList<>();
        /**
         * 命中意图的专用词表得分。
         * 0 表示问句里一个该领域的词都没有，这个意图只是靠示例相似度或泛词「多少/几个」蹭上来的。
         * 澄清分支用它区分「确实在问这一类，只是没说清对象」和「根本不是这类问题」。
         */
        private double lexiconScore;
    }

    @Data
    public static class Candidate {
        private String intentCode;
        private String intentName;
        private BigDecimal score;
        private BigDecimal confidence;
        private BigDecimal dice;
        /** 澄清选项展示用 */
        private List<ChatVO.Option> asOptions;
    }

    private static class Scored {
        private final AiIntent intent;
        private double score;
        private final double dice;
        /** 该意图专用词表命中的词数得分：>0 说明问句里确实出现了这个意图的领域词 */
        private final double lexicon;

        Scored(AiIntent intent, double score, double dice) {
            this(intent, score, dice, 0);
        }

        Scored(AiIntent intent, double score, double dice, double lexicon) {
            this.intent = intent;
            this.score = score;
            this.dice = dice;
            this.lexicon = lexicon;
        }
    }
}
