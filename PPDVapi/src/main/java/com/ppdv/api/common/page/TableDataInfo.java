package com.ppdv.api.common.page;

import java.io.Serializable;
import java.util.List;

/**
 * 若依分页表格返回：{ "code":200, "msg":"...", "rows":[...], "total":N }。
 */
public class TableDataInfo implements Serializable {

    private long total;
    private List<?> rows;
    private int code;
    private String msg;

    public TableDataInfo() {
    }

    public TableDataInfo(List<?> rows, long total) {
        this.rows = rows;
        this.total = total;
        this.code = 200;
        this.msg = "查询成功";
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<?> getRows() {
        return rows;
    }

    public void setRows(List<?> rows) {
        this.rows = rows;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
