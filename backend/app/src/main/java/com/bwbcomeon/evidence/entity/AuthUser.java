package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 绯荤粺鐢ㄦ埛琛? */
@Data
public class AuthUser {
    /**
     * 鐢ㄦ埛ID
     */
    private UUID id;

    /**
     * 鐧诲綍鍚?     */
    private String username;

    /**
     * 鏄剧ず鍚嶇О
     */
    private String displayName;

    /**
     * 閭
     */
    private String email;

    /**
     * 鏄惁鍚敤
     */
    private Boolean isActive;

    /**
     * 鍒涘缓鏃堕棿
     */
    private OffsetDateTime createdAt;
}