package com.vidego.common.result;

import java.util.List;
import lombok.Data;

@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int size;

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
