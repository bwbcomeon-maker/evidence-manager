package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.AdminUserUpdateRequest;
import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import com.bwbcomeon.evidence.web.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户管理「禁止自我操作」安全规则回归测试：
 * - SYSTEM_ADMIN 修改/禁用/重置密码/删除自己 -> 403
 * - admin 对 admin 任何修改 -> 403
 * - 操作他人时正常放行
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceSelfOperationTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final AuthUserVO ADMIN_VO = new AuthUserVO(ADMIN_ID, "admin", "系统管理员", "SYSTEM_ADMIN", true);
    private static final AuthUserVO OTHER_VO = new AuthUserVO(OTHER_ID, "pmo1", "PMO", "PMO", true);

    @Mock
    private HttpServletRequest request;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AdminUserService adminUserService;

    private SysUser adminUser;
    private SysUser otherUser;

    @BeforeEach
    void setUp() {
        adminUser = new SysUser();
        adminUser.setId(ADMIN_ID);
        adminUser.setUsername("admin");
        adminUser.setRoleCode("SYSTEM_ADMIN");
        adminUser.setEnabled(true);
        adminUser.setIsDeleted(false);

        otherUser = new SysUser();
        otherUser.setId(OTHER_ID);
        otherUser.setUsername("pmo1");
        otherUser.setRoleCode("PMO");
        otherUser.setEnabled(true);
        otherUser.setIsDeleted(false);
    }

    @Test
    void update_self_throws_403() {
        when(request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER)).thenReturn(ADMIN_VO);
        when(sysUserMapper.selectById(ADMIN_ID)).thenReturn(adminUser);

        AdminUserUpdateRequest body = new AdminUserUpdateRequest();
        body.setRealName("x");

        assertThatThrownBy(() -> adminUserService.update(request, ADMIN_ID, body))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getCode()).isEqualTo(AdminUserService.SELF_OPERATION_FORBIDDEN_CODE);
                    assertThat(ex.getMessage()).isEqualTo(AdminUserService.SELF_OPERATION_FORBIDDEN_MESSAGE);
                });
        verify(authService).recordAudit(eq(request), eq(AdminUserService.AUDIT_ACTION_SELF_OPERATION_FORBIDDEN), eq(false), eq(ADMIN_ID), any(), any(), any());
        verify(sysUserMapper, never()).update(any());
    }

    @Test
    void update_admin_by_admin_throws_403() {
        when(request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER)).thenReturn(ADMIN_VO);
        when(sysUserMapper.selectById(ADMIN_ID)).thenReturn(adminUser);

        AdminUserUpdateRequest body = new AdminUserUpdateRequest();
        body.setRealName("x");

        assertThatThrownBy(() -> adminUserService.update(request, ADMIN_ID, body))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
        verify(sysUserMapper, never()).update(any());
    }

    @Test
    void setEnabled_self_throws_403() {
        when(request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER)).thenReturn(ADMIN_VO);
        when(sysUserMapper.selectById(ADMIN_ID)).thenReturn(adminUser);

        assertThatThrownBy(() -> adminUserService.setEnabled(request, ADMIN_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
        verify(sysUserMapper, never()).setEnabled(any(), any(boolean.class));
    }

    @Test
    void resetPassword_self_throws_403() {
        when(request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER)).thenReturn(ADMIN_VO);
        when(sysUserMapper.selectById(ADMIN_ID)).thenReturn(adminUser);

        assertThatThrownBy(() -> adminUserService.resetPassword(request, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
        verify(sysUserMapper, never()).resetPassword(any(), any());
    }

    @Test
    void logicalDelete_self_throws_403() {
        when(request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER)).thenReturn(ADMIN_VO);
        when(sysUserMapper.selectById(ADMIN_ID)).thenReturn(adminUser);

        assertThatThrownBy(() -> adminUserService.logicalDelete(request, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
        verify(sysUserMapper, never()).logicalDelete(any());
    }

    @Test
    void update_other_user_succeeds() {
        when(request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER)).thenReturn(ADMIN_VO);
        when(sysUserMapper.selectById(OTHER_ID)).thenReturn(otherUser);

        AdminUserUpdateRequest body = new AdminUserUpdateRequest();
        body.setRealName("PMO2");

        adminUserService.update(request, OTHER_ID, body);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).update(captor.capture());
        assertThat(captor.getValue().getRealName()).isEqualTo("PMO2");
    }

    @Test
    void setEnabled_other_user_succeeds() {
        when(request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER)).thenReturn(ADMIN_VO);
        when(sysUserMapper.selectById(OTHER_ID)).thenReturn(otherUser);

        adminUserService.setEnabled(request, OTHER_ID, false);

        verify(sysUserMapper).setEnabled(OTHER_ID, false);
    }
}
