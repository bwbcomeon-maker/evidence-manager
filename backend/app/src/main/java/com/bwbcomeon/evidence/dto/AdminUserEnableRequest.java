package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 管理员-启用/禁用请求
 */
@Data
public class AdminUserEnableRequest {
    /** 是否启用 */
    private Boolean enabled;
}
