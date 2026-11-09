package com.zufe.procurement.m7.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.auth.*;
import com.zufe.procurement.m7.assistant.entity.*;
import com.zufe.procurement.m7.assistant.mapper.*;
import com.zufe.procurement.m7.assistant.vo.AiContext;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SessionAuthorizationTest {
    final AiSessionMapper sessions = mock(AiSessionMapper.class);
    final AiMessageMapper messages = mock(AiMessageMapper.class);
    final ObjectMapper json = new ObjectMapper();
    final AiSessionService service = new AiSessionService(sessions, messages, json);
    CurrentUser user;
    AiSession session;
    @BeforeEach void setup() {
        user = new CurrentUser(); user.setUserId(1L);
        user.setPermissions(new HashSet<>(Set.of("m9:data:query", "m8:kb:query")));
        user.getScope().getDeptIds().add(1L); user.getScope().getWarehouseIds().add(1L);
        AuthContext.set(user);
        session = new AiSession(); session.setId(1L); session.setUserId(1L);
        when(sessions.selectById(1L)).thenReturn(session);
    }
    @AfterEach void cleanup() { AuthContext.clear(); }
    private AiMessage answer(String fingerprint) throws Exception {
        AiMessage message = new AiMessage(); message.setId(3L); message.setRole(2);
        message.setIntentCode("order_list"); message.setContent("private order data");
        message.setResultJson(json.writeValueAsString(Map.of("accessFingerprint", fingerprint)));
        when(messages.selectList(any())).thenReturn(List.of(message));
        when(messages.selectOne(any())).thenReturn(message);
        return message;
    }
    @Test void foreignSessionNeverExposesHistory() {
        assertThatThrownBy(() -> service.messages(1L, 2L)).isInstanceOf(com.zufe.procurement.common.BizException.class);
        verifyNoInteractions(messages);
    }
    @Test void unchangedAuthorityRestoresSavedResult() throws Exception {
        answer(AccessSnapshot.fingerprint(user));
        assertThat(service.messages(1L, 1L).get(0).getContent()).isEqualTo("private order data");
    }
    @Test void departmentChangeHidesResultAndRerunSummary() throws Exception {
        answer(AccessSnapshot.fingerprint(user)); user.getScope().getDeptIds().clear(); user.getScope().getDeptIds().add(2L);
        var restored = service.messages(1L, 1L).get(0);
        assertThat(restored.getResultJson()).isNull(); assertThat(restored.getContent()).doesNotContain("private");
        assertThat(service.lastAssistantMessage(1L)).isNull();
    }
    @Test void warehouseChangeInvalidatesModelContext() throws Exception {
        AiContext context = new AiContext(); context.setAccessFingerprint(AccessSnapshot.fingerprint(user));
        session.setContextJson(json.writeValueAsString(context));
        assertThat(service.readContext(session)).isNotNull();
        user.getScope().getWarehouseIds().clear(); user.getScope().getWarehouseIds().add(2L);
        assertThat(service.readContext(session)).isNull();
    }
    @Test void permissionRevocationHidesHistory() throws Exception {
        answer(AccessSnapshot.fingerprint(user)); user.getPermissions().remove("m9:data:query");
        assertThat(service.messages(1L, 1L).get(0).getResultCode()).isEqualTo("NoPermission");
    }
    @Test void feedbackCannotWriteToAnotherUsersSession() {
        session.setUserId(2L);
        assertThatThrownBy(() -> service.appendFeedback(1L, "feedback", Map.of()))
                .isInstanceOf(com.zufe.procurement.common.BizException.class);
        verifyNoInteractions(messages);
    }
}
