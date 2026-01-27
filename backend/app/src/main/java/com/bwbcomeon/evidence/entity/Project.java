package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 项目表实体类
 * 
 * @author system
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
     * 项目描述（项目说明）
     */
    private String description;
    
    /**
     * 项目状态：active-活跃, archived-已归档
     */
    private String status;
    
    /**
     * 创建人（创建用户ID）
     */
    private UUID createdBy;
    
    /**
     * 创建时间
     * 
     * 使用 Instant 类型，因为 PostgreSQL 的 TIMESTAMPTZ 包含时区信息，
     * Instant 是 UTC 时间点，适合存储带时区的时间戳
     */
    private Instant createdAt;
    
    /**
     * 更新时间
     * 
     * 使用 Instant 类型，因为 PostgreSQL 的 TIMESTAMPTZ 包含时区信息，
     * Instant 是 UTC 时间点，适合存储带时区的时间戳
     */
    private Instant updatedAt;
}
