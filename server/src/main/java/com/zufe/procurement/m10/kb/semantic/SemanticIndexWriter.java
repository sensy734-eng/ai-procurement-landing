package com.zufe.procurement.m10.kb.semantic;

import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbSemanticVector;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbSemanticVectorMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Separate Spring proxy: network work completes before this short transaction starts. */
@Service
public class SemanticIndexWriter {
    private final KbChunkMapper chunks;
    private final KbSemanticVectorMapper vectors;
    public SemanticIndexWriter(KbChunkMapper chunks, KbSemanticVectorMapper vectors) {
        this.chunks = chunks; this.vectors = vectors;
    }
    @Transactional(rollbackFor = Exception.class)
    public void write(List<KbSemanticVector> batch) {
        for (KbSemanticVector vector : batch) {
            KbChunk current = chunks.selectForUpdate(vector.getChunkId());
            if (current == null || !vector.getContentHash().equals(SemanticText.hash(current.getChunkText()))) {
                throw new IllegalStateException("embedding_source_changed");
            }
            vectors.upsert(vector);
        }
    }
}
