package com.bwbcomeon.evidence.dto;

import lombok.Data;
import java.util.List;

/**
 * 归档门禁失败时返回给前端的结构化数据（HTTP 400 body）
 */
@Data
public class ArchiveBlockVO {
    private String archiveBlockReason;
    private List<String> keyMissing;
    private List<String> blockedByStages;
    private List<BlockedByItemVO> blockedByRequiredItems;
}
