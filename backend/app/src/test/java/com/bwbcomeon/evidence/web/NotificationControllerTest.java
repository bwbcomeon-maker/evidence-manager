package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 模拟前端「我的待办」中点击一条未读待办：验证 PATCH /api/notifications/{id}/read 会进入
 * NotificationServiceImpl.markRead 并传入正确的 userId、notificationId。
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final Long CURRENT_USER_ID = 100L;
    private static final Long NOTIFICATION_ID = 5L;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
    }

    /**
     * 模拟：用户已登录，在待办列表点击一条未读待办（前端会发 PATCH /api/notifications/{id}/read）。
     * 验证：Controller 会调用 NotificationService.markRead(userId, notificationId)，
     *       即请求会进入 com.bwbcomeon.evidence.service.impl.NotificationServiceImpl.markRead。
     */
    @Test
    void markRead_simulatesClickUnreadTodo_invokesServiceMarkRead() {
        request.setAttribute(AuthInterceptor.REQUEST_CURRENT_USER,
                new AuthUserVO(CURRENT_USER_ID, "testuser", "测试用户", "PMO", true));

        Result<Void> result = notificationController.markRead(request, NOTIFICATION_ID);

        assertThat(result.getCode()).isEqualTo(0);

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> notificationIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationService).markRead(userIdCaptor.capture(), notificationIdCaptor.capture());

        assertThat(userIdCaptor.getValue()).isEqualTo(CURRENT_USER_ID);
        assertThat(notificationIdCaptor.getValue()).isEqualTo(NOTIFICATION_ID);
    }

    @Test
    void markRead_noCurrentUser_returns401() {
        Result<Void> result = notificationController.markRead(request, NOTIFICATION_ID);

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("未登录");
        verify(notificationService, never()).markRead(any(), any());
    }
}
