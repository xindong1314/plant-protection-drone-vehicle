package com.ppdv.api.common;

import java.util.HashMap;
import java.util.Objects;

/**
 * 若依风格统一返回体：{ "code":200, "msg":"...", "data":... }。
 * 成功码 200，失败码 500（或自定义）。若依前端 axios 拦截器以 code===200 判成功。
 */
public class AjaxResult extends HashMap<String, Object> {

    public static final String CODE_TAG = "code";
    public static final String MSG_TAG = "msg";
    public static final String DATA_TAG = "data";

    public static final int SUCCESS = 200;
    public static final int WARN = 601;
    public static final int ERROR = 500;

    public AjaxResult() {
    }

    public AjaxResult(int code, String msg) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }

    public AjaxResult(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    public static AjaxResult success() {
        return new AjaxResult(SUCCESS, "操作成功");
    }

    public static AjaxResult success(Object data) {
        return new AjaxResult(SUCCESS, "操作成功", data);
    }

    public static AjaxResult success(String msg) {
        return new AjaxResult(SUCCESS, msg);
    }

    public static AjaxResult success(String msg, Object data) {
        return new AjaxResult(SUCCESS, msg, data);
    }

    public static AjaxResult warn(String msg) {
        return new AjaxResult(WARN, msg);
    }

    public static AjaxResult error() {
        return new AjaxResult(ERROR, "操作失败");
    }

    public static AjaxResult error(String msg) {
        return new AjaxResult(ERROR, msg);
    }

    public static AjaxResult error(int code, String msg) {
        return new AjaxResult(code, msg);
    }

    /** 根据受影响行数返回成功/失败（若依常用 toAjax）。 */
    public static AjaxResult toAjax(int rows) {
        return rows > 0 ? success() : error();
    }

    public static AjaxResult toAjax(boolean result) {
        return result ? success() : error();
    }

    public boolean isSuccess() {
        return Objects.equals(SUCCESS, this.get(CODE_TAG));
    }

    @Override
    public AjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
