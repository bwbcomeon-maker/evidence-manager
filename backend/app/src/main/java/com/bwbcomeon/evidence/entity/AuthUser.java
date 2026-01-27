package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 系统用户表实体类
 */
@Data
public class AuthUser {
    /**
     * 用户ID (UUID, 主键)
     */
    private UUID id;

    /**
     * 登录名（唯一登录账号）
     */
    private String username;

    /**
     * 显示名称（用户姓名或昵称）
     */
    private String displayName;

    /**
     * 联系邮箱
     */
    private String email;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
