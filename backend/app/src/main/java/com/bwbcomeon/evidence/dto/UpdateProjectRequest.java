package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 更新项目请求（部分字段）
 */
@Data
public class UpdateProjectRequest {
    /** 是否含采购（影响项目启动阶段「项目前期产品比测报告」是否必填） */
    private Boolean hasProcurement;
}
