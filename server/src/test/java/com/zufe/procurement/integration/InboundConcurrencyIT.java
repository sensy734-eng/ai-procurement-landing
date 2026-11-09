package com.zufe.procurement.integration;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.m5.inbound.dto.InboundVoidRequest;
import com.zufe.procurement.m5.inbound.service.InboundOrderService;
import com.zufe.procurement.m3.order.service.PurchaseOrderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {"app.llm.enabled=false", "spring.profiles.active=integration"})
@Testcontainers(disabledWithoutDocker = true)
class InboundConcurrencyIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("procurement_db").withUsername("workbench").withPassword("integration-only");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired JdbcTemplate db;
    @Autowired InboundOrderService inbounds;
    @Autowired com.zufe.procurement.m5.stock.service.StockService stock;
    @MockitoSpyBean PurchaseOrderService orders;
    BigDecimal deliveredBefore;
    Integer itemStatusBefore;
    Integer orderStatusBefore;

    @BeforeAll static void schema() throws Exception {
        // Start the static container before Spring creates a test instance or resolves its URL.
        DataSource source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        // The only datasource comes from this disposable container, never from a developer database.
        try (var connection = source.getConnection()) {
            assertThat(connection.getMetaData().getURL()).contains(MYSQL.getMappedPort(3306).toString());
        }
        try (var paths = Files.list(Path.of("../db"))) {
            for (Path file : paths.filter(p -> p.getFileName().toString().matches("(0[1-8]|1[0-4])_.*\\.sql")).sorted().toList()) {
                ResourceDatabasePopulator script = new ResourceDatabasePopulator(new FileSystemResource(file));
                script.setSqlScriptEncoding("UTF-8"); script.execute(source);
            }
        }
        try (var paths = Files.list(Path.of("../db/migrations"))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                ResourceDatabasePopulator script = new ResourceDatabasePopulator(new FileSystemResource(file));
                script.setSqlScriptEncoding("UTF-8"); script.execute(source);
            }
        }
    }
    @BeforeEach void fixture() {
        reset(orders);
        db.update("DELETE FROM stock_flow WHERE source_id=90001");
        db.update("DELETE FROM stock_balance WHERE batch_no='IT-INBOUND'");
        db.update("DELETE FROM inbound_item WHERE inbound_id=90001");
        db.update("DELETE FROM inbound_order WHERE id=90001");
        deliveredBefore = db.queryForObject("SELECT delivered_qty FROM purchase_order_item WHERE id=1", BigDecimal.class);
        itemStatusBefore = db.queryForObject("SELECT item_fulfill_status FROM purchase_order_item WHERE id=1", Integer.class);
        orderStatusBefore = db.queryForObject("SELECT fulfill_status FROM purchase_order WHERE id=(SELECT order_id FROM purchase_order_item WHERE id=1)", Integer.class);
        db.update("INSERT INTO inbound_order(id,inbound_no,warehouse_id,inbound_date,status,operator_id) VALUES(90001,'IT-INBOUND',1,CURRENT_DATE,1,1)");
        db.update("INSERT INTO inbound_item(inbound_id,order_item_id,material_id,quantity,unit_id,batch_no,location_id,owner_type,owner_id) VALUES(90001,1,1,7,1,'IT-INBOUND',1,1,1)");
    }
    @AfterEach void cleanup() {
        reset(orders);
        db.update("UPDATE purchase_order_item SET delivered_qty=?, item_fulfill_status=? WHERE id=1", deliveredBefore, itemStatusBefore);
        db.update("UPDATE purchase_order SET fulfill_status=? WHERE id=(SELECT order_id FROM purchase_order_item WHERE id=1)", orderStatusBefore);
        AuthContext.clear();
    }
    private boolean act(Runnable operation) {
        CurrentUser user = new CurrentUser(); user.setUserId(1L); user.setUsername("integration");
        user.getScope().setAll(true); AuthContext.set(user);
        try { operation.run(); return true; }
        catch (com.zufe.procurement.common.BizException expected) { return false; }
        finally { AuthContext.clear(); }
    }
    @Test void twentyConfirmRequestsWriteExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> requests = new ArrayList<>();
            for (int i=0; i<20; i++) requests.add(pool.submit(() -> { start.await(); return act(() -> inbounds.confirm(90001L)); }));
            start.countDown(); int successes = 0;
            for (Future<Boolean> result : requests) if (result.get(30, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(db.queryForObject("SELECT quantity FROM stock_balance WHERE batch_no='IT-INBOUND'", BigDecimal.class)).isEqualByComparingTo("7");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_flow WHERE source_id=90001", Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT delivered_qty FROM purchase_order_item WHERE id=1", BigDecimal.class)).isEqualByComparingTo(deliveredBefore.add(new BigDecimal("7")));
        } finally { pool.shutdownNow(); }
    }
    @Test void confirmAndVoidRaceBalancesDocumentsAndLedger() throws Exception {
        InboundVoidRequest request = new InboundVoidRequest(); request.setReason("integration race");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = pool.submit(() -> act(() -> inbounds.confirm(90001L)));
            Future<Boolean> b = pool.submit(() -> act(() -> inbounds.voidOrder(90001L, request)));
            assertThat(a.get(30, TimeUnit.SECONDS)).isTrue(); b.get(30, TimeUnit.SECONDS);
            int status = db.queryForObject("SELECT status FROM inbound_order WHERE id=90001", Integer.class);
            String expected = status == 3 ? "0" : "7";
            assertThat(db.queryForObject("SELECT quantity FROM stock_balance WHERE batch_no='IT-INBOUND'", BigDecimal.class)).isEqualByComparingTo(expected);
            assertThat(db.queryForObject("SELECT SUM(change_qty) FROM stock_flow WHERE source_id=90001", BigDecimal.class)).isEqualByComparingTo(expected);
            assertThat(db.queryForObject("SELECT delivered_qty FROM purchase_order_item WHERE id=1", BigDecimal.class)).isEqualByComparingTo(deliveredBefore.add(new BigDecimal(expected)));
        } finally { pool.shutdownNow(); }
    }
    @Test void orderWriteFailureRollsBackAllPriorWrites() {
        doThrow(new IllegalStateException("injected order failure")).when(orders).applyDeliveredQty(eq(1L), any());
        assertThatThrownBy(() -> act(() -> inbounds.confirm(90001L))).isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("SELECT status FROM inbound_order WHERE id=90001", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_balance WHERE batch_no='IT-INBOUND'", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_flow WHERE source_id=90001", Integer.class)).isZero();
    }
    @Test void deletionCannotRemoveConfirmedBill() {
        assertThat(act(() -> inbounds.confirm(90001L))).isTrue();
        assertThat(act(() -> inbounds.deleteDraft(90001L))).isFalse();
        assertThat(db.queryForObject("SELECT is_deleted FROM inbound_order WHERE id=90001", Integer.class)).isZero();
    }
    @Test void foreignWarehouseAndMissingScopeFailClosed() {
        CurrentUser user = new CurrentUser(); user.setUserId(99L);
        user.getScope().getWarehouseIds().add(2L); AuthContext.set(user);
        assertThatThrownBy(() -> inbounds.confirm(90001L)).isInstanceOf(com.zufe.procurement.common.BizException.class);
        assertThatThrownBy(() -> stock.available(1L, 1L)).isInstanceOf(com.zufe.procurement.common.BizException.class);
        user.getScope().getWarehouseIds().clear(); user.getScope().getDeptIds().add(1L);
        assertThatThrownBy(() -> stock.available(1L, 1L)).isInstanceOf(com.zufe.procurement.common.BizException.class);
        assertThat(db.queryForObject("SELECT status FROM inbound_order WHERE id=90001", Integer.class)).isEqualTo(1);
    }
    @Test void orderQueriesCannotCrossDepartmentScope() {
        CurrentUser user = new CurrentUser(); user.setUserId(99L);
        Long department = db.queryForObject("SELECT dept_id FROM purchase_order WHERE id=(SELECT order_id FROM purchase_order_item WHERE id=1)", Long.class);
        user.getScope().getDeptIds().add(department); AuthContext.set(user);
        assertThat(orders.page(new com.zufe.procurement.m3.order.dto.OrderQuery()).getTotal()).isGreaterThan(0);
        user.getScope().getDeptIds().clear(); user.getScope().getDeptIds().add(999999L);
        assertThat(orders.page(new com.zufe.procurement.m3.order.dto.OrderQuery()).getRecords()).isEmpty();
    }
}
