package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目基本信息（列表/详情/创建响应共用）
 * V1：权限位以 permissions 为准；扁平 can* 保留兼容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectVO {
    private Long id;
    /** 项目令号 */
    private String code;
    private String name;
    private String description;
    private String status;
    /** 创建时间（列表可无，详情展示用） */
    private String createdAt;
    /** V1 统一权限位（前端按钮只读此） */
    private PermissionBits permissions;
    /** @deprecated 兼容，与 permissions.canInvalidate 一致 */
    private Boolean canInvalidate;
    /** @deprecated 兼容，与 permissions.canManageMembers 一致 */
    private Boolean canManageMembers;
    /** 兼容：与 permissions.canUpload 一致 */
    private Boolean canUpload;
    /** 当前项目经理 auth_user.id（ACL 中 role=owner 的用户，若无则为 created_by） */
    private String currentPmUserId;
    /** 当前项目经理展示名（auth_user.display_name） */
    private String currentPmDisplayName;
}
