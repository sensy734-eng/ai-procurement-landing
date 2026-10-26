package com.zufe.procurement.m10.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import com.zufe.procurement.m10.kb.entity.KbSparseIndex;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbDocumentMapper;
import com.zufe.procurement.m10.kb.mapper.KbSparseIndexMapper;
import com.zufe.procurement.m10.kb.support.HashingVectorizer;
import com.zufe.procurement.m10.kb.support.TextTokens;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 双路检索（M10-5-1 检索测试台的两条路；M8 问答在线段复用）。
 *
 * 口径（规划书 3.4）：
 * - 稀疏路：中文字符二元组分词 + Okapi BM25（k1=1.5、b=0.75），
 *   倒排取词元 postings，idf 按启用切块总数计算，与建索引分词完全一致；
 * - 向量路：切块向量全量候选余弦相似度（不做稀疏预滤），保证两路在相同候选集
 *   条件下对照，不预设优劣；演示规模下全量扫描足够，生产环境换 ANN 不影响口径；
 * - 两路都只召回启用文档的切块（停用/失效不参与，M10-4-3）。
 */
@Service
public class KbRetrievalService {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final KbChunkMapper chunkMapper;
    private final KbSparseIndexMapper sparseIndexMapper;
    private final KbDocumentMapper documentMapper;
    private final HashingVectorizer vectorizer;

    public KbRetrievalService(KbChunkMapper chunkMapper,
                              KbSparseIndexMapper sparseIndexMapper,
                              KbDocumentMapper documentMapper,
                              HashingVectorizer vectorizer) {
        this.chunkMapper = chunkMapper;
        this.sparseIndexMapper = sparseIndexMapper;
        this.documentMapper = documentMapper;
        this.vectorizer = vectorizer;
    }

    /** 一次问句并行两路召回，各路按得分降序取 Top-K */
    public RetrievalResult search(String question, int topK) {
        List<Long> enabledIds = chunkMapper.selectEnabledChunkIds();
        RetrievalResult result = new RetrievalResult();
        if (enabledIds.isEmpty()) {
            result.setBm25(List.of());
            result.setVector(List.of());
            return result;
        }
        Set<Long> enabledSet = new HashSet<>(enabledIds);
        int n = enabledIds.size();
        Double avg = chunkMapper.avgEnabledCharCount();
        double avgdl = avg == null || avg <= 0 ? 1.0 : avg;

        // 稀疏路：汇总候选块，再批量取块元数据算 dl（打分需要每块真实长度）
        List<String> tokens = TextTokens.tokenize(question);
        Map<Long, List<double[]>> termScores = new HashMap<>(); // chunkId -> [idf, tf] 列表
        for (String token : tokens) {
            List<KbSparseIndex> postings = sparseIndexMapper.selectList(new LambdaQueryWrapper<KbSparseIndex>()
                    .eq(KbSparseIndex::getToken, token)
                    .eq(KbSparseIndex::getIsDeleted, 0));
            List<KbSparseIndex> enabled = new ArrayList<>();
            for (KbSparseIndex posting : postings) {
                if (enabledSet.contains(posting.getChunkId())) {
                    enabled.add(posting);
                }
            }
            if (enabled.isEmpty()) {
                continue;
            }
            int df = enabled.size();
            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            for (KbSparseIndex posting : enabled) {
                double tf = posting.getTf() == null ? 0 : posting.getTf();
                termScores.computeIfAbsent(posting.getChunkId(), key -> new ArrayList<>())
                        .add(new double[]{idf, tf});
            }
        }
        Map<Long, KbChunk> chunkById = loadChunks(termScores.keySet());
        Map<Long, Double> bm25Scores = new HashMap<>();
        for (Map.Entry<Long, List<double[]>> entry : termScores.entrySet()) {
            KbChunk chunk = chunkById.get(entry.getKey());
            double dl = chunk == null || chunk.getCharCount() == null ? avgdl : chunk.getCharCount();
            double score = 0;
            for (double[] idfTf : entry.getValue()) {
                double tf = idfTf[1];
                score += idfTf[0] * tf * (K1 + 1) / (tf + K1 * (1 - B + B * dl / avgdl));
            }
            bm25Scores.put(entry.getKey(), score);
        }

        // 向量路：全量候选余弦（不做稀疏预滤，保证对照条件一致）
        Map<Long, Double> vectorScores = new HashMap<>();
        byte[] queryVector = vectorizer.vectorize(question);
        List<KbChunk> vectorCandidates = chunkMapper.selectEnabledWithEmbedding();
        for (KbChunk chunk : vectorCandidates) {
            double score = HashingVectorizer.cosine(queryVector, chunk.getEmbedding());
            if (score > 0) {
                vectorScores.put(chunk.getId(), score);
            }
        }

        chunkById.putAll(loadChunks(vectorScores.keySet()));
        Map<Long, String> docNames = loadDocNames(chunkById.values());
        result.setBm25(toHits(bm25Scores, topK, chunkById, docNames));
        result.setVector(toHits(vectorScores, topK, chunkById, docNames));
        return result;
    }

    private Map<Long, KbChunk> loadChunks(Set<Long> ids) {
        Map<Long, KbChunk> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (KbChunk chunk : chunkMapper.selectBatchIds(ids)) {
            result.put(chunk.getId(), chunk);
        }
        return result;
    }

    private Map<Long, String> loadDocNames(Iterable<KbChunk> chunks) {
        Set<Long> docIds = new HashSet<>();
        for (KbChunk chunk : chunks) {
            docIds.add(chunk.getDocId());
        }
        Map<Long, String> names = new HashMap<>();
        if (docIds.isEmpty()) {
            return names;
        }
        for (KbDocument doc : documentMapper.selectBatchIds(docIds)) {
            names.put(doc.getId(), doc.getDocName());
        }
        return names;
    }

    private List<Hit> toHits(Map<Long, Double> scores, int topK,
                             Map<Long, KbChunk> chunkById, Map<Long, String> docNames) {
        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        List<Hit> hits = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Long, Double> entry : sorted) {
            if (rank > topK) {
                break;
            }
            KbChunk chunk = chunkById.get(entry.getKey());
            if (chunk == null) {
                continue;
            }
            Hit hit = new Hit();
            hit.setRank(rank++);
            hit.setChunkId(chunk.getId());
            hit.setDocId(chunk.getDocId());
            hit.setDocName(docNames.get(chunk.getDocId()));
            hit.setSectionNo(chunk.getSectionNo());
            hit.setSectionTitle(chunk.getSectionTitle());
            hit.setScore(BigDecimal.valueOf(entry.getValue()).setScale(4, RoundingMode.HALF_UP));
            hit.setSnippet(snippet(chunk.getChunkText()));
            hits.add(hit);
        }
        return hits;
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= 80 ? flat : flat.substring(0, 80) + "…";
    }

    @Data
    public static class RetrievalResult {
        private List<Hit> bm25;
        private List<Hit> vector;
    }

    @Data
    public static class Hit {
        private int rank;
        private Long chunkId;
        private Long docId;
        private String docName;
        private String sectionNo;
        private String sectionTitle;
        private BigDecimal score;
        private String snippet;
    }
}
