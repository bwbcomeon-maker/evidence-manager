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
     * 项目状态：active / archived
     */
    private String status;

    /**
     * 是否含采购（S1 比测报告 required_when=HAS_PROCUREMENT 时参与计算）
     */
    private Boolean hasProcurement;

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
