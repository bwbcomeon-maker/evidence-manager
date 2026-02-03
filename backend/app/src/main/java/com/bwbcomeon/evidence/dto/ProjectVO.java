package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目基本信息（列表/详情/创建响应共用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
}
