package com.bwbcomeon.evidence.util;

import com.bwbcomeon.evidence.dto.PermissionBits;
import com.bwbcomeon.evidence.entity.AuthProjectAcl;
import com.bwbcomeon.evidence.entity.Project;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.AuthProjectAclMapper;
import com.bwbcomeon.evidence.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P1 回归：checkCanArchive 与 checkCanInvalidate 同源，editor/viewer 调接口应 403。
 */
@ExtendWith(MockitoExtension.class)
class PermissionUtilTest {

    private static final Long PROJECT_ID = 1L;
    private static final UUID CREATED_BY = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID EDITOR_USER = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_USER = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private AuthProjectAclMapper authProjectAclMapper;

    @InjectMocks
    private PermissionUtil permissionUtil;

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(PROJECT_ID);
        project.setCreatedBy(CREATED_BY);
    }

    @Test
    void checkCanArchive_editor_throws_403() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        AuthProjectAcl editorAcl = new AuthProjectAcl();
        editorAcl.setProjectId(PROJECT_ID);
        editorAcl.setUserId(EDITOR_USER);
        editorAcl.setRole("editor");
        when(authProjectAclMapper.selectByProjectIdAndUserId(eq(PROJECT_ID), eq(EDITOR_USER))).thenReturn(editorAcl);

        assertThatThrownBy(() -> permissionUtil.checkCanArchive(PROJECT_ID, EDITOR_USER, "PROJECT_EDITOR"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅项目责任人可归档证据");
    }

    @Test
    void checkCanArchive_viewer_throws_403() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        AuthProjectAcl viewerAcl = new AuthProjectAcl();
        viewerAcl.setProjectId(PROJECT_ID);
        viewerAcl.setUserId(VIEWER_USER);
        viewerAcl.setRole("viewer");
        when(authProjectAclMapper.selectByProjectIdAndUserId(eq(PROJECT_ID), eq(VIEWER_USER))).thenReturn(viewerAcl);

        assertThatThrownBy(() -> permissionUtil.checkCanArchive(PROJECT_ID, VIEWER_USER, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅项目责任人可归档证据");
    }

    @Test
    void checkCanArchive_system_admin_passes() {
        permissionUtil.checkCanArchive(PROJECT_ID, EDITOR_USER, "SYSTEM_ADMIN");
        // no throw
    }

    @Test
    void checkCanArchive_created_by_passes() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        permissionUtil.checkCanArchive(PROJECT_ID, CREATED_BY, "PMO");
        // no throw: created_by is always allowed
    }

    @Test
    void checkCanArchive_acl_owner_passes() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        AuthProjectAcl ownerAcl = new AuthProjectAcl();
        ownerAcl.setProjectId(PROJECT_ID);
        ownerAcl.setUserId(EDITOR_USER);
        ownerAcl.setRole("owner");
        when(authProjectAclMapper.selectByProjectIdAndUserId(eq(PROJECT_ID), eq(EDITOR_USER))).thenReturn(ownerAcl);
        permissionUtil.checkCanArchive(PROJECT_ID, EDITOR_USER, "PMO");
        // no throw
    }

    // ---------- Phase 4 P2-2: computeProjectPermissionBits 回归 ----------

    @Test
    void computeProjectPermissionBits_system_admin_all_true() {
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(PROJECT_ID, EDITOR_USER, "SYSTEM_ADMIN");
        assertThat(bits.getCanUpload()).isTrue();
        assertThat(bits.getCanSubmit()).isTrue();
        assertThat(bits.getCanArchive()).isTrue();
        assertThat(bits.getCanInvalidate()).isTrue();
        assertThat(bits.getCanManageMembers()).isTrue();
    }

    @Test
    void computeProjectPermissionBits_auditor_all_false() {
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(PROJECT_ID, EDITOR_USER, "AUDITOR");
        assertThat(bits.getCanUpload()).isFalse();
        assertThat(bits.getCanSubmit()).isFalse();
        assertThat(bits.getCanArchive()).isFalse();
        assertThat(bits.getCanInvalidate()).isFalse();
        assertThat(bits.getCanManageMembers()).isFalse();
    }

    @Test
    void computeProjectPermissionBits_pmo_no_project_role_only_can_manage_members() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(authProjectAclMapper.selectByProjectIdAndUserId(eq(PROJECT_ID), eq(EDITOR_USER))).thenReturn(null);
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(PROJECT_ID, EDITOR_USER, "PMO");
        assertThat(bits.getCanUpload()).isFalse();
        assertThat(bits.getCanSubmit()).isFalse();
        assertThat(bits.getCanArchive()).isFalse();
        assertThat(bits.getCanInvalidate()).isFalse();
        assertThat(bits.getCanManageMembers()).isTrue();
    }

    @Test
    void computeProjectPermissionBits_acl_owner_all_true() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        AuthProjectAcl ownerAcl = new AuthProjectAcl();
        ownerAcl.setProjectId(PROJECT_ID);
        ownerAcl.setUserId(EDITOR_USER);
        ownerAcl.setRole("owner");
        when(authProjectAclMapper.selectByProjectIdAndUserId(eq(PROJECT_ID), eq(EDITOR_USER))).thenReturn(ownerAcl);
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(PROJECT_ID, EDITOR_USER, null);
        assertThat(bits.getCanUpload()).isTrue();
        assertThat(bits.getCanSubmit()).isTrue();
        assertThat(bits.getCanArchive()).isTrue();
        assertThat(bits.getCanInvalidate()).isTrue();
        assertThat(bits.getCanManageMembers()).isTrue();
    }

    @Test
    void computeProjectPermissionBits_acl_editor_upload_submit_only() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        AuthProjectAcl editorAcl = new AuthProjectAcl();
        editorAcl.setProjectId(PROJECT_ID);
        editorAcl.setUserId(EDITOR_USER);
        editorAcl.setRole("editor");
        when(authProjectAclMapper.selectByProjectIdAndUserId(eq(PROJECT_ID), eq(EDITOR_USER))).thenReturn(editorAcl);
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(PROJECT_ID, EDITOR_USER, null);
        assertThat(bits.getCanUpload()).isTrue();
        assertThat(bits.getCanSubmit()).isTrue();
        assertThat(bits.getCanArchive()).isFalse();
        assertThat(bits.getCanInvalidate()).isFalse();
        assertThat(bits.getCanManageMembers()).isFalse();
    }

    @Test
    void computeProjectPermissionBits_acl_viewer_all_false() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        AuthProjectAcl viewerAcl = new AuthProjectAcl();
        viewerAcl.setProjectId(PROJECT_ID);
        viewerAcl.setUserId(VIEWER_USER);
        viewerAcl.setRole("viewer");
        when(authProjectAclMapper.selectByProjectIdAndUserId(eq(PROJECT_ID), eq(VIEWER_USER))).thenReturn(viewerAcl);
        PermissionBits bits = permissionUtil.computeProjectPermissionBits(PROJECT_ID, VIEWER_USER, null);
        assertThat(bits.getCanUpload()).isFalse();
        assertThat(bits.getCanSubmit()).isFalse();
        assertThat(bits.getCanArchive()).isFalse();
        assertThat(bits.getCanInvalidate()).isFalse();
        assertThat(bits.getCanManageMembers()).isFalse();
    }
}
