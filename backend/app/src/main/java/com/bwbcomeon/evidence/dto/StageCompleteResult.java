package com.bwbcomeon.evidence.dto;

import lombok.Data;
import java.util.List;

/**
 * 阶段完成操作结果：成功或失败（含缺失项）
 */
@Data
public class StageCompleteResult {
    private boolean success;
    private String message;
    private List<BlockedByItemVO> missingItems;

    public static StageCompleteResult ok() {
        StageCompleteResult r = new StageCompleteResult();
        r.setSuccess(true);
        return r;
    }

    public static StageCompleteResult fail(String message, List<BlockedByItemVO> missingItems) {
        StageCompleteResult r = new StageCompleteResult();
        r.setSuccess(false);
        r.setMessage(message);
        r.setMissingItems(missingItems);
        return r;
    }
}
