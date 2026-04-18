package com.example.aiinterview.common;

import lombok.Data;

@Data
public class Result<T> {

    private int code;
    private T data;
    private String msg;

    private Result(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, data, "success");
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, null, msg);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, null, msg);
    }
}
