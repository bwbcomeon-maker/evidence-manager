package com.bwbcomeon.evidence.mapper;

import com.bwbcomeon.evidence.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统数据字典表 Mapper 接口
 * 
 * @author system
 */
@Mapper
public interface SysDictMapper {
    
    /**
     * 根据ID查询字典
     * 
     * @param id 字典ID
     * @return 字典实体
     */
    SysDict selectById(@Param("id") Long id);
    
    /**
     * 根据字典类型查询字典列表
     * 
     * @param dictType 字典类型
     * @return 字典列表
     */
    List<SysDict> selectByDictType(@Param("dictType") String dictType);
    
    /**
     * 根据字典类型和字典编码查询字典
     * 
     * @param dictType 字典类型
     * @param dictCode 字典编码
     * @return 字典实体
     */
    SysDict selectByDictTypeAndCode(@Param("dictType") String dictType, 
                                     @Param("dictCode") String dictCode);
    
    /**
     * 查询所有字典（分页）
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 字典列表
     */
    List<SysDict> selectAll(@Param("offset") Long offset, @Param("limit") Integer limit);
    
    /**
     * 统计字典总数
     * 
     * @return 字典总数
     */
    Long countAll();
    
    /**
     * 插入字典
     * 
     * @param dict 字典实体
     * @return 影响行数
     */
    int insert(SysDict dict);
    
    /**
     * 更新字典
     * 
     * @param dict 字典实体
     * @return 影响行数
     */
    int update(SysDict dict);
    
    /**
     * 根据ID删除字典
     * 
     * @param id 字典ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
