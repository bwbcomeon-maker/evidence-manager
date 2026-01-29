package com.bwbcomeon.evidence.dto;

import lombok.Data;

/**
 * 登录请求体
 */
@Data
public class LoginRequest {
    /** 登录账号 */
    private String username;
    /** 密码 */
    private String password;
}
