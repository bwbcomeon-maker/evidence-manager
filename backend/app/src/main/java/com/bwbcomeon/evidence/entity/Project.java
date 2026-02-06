package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 项目
 */
@Data
public class Project {
    /**
     * ??ID
     */
    private Long id;

    /**
     * 项目令号（唯一，创建时手动填写）
     */
    private String code;

    /**
     * ????
     */
    private String name;

    /**
     * ????
     */
    private String description;

    /**
     * ?????active / archived
     */
    private String status;

    /**
     * 创建人 sys_user.id
     */
    private Long createdByUserId;

    /**
     * ????
     */
    private OffsetDateTime createdAt;

    /**
     * ????
     */
    private OffsetDateTime updatedAt;
}
