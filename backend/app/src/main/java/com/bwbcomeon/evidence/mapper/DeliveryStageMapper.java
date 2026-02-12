package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.DeliveryStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeliveryStageMapper {
    List<DeliveryStage> selectAll();

    DeliveryStage selectById(@Param("id") Long id);

    DeliveryStage selectByCode(@Param("code") String code);
}
