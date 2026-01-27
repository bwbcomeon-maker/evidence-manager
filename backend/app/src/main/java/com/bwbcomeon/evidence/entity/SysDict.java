package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 系统数据字典表实体类
 * 
 * @author system
 */
@Data
public class SysDict {
    
    /**
     * 字典ID
     */
    private Long id;
    
    /**
     * 字典类型
     */
    private String dictType;
    
    /**
     * 字典编码
     */
    private String dictCode;
    
    /**
     * 字典值
     */
    private String dictValue;
    
    /**
     * 字典标签
     */
    private String dictLabel;
    
    /**
     * 排序顺序
     */
    private Integer sortOrder;
    
    /**
     * 是否启用
     */
    private Boolean isActive;
    
    /**
     * 备注
     */
    private String remark;
    
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
