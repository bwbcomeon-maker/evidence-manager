package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 项目表
 */
@Data
public class Project {
    /**
     * 项目ID
     */
    private Long id;

    /**
     * 项目编号
     */
    private String code;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 项目状态：active / archived
     */
    private String status;

    /**
     * 创建人
     */
    private UUID createdBy;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
