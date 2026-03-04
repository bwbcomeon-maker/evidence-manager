package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.entity.Notification;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.NotificationMapper;
import com.bwbcomeon.evidence.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 NotificationServiceImpl.markRead 的业务逻辑：参数校验、查库、按 user_id 更新 read_at、403/404。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final Long NOTIFICATION_ID = 1L;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification notificationOwnedByCurrentUser;
    private Notification notificationOwnedByOtherUser;

    @BeforeEach
    void setUp() {
        notificationOwnedByCurrentUser = new Notification();
        notificationOwnedByCurrentUser.setId(NOTIFICATION_ID);
        notificationOwnedByCurrentUser.setUserId(USER_ID);
        notificationOwnedByCurrentUser.setType("ARCHIVE_PENDING");
        notificationOwnedByCurrentUser.setReadAt(null);

        notificationOwnedByOtherUser = new Notification();
        notificationOwnedByOtherUser.setId(NOTIFICATION_ID);
        notificationOwnedByOtherUser.setUserId(OTHER_USER_ID);
        notificationOwnedByOtherUser.setType("ARCHIVE_PENDING");
        notificationOwnedByOtherUser.setReadAt(null);
    }

    /** 正常流程：当前用户点击自己的未读待办，应调用 markReadByUser 并更新 1 行，不抛异常 */
    @Test
    void markRead_ownerClicks_callsMarkReadByUserAndSucceeds() {
        when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(notificationOwnedByCurrentUser);
        when(notificationMapper.markReadByUser(eq(NOTIFICATION_ID), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(1);

        notificationService.markRead(USER_ID, NOTIFICATION_ID);

        ArgumentCaptor<OffsetDateTime> readAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificationMapper).selectById(NOTIFICATION_ID);
        verify(notificationMapper).markReadByUser(eq(NOTIFICATION_ID), eq(USER_ID), readAtCaptor.capture());
        assertThat(readAtCaptor.getValue()).isNotNull();
    }

    /** 当前用户不是该条消息的接收人时，markReadByUser 返回 0，应抛 403 */
    @Test
    void markRead_otherUserOwnsNotification_throws403() {
        when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(notificationOwnedByOtherUser);
        when(notificationMapper.markReadByUser(eq(NOTIFICATION_ID), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> notificationService.markRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getCode()).isEqualTo(403);
                    assertThat(ex.getMessage()).isEqualTo("无权限操作该消息");
                });

        verify(notificationMapper).selectById(NOTIFICATION_ID);
        verify(notificationMapper).markReadByUser(eq(NOTIFICATION_ID), eq(USER_ID), any(OffsetDateTime.class));
    }

    /** 消息不存在时抛 404 */
    @Test
    void markRead_notificationNotFound_throws404() {
        when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(null);

        assertThatThrownBy(() -> notificationService.markRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getCode()).isEqualTo(404);
                    assertThat(ex.getMessage()).isEqualTo("消息不存在");
                });

        verify(notificationMapper).selectById(NOTIFICATION_ID);
        verify(notificationMapper, never()).markReadByUser(any(), any(), any());
    }

    /** userId 或 notificationId 为空时抛 400 */
    @Test
    void markRead_nullUserId_throws400() {
        assertThatThrownBy(() -> notificationService.markRead(null, NOTIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getCode()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("参数不能为空");
                });
        verify(notificationMapper, never()).selectById(any());
    }

    @Test
    void markRead_nullNotificationId_throws400() {
        assertThatThrownBy(() -> notificationService.markRead(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getCode()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo("参数不能为空");
                });
        verify(notificationMapper, never()).selectById(any());
    }
}
