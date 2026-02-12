package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 项目归档操作结果：成功或门禁失败（含 block 详情）
 */
@Data
public class ArchiveResult {
    private boolean success;
    private ArchiveBlockVO block;

    public static ArchiveResult ok() {
        ArchiveResult r = new ArchiveResult();
        r.setSuccess(true);
        return r;
    }

    public static ArchiveResult fail(ArchiveBlockVO block) {
        ArchiveResult r = new ArchiveResult();
        r.setSuccess(false);
        r.setBlock(block);
        return r;
    }
}
