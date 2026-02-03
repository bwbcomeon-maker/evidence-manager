package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.EvidenceListItemVO;
import com.bwbcomeon.evidence.dto.EvidenceResponse;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.EvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-1 回归：EvidenceController 使用当前用户 UUID，不再使用固定 UUID。
 */
@ExtendWith(MockitoExtension.class)
class EvidenceControllerTest {

    private static final UUID USER_A_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID USER_B_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock
    private EvidenceService evidenceService;

    @InjectMocks
    private EvidenceController evidenceController;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
    }

    @Test
    void listEvidences_uses_resolved_current_user_uuid_not_fixed() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(10L, "userA", "User A", "USER", true));
        when(evidenceService.resolveCreatedByUuid("userA")).thenReturn(USER_A_UUID);
        when(evidenceService.listEvidences(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(USER_A_UUID)))
                .thenReturn(List.of());

        Result<List<EvidenceListItemVO>> result = evidenceController.listEvidences(
                request, 1L, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        verify(evidenceService).resolveCreatedByUuid("userA");
        verify(evidenceService).listEvidences(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(USER_A_UUID));
    }

    @Test
    void listEvidences_different_user_gets_different_uuid() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(20L, "userB", "User B", "USER", true));
        when(evidenceService.resolveCreatedByUuid("userB")).thenReturn(USER_B_UUID);
        when(evidenceService.listEvidences(eq(2L), isNull(), isNull(), isNull(), isNull(), eq(USER_B_UUID)))
                .thenReturn(List.of());

        Result<List<EvidenceListItemVO>> result = evidenceController.listEvidences(
                request, 2L, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        verify(evidenceService).resolveCreatedByUuid("userB");
        verify(evidenceService).listEvidences(eq(2L), isNull(), isNull(), isNull(), isNull(), eq(USER_B_UUID));
    }

    @Test
    void listEvidences_returns_401_when_current_user_missing() {
        Result<List<EvidenceListItemVO>> result = evidenceController.listEvidences(
                request, 1L, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("未登录");
        verify(evidenceService, never()).listEvidences(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void listEvidences_returns_403_when_resolve_created_by_uuid_returns_null() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(99L, "unknown", null, "USER", true));
        when(evidenceService.resolveCreatedByUuid("unknown")).thenReturn(null);

        Result<List<EvidenceListItemVO>> result = evidenceController.listEvidences(
                request, 1L, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMessage()).isEqualTo("无法解析当前用户");
        verify(evidenceService).resolveCreatedByUuid("unknown");
        verify(evidenceService, never()).listEvidences(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadEvidence_uses_resolved_current_user_uuid_not_fixed() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(10L, "userA", "User A", "USER", true));
        when(evidenceService.resolveCreatedByUuid("userA")).thenReturn(USER_A_UUID);
        when(evidenceService.uploadEvidence(eq(1L), eq("n"), eq("PLAN"), any(), any(), eq(USER_A_UUID)))
                .thenReturn(new EvidenceResponse());

        MockMultipartFile file = new MockMultipartFile("file", "t.txt", "text/plain", "hello".getBytes());
        Result<EvidenceResponse> result = evidenceController.uploadEvidence(
                request, 1L, "n", "PLAN", null, file);

        assertThat(result.getCode()).isEqualTo(0);
        verify(evidenceService).resolveCreatedByUuid("userA");
        verify(evidenceService).uploadEvidence(eq(1L), eq("n"), eq("PLAN"), any(), any(), eq(USER_A_UUID));
    }
}
