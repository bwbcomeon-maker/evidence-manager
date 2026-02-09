package com.bwbcomeon.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证当前用户密码（用于修改密码前先校验原密码）
 */
@Data
public class VerifyPasswordRequest {
    /** 当前密码 */
    @NotBlank(message = "请输入原密码")
    private String password;
}
