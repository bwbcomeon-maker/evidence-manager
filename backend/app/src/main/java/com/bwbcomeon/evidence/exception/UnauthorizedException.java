package com.bwbcomeon.evidence.exception;

/**
 * 未登录 / 鉴权失败 → 401
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
