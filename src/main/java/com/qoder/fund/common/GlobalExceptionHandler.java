package com.qoder.fund.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBind(BindException e) {
        log.warn("参数绑定失败: {}", e.getMessage());
        return Result.error(400, "参数格式错误");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Result<?> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("并发冲突，数据已被修改");
        return Result.error(409, "数据已被其他操作修改，请刷新后重试");
    }

    @ExceptionHandler(DataAccessException.class)
    public Result<?> handleDataAccess(DataAccessException e) {
        log.error("数据库操作异常", e);
        return Result.error(500, "数据库操作失败");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntime(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.error(500, "服务器内部错误: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("未知异常", e);
        return Result.error(500, "服务器内部错误");
    }
}
