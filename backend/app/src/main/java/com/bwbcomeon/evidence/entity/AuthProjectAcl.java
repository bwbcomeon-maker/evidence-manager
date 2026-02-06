package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 项目权限表（用户为 sys_user.id）
 */
@Data
public class AuthProjectAcl {
    /**
     * 记录ID
     */
    private Long id;

    /**
     * 所属项目
     */
    private Long projectId;

    /**
     * 授权用户 sys_user.id
     */
    private Long sysUserId;

    /**
     * 项目角色：owner / editor / viewer
     */
    private String role;

    /**
     * 授权时间
     */
    private OffsetDateTime createdAt;
}
