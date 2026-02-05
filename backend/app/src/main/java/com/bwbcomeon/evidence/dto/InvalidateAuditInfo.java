package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作废证据后供审计日志使用的快照信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvalidateAuditInfo {
    private Long projectId;
    private String beforeData;
    private String afterData;
}
