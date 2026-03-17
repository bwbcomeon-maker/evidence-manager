package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;

/**
 * 版本文件下载结果（根据下载变体解析后的最终文件）
 */
@Data
@AllArgsConstructor
public class VersionDownloadResult {
    private Resource resource;
    private String filename;
    private String contentType;
    private boolean inlinePreviewAllowed;
}

