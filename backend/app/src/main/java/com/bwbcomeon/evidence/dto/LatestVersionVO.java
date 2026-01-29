package com.bwbcomeon.evidence.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 最新版本信息VO
 */
@Data
public class LatestVersionVO {
    /**
     * 版本ID
     */
    private Long versionId;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件存储相对路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
