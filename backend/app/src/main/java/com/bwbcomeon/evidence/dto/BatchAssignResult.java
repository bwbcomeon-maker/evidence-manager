package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量分配结果：成功数、失败数及失败明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchAssignResult {
    private int successCount;
    private int failCount;
    /** 失败项：projectId 或 target 标识 + 原因 */
    private List<String> errors = new ArrayList<>();

    public static BatchAssignResult of(int success, int fail, List<String> errors) {
        return new BatchAssignResult(success, fail, errors != null ? errors : new ArrayList<>());
    }
}
