package com.bwbcomeon.evidence.entity;

import lombok.Data;

/**
 * 交付阶段定义表 delivery_stage
 */
@Data
public class DeliveryStage {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
}
