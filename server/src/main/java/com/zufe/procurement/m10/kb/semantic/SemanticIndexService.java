package com.zufe.procurement.m10.kb.semantic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbSemanticVector;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbSemanticVectorMapper;
import com.zufe.procurement.m10.kb.service.KbIndexService.IndexBuildResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SemanticIndexService {
    private final EmbeddingProperties properties;
    private final EmbeddingGateway gateway;
    private final KbChunkMapper chunks;
    private final KbSemanticVectorMapper vectors;
    private final SemanticIndexWriter writer;
    private final ObjectMapper json;
    public SemanticIndexService(EmbeddingProperties properties, EmbeddingGateway gateway, KbChunkMapper chunks,
                                KbSemanticVectorMapper vectors, SemanticIndexWriter writer, ObjectMapper json) {
        this.properties = properties; this.gateway = gateway; this.chunks = chunks;
        this.vectors = vectors; this.writer = writer; this.json = json;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IndexBuildResult rebuild(Long docId) {
        long start = System.currentTimeMillis();
        List<KbChunk> source = chunks.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(docId != null, KbChunk::getDocId, docId).eq(KbChunk::getIsDeleted, 0).orderByAsc(KbChunk::getId));
        String fingerprint = properties.fingerprint();
        for (int offset = 0; offset < source.size(); offset += 10) {
            List<KbChunk> batch = source.subList(offset, Math.min(offset + 10, source.size()));
            List<double[]> embedded = gateway.embed(batch.stream().map(KbChunk::getChunkText).toList(), false);
            List<KbSemanticVector> pending = new ArrayList<>();
            for (int i = 0; i < batch.size(); i++) {
                KbSemanticVector vector = new KbSemanticVector();
                vector.setChunkId(batch.get(i).getId()); vector.setConfigFingerprint(fingerprint);
                vector.setModel(properties.getModel()); vector.setDimensions(properties.getDimensions());
                vector.setPreprocessingVersion(SemanticText.VERSION);
                vector.setContentHash(SemanticText.hash(batch.get(i).getChunkText()));
                try { vector.setVectorJson(json.writeValueAsString(embedded.get(i))); }
                catch (Exception e) { throw new IllegalStateException("embedding_serialization_error"); }
                pending.add(vector);
            }
            writer.write(pending);
        }
        IndexBuildResult result = new IndexBuildResult();
        result.setDocCount((int) source.stream().map(KbChunk::getDocId).distinct().count());
        result.setChunkCount(source.size()); result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    public Map<Long, Double> scores(String question, List<Long> enabledIds) {
        return scoresWithUsage(question, enabledIds).scores();
    }
    public record ScoredResult(Map<Long, Double> scores, Long totalTokens) {}
    public ScoredResult scoresWithUsage(String question, List<Long> enabledIds) {
        List<KbChunk> source = chunks.selectBatchIds(enabledIds);
        Map<Long, KbSemanticVector> indexed = new HashMap<>();
        for (KbSemanticVector vector : vectors.byConfig(properties.fingerprint())) indexed.put(vector.getChunkId(), vector);
        Map<Long, double[]> ready = new LinkedHashMap<>();
        if (source.size() != enabledIds.size()) throw new IllegalStateException("semantic_index_incomplete");
        for (KbChunk chunk : source) {
            KbSemanticVector vector = indexed.get(chunk.getId());
            if (vector == null || !Objects.equals(vector.getDimensions(), properties.getDimensions())
                    || !properties.getModel().equals(vector.getModel())
                    || !SemanticText.VERSION.equals(vector.getPreprocessingVersion())
                    || !SemanticText.hash(chunk.getChunkText()).equals(vector.getContentHash())) {
                throw new IllegalStateException("semantic_index_incomplete_or_stale");
            }
            try {
                double[] values = json.readValue(vector.getVectorJson(), double[].class);
                if (values.length != properties.getDimensions()) throw new IllegalStateException("semantic_dimension_mismatch");
                SemanticText.cosine(values, values);
                ready.put(chunk.getId(), values);
            } catch (Exception e) { throw new IllegalStateException("semantic_index_invalid"); }
        }
        EmbeddingGateway.EmbeddingResult embedded = gateway.embedWithUsage(List.of(question), true);
        double[] query = embedded.vectors().get(0);
        Map<Long, Double> scores = new HashMap<>();
        ready.forEach((id, vector) -> scores.put(id, SemanticText.cosine(query, vector)));
        return new ScoredResult(scores, embedded.totalTokens());
    }
}
