package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 归档门禁失败：未满足的必填项（便于前端跳转定位）
 */
@Data
public class BlockedByItemVO {
    private String stageCode;
    private String evidenceTypeCode;
    private String displayName;
    private Integer shortfall;
}
