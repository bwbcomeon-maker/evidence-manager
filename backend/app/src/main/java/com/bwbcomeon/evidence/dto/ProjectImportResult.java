package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目 Excel 导入结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectImportResult {
    private int total;
    /** 新增项目数 */
    private int inserted;
    /** 覆盖更新已有项目数 */
    private int updated;
    /** 未发生变化而跳过的项目数 */
    private int skipped;
    /** 导入错误明细 */
    private List<RowError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        private int row;
        private String code;
        private String message;
    }
}
