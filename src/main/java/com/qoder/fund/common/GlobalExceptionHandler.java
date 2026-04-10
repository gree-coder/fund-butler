package com.qoder.fund.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("[{}] 资源不存在: {}", getRequestPath(request), e.getMessage());
        return Result.error(404, "接口不存在: " + request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[{}] 参数错误: {} - {}", getRequestPath(request), e.getClass().getSimpleName(), e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[{}] 参数校验失败: {}", getRequestPath(request), message);
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBind(BindException e, HttpServletRequest request) {
        log.warn("[{}] 参数绑定失败: {}", getRequestPath(request), e.getMessage());
        return Result.error(400, "参数格式错误");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Result<?> handleOptimisticLock(OptimisticLockingFailureException e, HttpServletRequest request) {
        log.warn("[{}] 并发冲突，数据已被修改", getRequestPath(request));
        return Result.error(409, "数据已被其他操作修改，请刷新后重试");
    }

    @ExceptionHandler(DataAccessException.class)
    public Result<?> handleDataAccess(DataAccessException e, HttpServletRequest request) {
        log.error("[{}] 数据库操作异常: {}", getRequestPath(request), e.getMessage(), e);
        return Result.error(500, "数据库操作失败");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntime(RuntimeException e, HttpServletRequest request) {
        log.error("[{}] 运行时异常: {} - {}", getRequestPath(request),
                e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.error(500, "服务器内部错误: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("[{}] 未知异常: {} - {}", getRequestPath(request),
                e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.error(500, "服务器内部错误");
    }

    private String getRequestPath(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString != null ? method + " " + uri + "?" + queryString : method + " " + uri;
    }
}
