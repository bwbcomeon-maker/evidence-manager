package com.bwbcomeon.evidence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页结构：{ total, records, page, pageSize }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /** 总条数 */
    private long total;
    /** 当前页数据 */
    private List<T> records;
    /** 当前页码（从 1 开始） */
    private int page;
    /** 每页条数 */
    private int pageSize;
}
