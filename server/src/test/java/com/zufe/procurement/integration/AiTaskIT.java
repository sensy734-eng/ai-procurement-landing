package com.zufe.procurement.integration;

import com.zufe.procurement.auth.*;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.llm.*;
import com.zufe.procurement.m7.assistant.task.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.zufe.procurement.m7.assistant.task.TaskContracts.*;

/** Disposable MySQL + simulated gateway. Never mutates the Compose/developer database. */
@SpringBootTest(properties={"app.llm.enabled=false","spring.profiles.active=integration"})
@Testcontainers(disabledWithoutDocker=true)
class AiTaskIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("procurement_db").withUsername("workbench").withPassword("integration-only");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @BeforeAll static void schema() throws Exception {
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        try(var paths=Files.list(Path.of("../db"))) {
            for(var p:paths.filter(p->p.getFileName().toString().matches("(0[1-8]|1[0-4])_.*\\.sql")).sorted().toList()) {
                var script=new ResourceDatabasePopulator(new FileSystemResource(p));script.setSqlScriptEncoding("UTF-8");script.execute(ds);
            }
        }
        try(var paths=Files.list(Path.of("../db/migrations"))) {
            for(var p:paths.filter(p->p.toString().endsWith(".sql")).sorted().toList()) {
                var script=new ResourceDatabasePopulator(new FileSystemResource(p));script.setSqlScriptEncoding("UTF-8");script.execute(ds);
            }
        }
    }
    @Autowired JdbcTemplate db;
    @Autowired OrderReviewService reviews;
    @Autowired RequestDraftService drafts;
    @Autowired com.zufe.procurement.m2.request.service.PurchaseRequestService requests;
    @Autowired TaskRunStore runs;
    @MockitoBean LlmGateway llm;
    @BeforeEach void login() {
        CurrentUser u=new CurrentUser();u.setUserId(81001L);u.setDeptId(1101L);
        u.getPermissions().addAll(Set.of("m7:chat:query","m9:data:query","m3:order:view","m6:evidence:view","m6:stat:view",
                "m2:request:create","m2:request_item:create","m1:material:list","m2:request_item:owner"));
        u.getScope().getDeptIds().add(1101L);u.getScope().getOwnerNos().addAll(Set.of("PUBLIC","AI-DEMO-PRJ-A"));AuthContext.set(u);
        when(llm.available()).thenReturn(false);
    }
    @AfterEach void clear() { AuthContext.clear(); }
    ReviewResult review(long id) { return reviews.review(id,new ReviewRequest("检查价格和交期","all")); }
    DraftResult draft() { return drafts.draft(new DraftRequest("物料：AI演示螺栓；数量：200；单位：个；需求日期：下周五；归属：公共")); }
    DraftSelection selection(Long material,int type,Long owner,boolean confirmed) {
        return new DraftSelection(0,material,new BigDecimal("200"),LocalDate.now().plusDays(10).toString(),type,owner,confirmed);
    }
    DraftValidation validate(String run,DraftSelection row) { return drafts.validate(new DraftValidationRequest(run,List.of(row))); }
    String businessState() {
        var state=new LinkedHashMap<String,Object>();
        for(String table:List.of("purchase_request","purchase_request_item","purchase_order","purchase_order_item","stock_balance","stock_flow","inbound_order"))
            state.put(table,db.queryForList("SELECT * FROM "+table+" ORDER BY id"));
        return runs.hash(state);
    }
    @Test void historyOnlyUsesEarlierAuthorizedRowsAndKeepsUnknowns() {
        var r=review(81002);
        var history=r.getEvidence().stream().filter(e->e.kind().equals("history")).toList();
        assertThat(history).extracting(Evidence::sourceId).containsExactlyInAnyOrder(81005L,81006L);
        assertThat(r.getEvidence().stream().filter(e->e.kind().equals("history_summary")).findFirst().orElseThrow().text()).contains("11.5元");
        assertThat(r.getChecks()).anyMatch(c->c.code().startsWith("delivery:") && c.status().equals("attention"));
        assertThat(r.getChecks()).anyMatch(c->c.code().startsWith("price:") && c.detail().contains("80%"));
        var missing=review(81003);
        assertThat(missing.getChecks()).anyMatch(c->c.code().startsWith("price:") && c.status().equals("unknown"));
        assertThat(missing.getChecks()).anyMatch(c->c.code().startsWith("delivery:") && c.status().equals("unknown"));
        assertThat(missing.getEvidence()).noneMatch(e->e.kind().startsWith("history"));
    }
    @Test void departmentOwnerAndFeatureBoundariesRejectBeforeCallingModel() {
        assertThatThrownBy(()->review(81004)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->review(81008)).isInstanceOf(BizException.class);
        AuthContext.getOrThrow().getPermissions().remove("m3:order:view");
        assertThatThrownBy(()->review(81001)).isInstanceOf(BizException.class);
        verify(llm,never()).chat(anyString(),anyString(),anyInt(),anyInt());
    }
    @Test void bothTasksAndConfirmationLeaveAllBusinessTablesUntouched() {
        String before=businessState();review(81002);
        var d=draft();
        assertThat(d.getItems().get(0).getMaterialCandidates()).hasSize(2);
        assertThat(d.getItems().get(0).getMaterialId()).isNull();
        assertThat(d.getMaterials()).noneMatch(m->m.id()==81005L);
        assertThat(d.getOwners()).extracting(OwnerOption::id).containsExactly(81001L);
        var confirmed=validate(d.getRunId(),selection(81001L,2,81001L,true));
        assertThat(confirmed.items().get(0).ownerId()).isEqualTo(81001L);
        assertThat(businessState()).isEqualTo(before);
        assertThat(db.queryForObject("SELECT confirmation_json IS NOT NULL FROM ai_task_run WHERE run_id=?",Boolean.class,d.getRunId())).isTrue();
    }
    @Test void cannotReuseOtherUsersRunsOrExpiredRunsOrChangedScope() {
        var d=draft();AuthContext.getOrThrow().setUserId(2L);
        assertThatThrownBy(()->validate(d.getRunId(),selection(81001L,1,null,true))).isInstanceOf(BizException.class);
        AuthContext.getOrThrow().setUserId(81001L);AuthContext.getOrThrow().getScope().getOwnerNos().remove("AI-DEMO-PRJ-A");
        assertThatThrownBy(()->validate(d.getRunId(),selection(81001L,1,null,true))).isInstanceOf(BizException.class);
        login();db.update("UPDATE ai_task_run SET created_at=DATE_SUB(NOW(),INTERVAL 2 HOUR) WHERE run_id=?",d.getRunId());
        assertThatThrownBy(()->validate(d.getRunId(),selection(81001L,1,null,true))).isInstanceOf(BizException.class);
    }
    @Test void confirmationRevalidatesMaterialOwnerAndUnit() {
        var d=draft();
        assertThatThrownBy(()->validate(d.getRunId(),selection(81001L,1,null,false))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->validate(d.getRunId(),selection(81001L,2,81002L,true))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->validate(d.getRunId(),selection(81005L,1,null,true))).isInstanceOf(BizException.class);
        db.update("UPDATE material SET status=2 WHERE id=81001");
        try { assertThatThrownBy(()->validate(d.getRunId(),selection(81001L,1,null,true))).isInstanceOf(BizException.class); }
        finally { db.update("UPDATE material SET status=1 WHERE id=81001"); }
    }
    @Test void changedOrderDuringInferenceInvalidatesInterpretation() {
        when(llm.available()).thenReturn(true);
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenAnswer(call->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            db.update("UPDATE purchase_order SET remark='changed-during-inference' WHERE id=81002");
            return LlmResult.ok("{\"observations\":[{\"text\":\"请核实交付计划\",\"evidenceIds\":[1]}]}",1,"simulated-test",20,10);
        });
        String before=db.queryForObject("SELECT remark FROM purchase_order WHERE id=81002",String.class);
        try { var r=review(81002);assertThat(r.getMode()).isEqualTo("stale");assertThat(r.getObservations()).isEmpty(); }
        finally { db.update("UPDATE purchase_order SET remark=? WHERE id=81002",before); }
    }
    @Test void permissionRevocationDuringInferenceReturnsNoEvidence() {
        when(llm.available()).thenReturn(true);
        when(llm.chat(anyString(),anyString(),anyInt(),anyInt())).thenAnswer(call->{
            AuthContext.getOrThrow().getPermissions().remove("m3:order:view");
            return LlmResult.fail("TIMEOUT",1,"simulated-test");
        });
        assertThatThrownBy(()->review(81002)).isInstanceOf(BizException.class);
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void originalFormSaveKeepsScopedOwnerAndAllowsMissingPrice() {
        assertThat(requests.ownerOptions().getOptions()).noneMatch(o->o.getId()==81002L);
        Long id=requests.create(new com.zufe.procurement.m2.request.dto.RequestSaveRequest());
        var item=new com.zufe.procurement.m2.request.dto.RequestItemSaveRequest();
        item.setMaterialId(81001L);item.setQuantity(BigDecimal.TEN);item.setRequireDate(LocalDate.now().plusDays(5));
        item.setOwnerType(2);item.setOwnerId(81002L);
        assertThatThrownBy(()->requests.addItem(id,item)).isInstanceOf(BizException.class);
        item.setOwnerId(81001L);Long row=requests.addItem(id,item);
        assertThat(db.queryForObject("SELECT owner_no FROM purchase_request_item WHERE id=?",String.class,row)).isEqualTo("AI-DEMO-PRJ-A");
        assertThat(db.queryForObject("SELECT est_price FROM purchase_request_item WHERE id=?",BigDecimal.class,row)).isNull();
    }
}
