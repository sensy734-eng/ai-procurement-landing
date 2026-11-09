package com.zufe.procurement.m8.qa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.llm.*;
import com.zufe.procurement.m10.kb.entity.*;
import com.zufe.procurement.m10.kb.mapper.*;
import com.zufe.procurement.m10.kb.service.KbRetrievalService;
import com.zufe.procurement.m11.param.mapper.SysParamMapper;
import com.zufe.procurement.m7.assistant.vo.KnowledgeAnswer;
import com.zufe.procurement.m8.qa.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConservativeQaTest {
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final LlmProperties props = new LlmProperties();
    private KnowledgeQaService qa;
    private KbRetrievalService retrieval;

    @BeforeEach
    void setup() {
        props.setEnabled(true);
        when(gateway.available()).thenReturn(true);
        retrieval = mock(KbRetrievalService.class);
        KbChunkMapper chunks = mock(KbChunkMapper.class);
        KbDocumentMapper docs = mock(KbDocumentMapper.class);
        KbChunk a = chunk(1L, "采购审批期限为30个工作日。");
        KbChunk b = chunk(2L, "采购归档期限为3个工作日。");
        when(chunks.selectBatchIds(anyCollection())).thenReturn(List.of(a, b));
        when(chunks.selectById(1L)).thenReturn(a);
        when(chunks.selectById(2L)).thenReturn(b);
        KbDocument doc = new KbDocument(); doc.setId(1L); doc.setDocName("演示制度");
        doc.setStatus(1); doc.setVersion("1.0");
        when(docs.selectBatchIds(anyCollection())).thenReturn(List.of(doc));
        when(docs.selectById(1L)).thenReturn(doc);
        KbRetrievalService.RetrievalResult result = new KbRetrievalService.RetrievalResult();
        result.setBm25(List.of(hit(1, 1L), hit(2, 2L))); result.setVector(result.getBm25());
        result.setConfigId("bm25_hash");
        when(retrieval.search(anyString(), anyInt())).thenReturn(result);
        LlmAnswerComposer composer = new LlmAnswerComposer(gateway, props, new ObjectMapper(), new AiChannelGuard(props));
        qa = new KnowledgeQaService(retrieval, chunks, docs, mock(SysParamMapper.class),
                new CitationValidator(chunks, docs), new StepExtractor(), mock(MenuHintResolver.class), composer, props);
    }
    private KbChunk chunk(Long id, String text) {
        KbChunk c = new KbChunk(); c.setId(id); c.setDocId(1L); c.setChunkText(text); return c;
    }
    private KbRetrievalService.Hit hit(int rank, Long id) {
        KbRetrievalService.Hit h = new KbRetrievalService.Hit(); h.setChunkId(id); h.setDocId(1L);
        h.setRank(rank); h.setScore(BigDecimal.ONE); return h;
    }
    private void response(String json) {
        when(gateway.chat(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(LlmResult.ok(json, 2, "simulated-test", 100, 30));
    }
    private KnowledgeAnswer ask() { return qa.answer("采购审批期限是多少", 1); }
    private void assertReferenceOnly(KnowledgeAnswer answer) {
        assertThat(answer.getAnswerMode()).isEqualTo("references_only");
        assertThat(answer.getResultCode()).isEqualTo("NoData");
        assertThat(answer.getRefs()).isNotEmpty();
        assertThat(answer.getAnswer()).doesNotContain("期限为3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1.5]", "[0]", "[3]", "[2147483648]", "[\"1\"]", "[1,1]", "[]", "null"})
    void invalidCitationsNeverBecomeGeneratedAnswers(String citations) {
        response("{\"answerable\":true,\"answer\":\"采购审批期限为30个工作日\",\"citations\":" + citations + "}");
        assertReferenceOnly(ask());
    }

    @Test void cannotBorrowNumbersFromUncitedPassage() {
        response("{\"answerable\":true,\"answer\":\"采购审批期限为3个工作日\",\"citations\":[1]}");
        assertReferenceOnly(ask());
    }
    @Test void validNumericAnswerIsGenerated() {
        response("{\"answerable\":true,\"answer\":\"采购审批期限为三十个工作日\",\"citations\":[1]}");
        KnowledgeAnswer answer = ask();
        assertThat(answer.getAnswerMode()).isEqualTo("generated");
        assertThat(answer.getEvidenceChunkIds()).containsExactly(1L);
    }
    @ParameterizedTest @ValueSource(strings = {"TIMEOUT", "RATE_LIMIT", "SERVER", "BAD_JSON"})
    void modelFailuresOnlyReturnReferences(String failure) {
        when(gateway.chat(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(LlmResult.fail(failure, 1, "simulated-test"));
        assertReferenceOnly(ask());
    }
    @Test void malformedJsonOnlyReturnsReferences() { response("not JSON"); assertReferenceOnly(ask()); }
    @Test void disabledModelOnlyReturnsReferences() { props.setEnabled(false); assertReferenceOnly(ask()); verify(gateway, never()).chat(anyString(), anyString(), anyInt(), anyInt()); }
    @Test void missingEvidenceRefuses() {
        KbRetrievalService.RetrievalResult empty = new KbRetrievalService.RetrievalResult();
        empty.setBm25(List.of()); empty.setVector(List.of());
        when(retrieval.search(anyString(), anyInt())).thenReturn(empty);
        assertThat(ask().getAnswerMode()).isEqualTo("refused");
    }
}
