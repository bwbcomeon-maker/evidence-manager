package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 更新项目请求（部分字段）
 */
@Data
public class UpdateProjectRequest {
    /** 项目名称（可选传入；传入时不能为空白字符串） */
    private String name;

    /** 项目描述（可选；传入空字符串时视为清空） */
    private String description;

    /** 是否含采购（影响项目启动阶段「项目前期产品比测报告」是否必填） */
    private Boolean hasProcurement;
}
