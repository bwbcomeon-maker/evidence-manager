package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.EvidenceVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 证据版本表 Mapper
 */
@Mapper
public interface EvidenceVersionMapper {
    /**
     * 根据ID查询版本记录
     */
    EvidenceVersion selectById(@Param("id") Long id);

    /**
     * 根据证据ID查询版本列表
     */
    List<EvidenceVersion> selectByEvidenceId(@Param("evidenceId") Long evidenceId);

    /**
     * 根据项目ID查询版本列表
     */
    List<EvidenceVersion> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 查询证据的最大版本号
     * @param evidenceId 证据ID
     * @return 最大版本号，如果没有版本记录则返回null
     */
    Integer selectMaxVersionNoByEvidenceId(@Param("evidenceId") Long evidenceId);

    /**
     * 插入版本记录
     */
    int insert(EvidenceVersion evidenceVersion);

    /**
     * 更新版本记录
     */
    int update(EvidenceVersion evidenceVersion);

    /**
     * 根据ID删除版本记录
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据证据ID查询最新版本（version_no最大的版本）
     * @param evidenceId 证据ID
     * @return 最新版本记录，如果没有版本记录则返回null
     */
    EvidenceVersion selectLatestVersionByEvidenceId(@Param("evidenceId") Long evidenceId);

    /**
     * 批量查询多个证据的最新版本
     * @param evidenceIds 证据ID列表
     * @return 最新版本记录列表
     */
    List<EvidenceVersion> selectLatestVersionsByEvidenceIds(@Param("evidenceIds") List<Long> evidenceIds);
}
