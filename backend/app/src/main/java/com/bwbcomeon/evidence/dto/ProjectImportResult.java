package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目 Excel 导入结果（最小版：无批次表）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectImportResult {
    private int total;
    private int successCount;
    private int failCount;
    private List<RowResult> details = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowResult {
        private int row;
        private String code;
        private boolean success;
        private String message;
    }
}
