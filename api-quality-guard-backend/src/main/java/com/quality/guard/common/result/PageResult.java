package com.quality.guard.common.result;

import lombok.Data;

import java.util.List;

/**
 * 统一分页响应结构
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setCurrent(current);
        result.setSize(size);
        return result;
    }
}
