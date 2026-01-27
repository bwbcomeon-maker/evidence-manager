package com.bwbcomeon.evidence.entity;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 证据元数据表实体类
 * 
 * @author system
 */
@Data
public class EvidenceItem {
    
    /**
     * 证据ID
     */
    private Long id;
    
    /**
     * 项目ID（所属项目）
     */
    private Long projectId;
    
    /**
     * 证据标题
     */
    private String title;
    
    /**
     * 证据说明（补充说明）
     */
    private String note;
    
    /**
     * 存储桶（MinIO Bucket）
     */
    private String bucket;
    
    /**
     * 对象路径（MinIO Key）
     */
    private String objectKey;
    
    /**
     * 文件类型（MIME 类型）
     */
    private String contentType;
    
    /**
     * 文件大小（字节大小）
     */
    private Long sizeBytes;
    
    /**
     * ETag（校验标识）
     */
    private String etag;
    
    /**
     * 状态：active-有效, invalid-误传, archived-已归档
     */
    private String status;
    
    /**
     * 创建人（上传人）
     */
    private UUID createdBy;
    
    /**
     * 创建时间（上传时间）
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
    
    /**
     * 误传原因（误传说明）
     */
    private String invalidReason;
    
    /**
     * 误传人（操作人）
     */
    private UUID invalidBy;
    
    /**
     * 误传时间（操作时间）
     * 
     * 使用 Instant 类型，因为 PostgreSQL 的 TIMESTAMPTZ 包含时区信息，
     * Instant 是 UTC 时间点，适合存储带时区的时间戳
     */
    private Instant invalidAt;
}
