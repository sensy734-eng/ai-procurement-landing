package com.zufe.procurement.m7.assistant.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.auth.*;
import com.zufe.procurement.llm.*;
import com.zufe.procurement.m10.kb.mapper.*;
import com.zufe.procurement.m10.kb.service.KbRetrievalService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import static com.zufe.procurement.m7.assistant.task.TaskContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Simulated gateway failures; these are NOT real model quality measurements. */
class TaskFailureTest {
    LlmGateway llm=mock(LlmGateway.class);
    TaskAccess access=mock(TaskAccess.class);
    TaskRunStore runs=mock(TaskRunStore.class);
    OrderEvidenceReader reader=mock(OrderEvidenceReader.class);
    DraftCatalog catalog=mock(DraftCatalog.class);
    ObjectMapper json=new ObjectMapper();
    OrderReviewService review;
    RequestDraftService draft;
    @BeforeEach void setup() {
        CurrentUser user=new CurrentUser();user.setUserId(1L);user.setDeptId(1101L);user.getScope().setAll(true);AuthContext.set(user);
        when(access.fingerprint()).thenReturn("scope");when(runs.json(any())).thenReturn("{}");
        when(reader.read(eq(1L),anyString())).thenAnswer(call -> snapshot("same"));
        when(llm.available()).thenReturn(true);
        when(catalog.materials()).thenReturn(List.of(new MaterialOption(1L,"A","螺栓","M8","个"), new MaterialOption(2L,"B","螺栓","M10","个")));
        when(catalog.owners()).thenReturn(List.of());
        review=new OrderReviewService(reader,access,runs,llm,json,mock(KbRetrievalService.class),mock(KbChunkMapper.class),mock(KbDocumentMapper.class));
        draft=new RequestDraftService(catalog,access,runs,llm,json);
    }
    @AfterEach void clear() { AuthContext.clear(); }
    ReviewResult snapshot(String id) {
        var r=new ReviewResult();r.setOrderId(1L);r.setOrderNo("DEMO");r.setSnapshotId(id);
        r.getEvidence().add(new Evidence(1,"order_item","明细","数量30个",null,1L,null));return r;
    }
    ReviewResult review() { return review.review(1L,new ReviewRequest("检查订单","all")); }
    @ParameterizedTest @ValueSource(strings={"TIMEOUT","RATE_LIMIT","SERVER","BAD_JSON","NETWORK"})
    void allGatewayFailuresRetainFactsWithoutAi(String reason) {
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenReturn(LlmResult.fail(reason,1,"simulated-test"));
        var r=review();assertThat(r.getMode()).isEqualTo("facts_only");assertThat(r.getObservations()).isEmpty();assertThat(r.getEvidence()).hasSize(1);
        var d=draft.draft(new DraftRequest("想买一些螺栓"));assertThat(d.getMode()).isEqualTo("manual_required");assertThat(d.getItems()).isEmpty();
    }
    @Test void disabledModelCanOnlyParseLabeledTemplate() {
        when(llm.available()).thenReturn(false);
        assertThat(review().getMode()).isEqualTo("facts_only");
        var d=draft.draft(new DraftRequest("物料：螺栓；数量：二百；单位：个；需求日期：下周五；归属：公共"));
        assertThat(d.getMode()).isEqualTo("rule_template");assertThat(d.getItems().get(0).getMaterialId()).isNull();
        assertThat(d.getItems().get(0).getMaterialCandidates()).hasSize(2);
        verify(llm,never()).chat(anyString(),anyString(),anyInt(),anyInt());
    }
    @Test void malformedResponseDoesNotGenerateFallbackConclusions() {
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenReturn(LlmResult.ok("invalid json",1,"simulated-test",1,1));
        assertThat(review().getDegradedReason()).isEqualTo("OUTPUT_VALIDATION_FAILED");
        assertThat(draft.draft(new DraftRequest("想买螺栓")).getMode()).isEqualTo("manual_required");
    }
    @Test void modelExceptionsAreConservative() {
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenThrow(new IllegalStateException("network"));
        assertThat(review().getMode()).isEqualTo("facts_only");
        assertThat(draft.draft(new DraftRequest("想买螺栓")).getMode()).isEqualTo("manual_required");
    }
    @Test void changedEvidenceClearsPreviouslyValidInterpretation() {
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenReturn(LlmResult.ok("{\"observations\":[{\"text\":\"数量30个\",\"evidenceIds\":[1]}]}",1,"simulated-test",1,1));
        when(reader.read(eq(1L),anyString())).thenReturn(snapshot("before"),snapshot("after"));
        var r=review();assertThat(r.getMode()).isEqualTo("stale");assertThat(r.getObservations()).isEmpty();
    }
    @Test void generatedExtractionNeverTrustsModelMaterialIds() {
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenReturn(LlmResult.ok("{\"items\":[{\"source\":\"买30个螺栓\",\"materialText\":\"螺栓\",\"quantityText\":\"30\",\"unitText\":\"个\",\"materialId\":999}]}",1,"simulated-test",1,1));
        var d=draft.draft(new DraftRequest("买30个螺栓"));
        assertThat(d.getMode()).isEqualTo("generated");assertThat(d.getItems().get(0).getMaterialId()).isNull();
        assertThat(d.getItems().get(0).getRequireDate()).isNull();assertThat(d.getItems().get(0).getOwnerType()).isNull();
    }
    DraftLine generatedLine(String material, String owner) throws Exception {
        String text=owner+"，明天要30件"+material;
        String output=json.writeValueAsString(Map.of("items",List.of(Map.of("source",text,"materialText",material,
                "quantityText","30","unitText","件","dateText","明天","ownerText",owner))));
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenReturn(LlmResult.ok(output,1,"simulated-test",1,1));
        return draft.draft(new DraftRequest(text)).getItems().get(0);
    }
    @ParameterizedTest @ValueSource(strings={"公共","公共库存","公共领用","部门领用"})
    void generatedPublicOwnerAliasesResolve(String owner) throws Exception {
        var line=generatedLine("螺栓",owner);
        assertThat(line.getOwnerType()).isEqualTo(1);assertThat(line.getOwnerId()).isNull();
    }
    @Test void generatedPublicOwnerStillRequiresScope() throws Exception {
        var scope=AuthContext.getOrThrow().getScope();scope.setAll(false);scope.getDeptIds().add(1101L);
        scope.getOwnerNos().add("AI-DEMO-PRJ-A");
        var line=generatedLine("螺栓","公共领用");
        assertThat(line.getOwnerType()).isNull();assertThat(line.getIssues()).contains("公共领用不在当前授权范围内，请选择授权归属对象");
    }
    @Test void generatedCodeWithChineseDescriptionMatchesCompleteCode() throws Exception {
        when(catalog.materials()).thenReturn(List.of(new MaterialOption(1L,"AI-DEMO-M8","AI演示螺栓","M8","件"),
                new MaterialOption(2L,"AI-DEMO-M80","AI演示螺栓","M80","件")));
        assertThat(generatedLine("AI-DEMO-M8螺栓","公共领用").getMaterialId()).isEqualTo(1L);
        assertThat(generatedLine("AI-DEMO-M80螺栓","公共领用").getMaterialId()).isEqualTo(2L);
        assertThat(generatedLine("AI-DEMO-M800螺栓","公共领用").getMaterialId()).isNull();
    }
    @Test void multipleCodesNeverSilentlySelectTheFirst() throws Exception {
        when(catalog.materials()).thenReturn(List.of(new MaterialOption(1L,"AI-DEMO-M8","AI演示螺栓","M8","件"),
                new MaterialOption(2L,"AI-DEMO-M10","AI演示螺栓","M10","件")));
        var line=generatedLine("AI-DEMO-M8或AI-DEMO-M10螺栓","公共领用");
        assertThat(line.getMaterialId()).isNull();assertThat(line.getMaterialCandidates()).hasSize(2);
    }
}
