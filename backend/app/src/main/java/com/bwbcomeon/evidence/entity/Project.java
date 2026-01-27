package com.bwbcomeon.evidence.entity;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ???
 */
@Data
public class Project {
    /**
     * ??ID
     */
    private Long id;

    /**
     * ????
     */
    private String code;

    /**
     * ????
     */
    private String name;

    /**
     * ????
     */
    private String description;

    /**
     * ?????active / archived
     */
    private String status;

    /**
     * ???
     */
    private UUID createdBy;

    /**
     * ????
     */
    private OffsetDateTime createdAt;

    /**
     * ????
     */
    private OffsetDateTime updatedAt;
}
