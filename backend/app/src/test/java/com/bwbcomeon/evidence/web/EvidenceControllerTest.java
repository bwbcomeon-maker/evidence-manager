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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-1 回归：EvidenceController 使用当前用户 sys_user.id (Long)。
 */
@ExtendWith(MockitoExtension.class)
class EvidenceControllerTest {

    private static final Long USER_A_ID = 10L;
    private static final Long USER_B_ID = 20L;

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
    void listEvidences_uses_current_user_id() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(USER_A_ID, "userA", "User A", "USER", true));
        when(evidenceService.listEvidences(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(USER_A_ID), eq("USER")))
                .thenReturn(List.of());

        Result<List<EvidenceListItemVO>> result = evidenceController.listEvidences(
                request, 1L, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        verify(evidenceService).listEvidences(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(USER_A_ID), eq("USER"));
    }

    @Test
    void listEvidences_different_user_gets_different_id() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(USER_B_ID, "userB", "User B", "USER", true));
        when(evidenceService.listEvidences(eq(2L), isNull(), isNull(), isNull(), isNull(), eq(USER_B_ID), eq("USER")))
                .thenReturn(List.of());

        Result<List<EvidenceListItemVO>> result = evidenceController.listEvidences(
                request, 2L, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        verify(evidenceService).listEvidences(eq(2L), isNull(), isNull(), isNull(), isNull(), eq(USER_B_ID), eq("USER"));
    }

    @Test
    void listEvidences_returns_401_when_current_user_missing() {
        Result<List<EvidenceListItemVO>> result = evidenceController.listEvidences(
                request, 1L, null, null, null, null);

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("未登录");
        verify(evidenceService, never()).listEvidences(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadEvidence_uses_current_user_id() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(USER_A_ID, "userA", "User A", "USER", true));
        when(evidenceService.uploadEvidence(eq(1L), eq("n"), eq("PLAN"), any(), any(), eq(USER_A_ID), eq("USER")))
                .thenReturn(new EvidenceResponse());

        MockMultipartFile file = new MockMultipartFile("file", "t.txt", "text/plain", "hello".getBytes());
        Result<EvidenceResponse> result = evidenceController.uploadEvidence(
                request, 1L, "n", "PLAN", null, file);

        assertThat(result.getCode()).isEqualTo(0);
        verify(evidenceService).uploadEvidence(eq(1L), eq("n"), eq("PLAN"), any(), any(), eq(USER_A_ID), eq("USER"));
    }
}
