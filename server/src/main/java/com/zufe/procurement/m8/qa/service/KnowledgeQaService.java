package com.zufe.procurement.m8.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbDocumentMapper;
import com.zufe.procurement.m10.kb.service.KbRetrievalService;
import com.zufe.procurement.m10.kb.support.TextTokens;
import com.zufe.procurement.m11.param.entity.SysParam;
import com.zufe.procurement.m11.param.mapper.SysParamMapper;
import com.zufe.procurement.m7.assistant.port.AiKnowledgePort;
import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m7.assistant.vo.KnowledgeAnswer;
import com.zufe.procurement.m8.qa.support.MenuHintResolver;
import com.zufe.procurement.m8.qa.support.StepExtractor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 制度与教程问答（M8）。
 *
 * 组织方式：
 * 1. 复用 M10 的双路检索（M8-1-1、M8-1-2），两路结果都原样返回给前端做对照（M8-1-3）；
 * 2. 用 Reciprocal Rank Fusion 融合两路排名取答案依据——两路分数量纲不同，
 *    直接比大小没有意义，按名次融合才可比且无需调参；
 * 3. 条款型问句直接引用原文，不改写（M8-1-5）；教程类按后端规则拆步骤（M8-2-2/3）；
 * 4. 同一制度多版本时取现行有效版本，并提示还有哪些版本（M8-4-1/2）；
 * 5. 依据不足明确拒答，不拿相关度低的片段凑答案（M8-5-1/2）。
 */
@Service
public class KnowledgeQaService implements AiKnowledgePort {

    /** RRF 常数，取检索领域惯用的 60 */
    private static final int RRF_K = 60;
    private static final String PARAM_TOPK = "kb_recall_topk";
    private static final String PARAM_THRESHOLD = "kb_score_threshold";
    private static final Pattern CLAUSE_NO = Pattern.compile("第\\s*[一二三四五六七八九十百零0-9]+\\s*条");
    private static final Pattern CLAUSE_ASK = Pattern.compile(".*(几天|多久|多少天|几个工作日|期限|规定|是否允许|可以吗).*");

    private final KbRetrievalService retrievalService;
    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final SysParamMapper sysParamMapper;
    private final CitationValidator citationValidator;
    private final StepExtractor stepExtractor;
    private final MenuHintResolver menuHintResolver;

    public KnowledgeQaService(KbRetrievalService retrievalService, KbChunkMapper chunkMapper,
                              KbDocumentMapper documentMapper, SysParamMapper sysParamMapper,
                              CitationValidator citationValidator, StepExtractor stepExtractor,
                              MenuHintResolver menuHintResolver) {
        this.retrievalService = retrievalService;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.sysParamMapper = sysParamMapper;
        this.citationValidator = citationValidator;
        this.stepExtractor = stepExtractor;
        this.menuHintResolver = menuHintResolver;
    }

    @Override
    public KnowledgeAnswer answer(String question, int route) {
        KnowledgeAnswer answer = new KnowledgeAnswer();
        answer.setNotices(new ArrayList<>());
        int topK = intParam(PARAM_TOPK, 5);
        BigDecimal threshold = decimalParam(PARAM_THRESHOLD, new BigDecimal("0.35"));

        KbRetrievalService.RetrievalResult retrieval = retrievalService.search(question, topK);
        answer.setDualPath(buildDualPath(retrieval));

        Set<Long> chunkIds = new LinkedHashSet<>();
        for (KbRetrievalService.Hit hit : retrieval.getBm25()) {
            chunkIds.add(hit.getChunkId());
        }
        for (KbRetrievalService.Hit hit : retrieval.getVector()) {
            chunkIds.add(hit.getChunkId());
        }
        if (chunkIds.isEmpty()) {
            return refuse(answer, "知识库里没有可检索的文档，或倒排索引与向量尚未生成。",
                    "请先在知识库页面重建索引，或补充制度与操作手册文档。");
        }
        Map<Long, KbChunk> chunks = new HashMap<>();
        for (KbChunk chunk : chunkMapper.selectBatchIds(chunkIds)) {
            chunks.put(chunk.getId(), chunk);
        }
        Map<Long, KbDocument> docs = new HashMap<>();
        Set<Long> docIds = new LinkedHashSet<>();
        for (KbChunk chunk : chunks.values()) {
            docIds.add(chunk.getDocId());
        }
        if (!docIds.isEmpty()) {
            for (KbDocument doc : documentMapper.selectBatchIds(docIds)) {
                docs.put(doc.getId(), doc);
            }
        }
        List<Candidate> candidates = fuse(retrieval, chunks, docs);
        if (candidates.isEmpty()) {
            return refuse(answer, "检索没有返回可用片段。", "请补充知识库文档或调整问法。");
        }

        VersionResolution resolution = resolveVersions(candidates);
        candidates = resolution.used;
        answer.setVersionConflict(resolution.conflict);

        Candidate best = candidates.get(0);
        String bestText = text(best.chunk);

        // ---------- 拒答判定 M8-5-1 ----------
        double vectorTop = retrieval.getVector().isEmpty() ? 0
                : retrieval.getVector().get(0).getScore().doubleValue();
        answer.setTopScore(BigDecimal.valueOf(vectorTop).setScale(4, RoundingMode.HALF_UP));
        int termHits = countTermHits(question, bestText);
        if (vectorTop < threshold.doubleValue() && termHits < 2) {
            return refuse(answer, "知识库里没有找到足以支撑回答的依据（最高相似度 "
                            + answer.getTopScore().toPlainString() + " 低于阈值 " + threshold.toPlainString()
                            + "，词元命中 " + termHits + " 个）。",
                    "建议人工确认，或先在知识库补充相关制度条款与操作说明。");
        }

        // ---------- 引用 M8-3-1 / M8-3-3 ----------
        List<ChatVO.Citation> refs = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (refs.size() >= 3) {
                break;
            }
            CitationValidator.Check check = citationValidator.verify(candidate.chunk.getDocId(),
                    candidate.chunk.getId());
            if (!check.getValid()) {
                answer.getNotices().add("已剔除一条无效引用：" + check.getReason());
                continue;
            }
            ChatVO.Citation citation = new ChatVO.Citation();
            citation.setDocId(candidate.chunk.getDocId());
            citation.setDocName(candidate.doc == null ? null : candidate.doc.getDocName());
            citation.setDocVersion(candidate.doc == null ? null : candidate.doc.getVersion());
            citation.setEffectiveDate(candidate.doc == null || candidate.doc.getEffectiveDate() == null
                    ? null : candidate.doc.getEffectiveDate().toString());
            citation.setSectionNo(candidate.chunk.getSectionNo());
            citation.setSectionTitle(candidate.chunk.getSectionTitle());
            citation.setChunkId(candidate.chunk.getId());
            citation.setSnippet(snippet(bestTextOf(candidate), 120));
            citation.setScore(BigDecimal.valueOf(candidate.bestScore).setScale(4, RoundingMode.HALF_UP));
            citation.setPath(candidate.pathLabel());
            citation.setValid(Boolean.TRUE);
            refs.add(citation);
        }
        if (refs.isEmpty()) {
            return refuse(answer, "检索到的片段均未通过引用有效性校验。", "请检查文档是否已被删除或停用。");
        }
        answer.setRefs(refs);

        // ---------- 答案组织 M8-1-4 / M8-1-5 / M8-2-2 / M8-2-3 ----------
        if (route == 2) {
            List<String> steps = stepExtractor.extract(bestText);
            answer.setStepCount(steps.size());
            answer.setAnswer(composeTutorial(best, steps));
        } else {
            answer.setAnswer(composeKnowledge(question, best, bestText));
        }
        answer.getNotices().add("稀疏路首位与向量路首位"
                + (sameTop(retrieval) ? "一致" : "不一致") + "，两路结果见对照区");
        answer.setResultCode("Success");
        return answer;
    }

    private String composeKnowledge(String question, Candidate best, String text) {
        StringBuilder sb = new StringBuilder();
        boolean clause = CLAUSE_NO.matcher(question).find()
                || (CLAUSE_ASK.matcher(question).matches() && text.startsWith("第"));
        String docName = best.doc == null ? "知识库文档" : best.doc.getDocName();
        if (clause) {
            sb.append("《").append(docName).append("》").append(section(best)).append(" 原文如下（未改写）：\n");
            sb.append(limit(text, 400));
        } else {
            sb.append("根据《").append(docName).append("》").append(section(best)).append("：\n");
            sb.append(limit(firstSentences(text, 2), 240));
            sb.append("\n以上为检索片段的摘录，完整条款见引用区原文。");
        }
        return sb.toString();
    }

    private String composeTutorial(Candidate best, List<String> steps) {
        String docName = best.doc == null ? "知识库文档" : best.doc.getDocName();
        StringBuilder sb = new StringBuilder();
        sb.append("《").append(docName).append("》").append(section(best)).append(" 的操作步骤如下：\n");
        if (steps.isEmpty()) {
            sb.append(limit(firstSentences(text(best.chunk), 3), 300));
        } else {
            for (int i = 0; i < steps.size(); i++) {
                sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }
        String[] hint = menuHintResolver.resolve(text(best.chunk));
        if (hint != null) {
            sb.append("\n对应页面：").append(hint[1]).append("（").append(hint[0]).append("）");
            if (best.doc != null && best.doc.getVersion() != null) {
                sb.append("，适用于文档版本 V").append(best.doc.getVersion());
            }
            sb.append("。");
        }
        return sb.toString().trim();
    }

    private KnowledgeAnswer refuse(KnowledgeAnswer answer, String reason, String suggestion) {
        answer.setResultCode("NoData");
        answer.setRefusalReason(reason + " " + suggestion);
        answer.setAnswer("抱歉，这个问题我暂时无法给出有依据的回答。" + reason + suggestion);
        answer.setRefs(new ArrayList<>());
        if (answer.getNotices() == null) {
            answer.setNotices(new ArrayList<>());
        }
        return answer;
    }

    // ---------------- 融合与版本 ----------------

    private List<Candidate> fuse(KbRetrievalService.RetrievalResult retrieval, Map<Long, KbChunk> chunks,
                                 Map<Long, KbDocument> docs) {
        Map<Long, Candidate> byChunk = new LinkedHashMap<>();
        for (KbRetrievalService.Hit hit : retrieval.getBm25()) {
            Candidate candidate = byChunk.computeIfAbsent(hit.getChunkId(), id -> newCandidate(id, chunks, docs));
            if (candidate == null) {
                byChunk.remove(hit.getChunkId());
                continue;
            }
            candidate.fused += 1.0 / (RRF_K + hit.getRank());
            candidate.bm25Rank = hit.getRank();
            candidate.bestScore = Math.max(candidate.bestScore, hit.getScore().doubleValue());
        }
        for (KbRetrievalService.Hit hit : retrieval.getVector()) {
            Candidate candidate = byChunk.computeIfAbsent(hit.getChunkId(), id -> newCandidate(id, chunks, docs));
            if (candidate == null) {
                byChunk.remove(hit.getChunkId());
                continue;
            }
            candidate.fused += 1.0 / (RRF_K + hit.getRank());
            candidate.vectorRank = hit.getRank();
            candidate.bestScore = Math.max(candidate.bestScore, hit.getScore().doubleValue());
        }
        List<Candidate> list = new ArrayList<>();
        for (Candidate candidate : byChunk.values()) {
            if (candidate != null && candidate.chunk != null) {
                list.add(candidate);
            }
        }
        list.sort((a, b) -> Double.compare(b.fused, a.fused));
        return list;
    }

    private Candidate newCandidate(Long chunkId, Map<Long, KbChunk> chunks, Map<Long, KbDocument> docs) {
        KbChunk chunk = chunks.get(chunkId);
        if (chunk == null) {
            return null;
        }
        Candidate candidate = new Candidate();
        candidate.chunk = chunk;
        candidate.doc = docs.get(chunk.getDocId());
        candidate.bm25Rank = 0;
        candidate.vectorRank = 0;
        return candidate;
    }

    /**
     * 版本优先（M8-4-1、M8-4-2）：同名文档多版本同时被召回时，
     * 取生效日期不晚于今天的最新版本；其余版本记入冲突提示。
     */
    private VersionResolution resolveVersions(List<Candidate> candidates) {
        Map<String, List<Candidate>> byName = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String name = candidate.doc == null ? "未命名文档" : candidate.doc.getDocName();
            byName.computeIfAbsent(name, key -> new ArrayList<>()).add(candidate);
        }
        VersionResolution resolution = new VersionResolution();
        LocalDate today = LocalDate.now();
        for (Map.Entry<String, List<Candidate>> entry : byName.entrySet()) {
            List<Candidate> group = entry.getValue();
            Set<Long> docIds = new LinkedHashSet<>();
            for (Candidate candidate : group) {
                docIds.add(candidate.chunk.getDocId());
            }
            if (docIds.size() <= 1) {
                resolution.used.addAll(group);
                continue;
            }
            Candidate preferred = null;
            for (Candidate candidate : group) {
                if (candidate.doc == null || candidate.doc.getEffectiveDate() == null) {
                    continue;
                }
                boolean effective = !candidate.doc.getEffectiveDate().isAfter(today);
                if (!effective) {
                    continue;
                }
                if (preferred == null || candidate.doc.getEffectiveDate()
                        .isAfter(preferred.doc.getEffectiveDate())) {
                    preferred = candidate;
                }
            }
            if (preferred == null) {
                for (Candidate candidate : group) {
                    if (preferred == null || (candidate.doc != null && candidate.doc.getEffectiveDate() != null
                            && candidate.doc.getEffectiveDate().isAfter(preferred.doc.getEffectiveDate()))) {
                        preferred = candidate;
                    }
                }
            }
            for (Candidate candidate : group) {
                if (candidate.chunk.getDocId().equals(preferred.chunk.getDocId())) {
                    resolution.used.add(candidate);
                }
            }
            ChatVO.VersionConflict conflict = new ChatVO.VersionConflict();
            conflict.setDocName(entry.getKey());
            conflict.setUsedVersion(preferred.doc == null ? null : preferred.doc.getVersion());
            conflict.setUsedEffectiveDate(preferred.doc == null || preferred.doc.getEffectiveDate() == null
                    ? null : preferred.doc.getEffectiveDate().toString());
            List<String> others = new ArrayList<>();
            for (Candidate candidate : group) {
                if (!candidate.chunk.getDocId().equals(preferred.chunk.getDocId()) && candidate.doc != null) {
                    String text = "V" + candidate.doc.getVersion() + "（生效 "
                            + (candidate.doc.getEffectiveDate() == null ? "未设置"
                            : candidate.doc.getEffectiveDate().toString()) + "）";
                    if (!others.contains(text)) {
                        others.add(text);
                    }
                }
            }
            conflict.setOtherVersions(others);
            conflict.setMessage("《" + entry.getKey() + "》存在 " + (others.size() + 1)
                    + " 个版本同时命中，已按现行有效版本 V" + conflict.getUsedVersion() + " 作答，"
                    + "其余版本：" + String.join("、", others));
            resolution.conflict = conflict;
            resolution.used.sort((a, b) -> Double.compare(b.fused, a.fused));
        }
        resolution.used.sort((a, b) -> Double.compare(b.fused, a.fused));
        return resolution;
    }

    // ---------------- 双路展示 ----------------

    private List<ChatVO.DualPathItem> buildDualPath(KbRetrievalService.RetrievalResult retrieval) {
        List<ChatVO.DualPathItem> items = new ArrayList<>();
        for (KbRetrievalService.Hit hit : retrieval.getBm25()) {
            items.add(dualPath("sparse", hit));
        }
        for (KbRetrievalService.Hit hit : retrieval.getVector()) {
            items.add(dualPath("vector", hit));
        }
        return items;
    }

    private ChatVO.DualPathItem dualPath(String path, KbRetrievalService.Hit hit) {
        ChatVO.DualPathItem item = new ChatVO.DualPathItem();
        item.setPath(path);
        item.setRank(hit.getRank());
        item.setChunkId(hit.getChunkId());
        item.setDocId(hit.getDocId());
        item.setDocName(hit.getDocName());
        item.setSectionNo(hit.getSectionNo());
        item.setScore(hit.getScore());
        item.setSnippet(hit.getSnippet());
        return item;
    }

    private boolean sameTop(KbRetrievalService.RetrievalResult retrieval) {
        if (retrieval.getBm25().isEmpty() || retrieval.getVector().isEmpty()) {
            return false;
        }
        return retrieval.getBm25().get(0).getChunkId()
                .equals(retrieval.getVector().get(0).getChunkId());
    }

    /** 问句词元在给定切块中的命中个数（拒答判定的辅助信号） */
    private int countTermHits(String question, String text) {
        if (text == null) {
            return 0;
        }
        Set<String> terms = new LinkedHashSet<>(TextTokens.tokenize(question));
        int hits = 0;
        for (String term : terms) {
            if (term.length() >= 2 && text.contains(term)) {
                hits++;
            }
        }
        return hits;
    }

    // ---------------- 文本处理 ----------------

    private String text(KbChunk chunk) {
        return chunk == null || chunk.getChunkText() == null ? "" : chunk.getChunkText().trim();
    }

    private String bestTextOf(Candidate candidate) {
        return text(candidate.chunk);
    }

    private String section(Candidate candidate) {
        if (candidate.chunk.getSectionNo() == null) {
            return "";
        }
        return candidate.chunk.getSectionNo()
                + (candidate.chunk.getSectionTitle() == null ? "" : " " + candidate.chunk.getSectionTitle());
    }

    private String firstSentences(String text, int count) {
        String[] parts = text.split("(?<=[。！？])");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && i < count; i++) {
            sb.append(parts[i]);
        }
        return sb.length() == 0 ? text : sb.toString();
    }

    private String limit(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String snippet(String text, int max) {
        String flat = text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private int intParam(String code, int fallback) {
        try {
            return Integer.parseInt(readParam(code, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private BigDecimal decimalParam(String code, BigDecimal fallback) {
        try {
            return new BigDecimal(readParam(code, fallback.toPlainString()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String readParam(String code, String fallback) {
        SysParam param = sysParamMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamCode, code)
                .last("LIMIT 1"));
        return param == null || param.getParamValue() == null ? fallback : param.getParamValue();
    }

    /** 融合候选：同一块只保留一条，记录两路名次用于标注来源 */
    @Data
    private static class Candidate {
        private KbChunk chunk;
        private KbDocument doc;
        private double fused;
        private double bestScore;
        private int bm25Rank;
        private int vectorRank;

        String pathLabel() {
            if (bm25Rank > 0 && vectorRank > 0) {
                return "both";
            }
            return bm25Rank > 0 ? "sparse" : "vector";
        }
    }

    @Data
    private static class VersionResolution {
        private List<Candidate> used = new ArrayList<>();
        private ChatVO.VersionConflict conflict;
    }
}
