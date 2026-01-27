package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 项目权限表
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
     * 授权用户
     */
    private UUID userId;

    /**
     * 项目角色：owner / editor / viewer
     */
    private String role;

    /**
     * 授权时间
     */
    private OffsetDateTime createdAt;
}
