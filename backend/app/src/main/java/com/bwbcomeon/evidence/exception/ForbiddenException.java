package com.bwbcomeon.evidence.exception;

/**
 * 无权限（如非管理员访问 /api/admin/**）→ 403
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
