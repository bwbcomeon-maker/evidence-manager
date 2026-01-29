package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重置密码返回：一次性明文，供前端展示
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordVO {
    /** 新密码（明文，仅返回一次） */
    private String newPassword;
}
