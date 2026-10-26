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
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 索引构建（M10-3-2 稀疏索引、M10-3-3 增量更新、M10-3-4 一键重建）。
 *
 * 口径：
 * - 增量更新的单位是文档：文档解析/正文补录/切块合并后只重建该文档的倒排与向量，
 *   不动其他文档（M10-3-3）；
 * - 倒排索引与检索端共用 TextTokens 二元组分词，token 即词元、tf 为块内词频；
 * - 向量由本地确定性向量器产出（HashingVectorizer），索引重建时按当前实现全量刷新，
 *   vector_model 记录向量来源，便于将来切换嵌入模型后识别需重建的块。
 */
@Service
public class KbIndexService {

    private final KbChunkMapper chunkMapper;
    private final KbSparseIndexMapper sparseIndexMapper;
    private final KbDocumentMapper documentMapper;
    private final HashingVectorizer vectorizer;

    public KbIndexService(KbChunkMapper chunkMapper,
                          KbSparseIndexMapper sparseIndexMapper,
                          KbDocumentMapper documentMapper,
                          HashingVectorizer vectorizer) {
        this.chunkMapper = chunkMapper;
        this.sparseIndexMapper = sparseIndexMapper;
        this.documentMapper = documentMapper;
        this.vectorizer = vectorizer;
    }

    /** 重建单文档索引：删旧倒排 → 按当前切块重写 → 刷新向量 */
    @Transactional
    public IndexBuildResult rebuildDoc(Long docId) {
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, docId)
                .eq(KbChunk::getIsDeleted, 0)
                .orderByAsc(KbChunk::getChunkIndex));
        IndexBuildResult result = new IndexBuildResult();
        result.setDocCount(1);
        result.setChunkCount(chunks.size());
        for (KbChunk chunk : chunks) {
            sparseIndexMapper.deleteByChunkIdPhysical(chunk.getId());
            Map<String, Integer> tf = termFrequency(chunk.getChunkText());
            for (Map.Entry<String, Integer> entry : tf.entrySet()) {
                KbSparseIndex index = new KbSparseIndex();
                index.setChunkId(chunk.getId());
                index.setToken(entry.getKey());
                index.setTf(entry.getValue());
                index.setIsDeleted(0);
                sparseIndexMapper.insert(index);
                result.setTokenCount(result.getTokenCount() + 1);
            }
            chunk.setEmbedding(vectorizer.vectorize(chunk.getChunkText()));
            chunk.setVectorModel(HashingVectorizer.MODEL);
            chunk.setVectorDim(HashingVectorizer.DIM);
            chunkMapper.updateById(chunk);
        }
        return result;
    }

    /** 全量重建（M10-3-4）：遍历全部未删除文档，返回耗时与规模 */
    public IndexBuildResult rebuildAll() {
        long start = System.currentTimeMillis();
        List<KbDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getIsDeleted, 0));
        IndexBuildResult total = new IndexBuildResult();
        for (KbDocument doc : docs) {
            IndexBuildResult one = rebuildDoc(doc.getId());
            total.setDocCount(total.getDocCount() + one.getDocCount());
            total.setChunkCount(total.getChunkCount() + one.getChunkCount());
            total.setTokenCount(total.getTokenCount() + one.getTokenCount());
        }
        total.setElapsedMs(System.currentTimeMillis() - start);
        return total;
    }

    /** 块内词元词频（保持插入顺序，便于对账） */
    static Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> tf = new LinkedHashMap<>();
        for (String token : TextTokens.tokenize(text)) {
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    @Data
    public static class IndexBuildResult {
        private int docCount;
        private int chunkCount;
        private int tokenCount;
        private long elapsedMs;
    }
}
