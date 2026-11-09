package com.zufe.procurement.m10.kb.service;

import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbDocumentMapper;
import com.zufe.procurement.m10.kb.semantic.EmbeddingProperties;
import com.zufe.procurement.m10.kb.semantic.SemanticText;
import com.zufe.procurement.m8.qa.service.LlmAnswerComposer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Read-only manifest of the complete serving corpus, including pre-existing documents. */
@Service
public class RetrievalManifestService {
    private final KbChunkMapper chunks;
    private final KbDocumentMapper documents;
    private final EmbeddingProperties config;
    private final com.zufe.procurement.llm.LlmProperties llm;
    private final com.zufe.procurement.m10.kb.mapper.KbSparseIndexMapper sparse;
    private final com.zufe.procurement.m10.kb.support.HashingVectorizer hashing;
    public RetrievalManifestService(KbChunkMapper chunks, KbDocumentMapper documents, EmbeddingProperties config,
                                    com.zufe.procurement.llm.LlmProperties llm,
                                    com.zufe.procurement.m10.kb.mapper.KbSparseIndexMapper sparse,
                                    com.zufe.procurement.m10.kb.support.HashingVectorizer hashing) {
        this.chunks = chunks; this.documents = documents; this.config = config;
        this.llm = llm;
        this.sparse = sparse; this.hashing = hashing;
    }
    @Transactional(readOnly = true)
    public Map<String, Object> manifest() {
        List<Long> ids = chunks.selectEnabledChunkIds();
        List<KbChunk> source = ids.isEmpty() ? List.of() : chunks.selectBatchIds(ids);
        Map<Long, Map<String, Integer>> terms = new HashMap<>();
        if (!ids.isEmpty()) {
            var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.zufe.procurement.m10.kb.entity.KbSparseIndex>()
                    .in(com.zufe.procurement.m10.kb.entity.KbSparseIndex::getChunkId, ids)
                    .eq(com.zufe.procurement.m10.kb.entity.KbSparseIndex::getIsDeleted, 0);
            sparse.selectList(wrapper).forEach(t -> terms.computeIfAbsent(t.getChunkId(), k -> new HashMap<>()).put(t.getToken(), t.getTf()));
        }
        Set<Long> docIds = new TreeSet<>();
        source.forEach(c -> docIds.add(c.getDocId()));
        Map<Long, KbDocument> docs = new HashMap<>();
        if (!docIds.isEmpty()) documents.selectBatchIds(docIds).forEach(d -> docs.put(d.getId(), d));
        List<Map<String, Object>> entries = new ArrayList<>();
        StringBuilder canonical = new StringBuilder();
        source.stream().sorted(Comparator.comparing(KbChunk::getId)).forEach(c -> {
            KbDocument d = docs.get(c.getDocId());
            String hash = SemanticText.hash(c.getChunkText());
            boolean baselineReady = java.util.Arrays.equals(c.getEmbedding(), hashing.vectorize(c.getChunkText()))
                    && KbIndexService.termFrequency(c.getChunkText()).equals(terms.getOrDefault(c.getId(), Map.of()));
            String metadata = c.getDocId() + "|" + d.getDocName() + "|" + d.getVersion() + "|"
                    + d.getEffectiveDate() + "|" + d.getExpireDate() + "|" + c.getSectionNo() + "|" + c.getSectionTitle();
            entries.add(Map.of("chunkId", c.getId(), "docId", c.getDocId(), "contentHash", hash,
                    "docVersion", d.getVersion() == null ? "" : d.getVersion(), "metadataHash", SemanticText.hash(metadata),
                    "baselineReady", baselineReady));
            canonical.append(c.getId()).append('|').append(hash).append('|').append(metadata).append('\n');
        });
        return Map.of("corpusHash", SemanticText.hash(canonical.toString()), "chunks", entries,
                "chunkCount", entries.size(), "documentCount", docIds.size(),
                "embedding", Map.of("model", config.getModel(), "dimensions", config.getDimensions(),
                        "fingerprint", config.fingerprint(), "preprocessing", SemanticText.VERSION,
                        "queryTimeoutMs", config.getQueryTimeoutMs()),
                "defaultStrategy", config.getStrategy(), "promptVersion", LlmAnswerComposer.PROMPT_VERSION,
                "chatModel", Map.of("model", llm.getModel(), "enabled", llm.isEnabled(), "answerEnabled", llm.isAnswerEnabled()),
                "serviceCommit", System.getenv().getOrDefault("BUILD_COMMIT", "unknown"),
                "databaseGuard", "read_only_manifest; compare before/after each experiment");
    }
}
