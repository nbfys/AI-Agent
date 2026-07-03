package com.example.aiinterview.config;

import com.example.aiinterview.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleIllegalState(IllegalStateException e) {
        log.error("状态异常: {}", e.getMessage());
        return Result.error(500, "服务内部错误");
    }

    @ExceptionHandler(RejectedExecutionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleRejectedExecution(RejectedExecutionException e) {
        log.error("服务过载，请求被拒绝: {}", e.getMessage());
        return Result.error(503, "服务器繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("未预期异常: ", e);
        return Result.error(500, "服务暂时不可用，请稍后重试");
    }
}
