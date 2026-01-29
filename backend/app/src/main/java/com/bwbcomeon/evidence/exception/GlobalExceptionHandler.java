package com.bwbcomeon.evidence.exception;

import com.bwbcomeon.evidence.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        logger.warn("Business exception: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 未登录 / 鉴权失败 → 401 */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleUnauthorized(UnauthorizedException e) {
        logger.warn("Unauthorized: {}", e.getMessage());
        return Result.error(401, e.getMessage());
    }

    /** 无权限（如非管理员）→ 403 */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleForbidden(ForbiddenException e) {
        logger.warn("Forbidden: {}", e.getMessage());
        return Result.error(403, e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("Illegal argument: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        logger.error("Unexpected error", e);
        return Result.error(500, "Internal server error: " + e.getMessage());
    }
}
