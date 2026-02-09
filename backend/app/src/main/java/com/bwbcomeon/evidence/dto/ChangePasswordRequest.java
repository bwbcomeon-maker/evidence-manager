package com.bwbcomeon.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自助修改密码请求体
 */
@Data
public class ChangePasswordRequest {
    /** 原密码 */
    @NotBlank(message = "请输入原密码")
    private String oldPassword;
    /** 新密码 */
    @NotBlank(message = "请输入新密码")
    private String newPassword;
}
