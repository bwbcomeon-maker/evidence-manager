package com.bwbcomeon.evidence.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建项目请求
 */
@Data
public class CreateProjectRequest {
    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String description;
}
