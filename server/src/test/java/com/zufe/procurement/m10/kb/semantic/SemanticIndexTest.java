package com.zufe.procurement.m10.kb.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.m10.kb.entity.*;
import com.zufe.procurement.m10.kb.mapper.*;
import com.zufe.procurement.m10.kb.service.KbRetrievalService;
import com.zufe.procurement.m10.kb.support.HashingVectorizer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SemanticIndexTest {
    @Test void fingerprintExcludesCredentialsButIncludesEveryRepresentationSetting() {
        EmbeddingProperties p = new EmbeddingProperties(); String original = p.fingerprint();
        p.setApiKey("different-secret"); assertThat(p.fingerprint()).isEqualTo(original);
        p.setModel("other-model"); assertThat(p.fingerprint()).isNotEqualTo(original);
        p.setModel("text-embedding-v4"); p.setDimensions(512); assertThat(p.fingerprint()).isNotEqualTo(original);
        assertThat(SemanticText.hash("hello\r\nworld")).isEqualTo(SemanticText.hash("hello\nworld"));
    }

    @Test void rejectsStaleOrWrongDimensionWithoutSpendingQueryTokens() {
        EmbeddingProperties p = new EmbeddingProperties(); p.setDimensions(2);
        KbChunkMapper chunks = mock(KbChunkMapper.class);
        KbSemanticVectorMapper vectors = mock(KbSemanticVectorMapper.class);
        EmbeddingGateway gateway = mock(EmbeddingGateway.class);
        SemanticIndexService service = new SemanticIndexService(p, gateway, chunks, vectors,
                mock(SemanticIndexWriter.class), new ObjectMapper());
        KbChunk chunk = new KbChunk(); chunk.setId(1L); chunk.setChunkText("new content");
        when(chunks.selectBatchIds(anyCollection())).thenReturn(List.of(chunk));
        KbSemanticVector vector = new KbSemanticVector(); vector.setChunkId(1L);
        vector.setModel(p.getModel()); vector.setDimensions(2); vector.setPreprocessingVersion(SemanticText.VERSION);
        vector.setContentHash(SemanticText.hash("old content")); vector.setVectorJson("[1,0]");
        when(vectors.byConfig(anyString())).thenReturn(List.of(vector));
        assertThatThrownBy(() -> service.scores("query", List.of(1L))).hasMessageContaining("stale");
        vector.setContentHash(SemanticText.hash(chunk.getChunkText())); vector.setDimensions(3);
        assertThatThrownBy(() -> service.scores("query", List.of(1L))).hasMessageContaining("stale");
        vector.setDimensions(2); vector.setVectorJson("[1]");
        assertThatThrownBy(() -> service.scores("query", List.of(1L))).hasMessageContaining("invalid");
        verifyNoInteractions(gateway);
    }

    @Test void writerRejectsChangedContentBeforePersisting() {
        KbChunkMapper chunks = mock(KbChunkMapper.class); KbSemanticVectorMapper vectors = mock(KbSemanticVectorMapper.class);
        KbChunk current = new KbChunk(); current.setId(1L); current.setChunkText("changed");
        when(chunks.selectForUpdate(1L)).thenReturn(current);
        KbSemanticVector pending = new KbSemanticVector(); pending.setChunkId(1L); pending.setContentHash(SemanticText.hash("original"));
        assertThatThrownBy(() -> new SemanticIndexWriter(chunks, vectors).write(List.of(pending)))
                .hasMessage("embedding_source_changed");
        verifyNoInteractions(vectors);
    }

    @Test void onlineFailureReturnsWholeHashBaselineButExperimentFails() {
        KbChunkMapper chunks = mock(KbChunkMapper.class); KbSparseIndexMapper sparse = mock(KbSparseIndexMapper.class);
        KbDocumentMapper docs = mock(KbDocumentMapper.class); SemanticIndexService semantic = mock(SemanticIndexService.class);
        when(chunks.selectEnabledChunkIds()).thenReturn(List.of(1L));
        when(chunks.selectEnabledWithEmbedding()).thenReturn(List.of());
        when(sparse.selectList(any())).thenReturn(List.of());
        when(semantic.scoresWithUsage(anyString(), anyList())).thenThrow(new IllegalStateException("index stale"));
        KbRetrievalService service = new KbRetrievalService(chunks, sparse, docs, new HashingVectorizer(), new EmbeddingProperties(), semantic);
        KbRetrievalService.RetrievalResult result = service.search("query", 5, "bm25_semantic", false);
        assertThat(result.getActualStrategy()).isEqualTo("bm25_hash");
        assertThat(result.getDegradedReason()).isNotBlank();
        assertThatThrownBy(() -> service.search("query", 5, "bm25_semantic", true))
                .isInstanceOf(com.zufe.procurement.common.BizException.class);
    }
}
