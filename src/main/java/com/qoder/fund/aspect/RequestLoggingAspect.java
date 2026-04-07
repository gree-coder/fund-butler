package com.qoder.fund.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 请求日志切面
 * 记录所有 Controller 的请求和响应信息
 */
@Slf4j
@Aspect
@Component
public class RequestLoggingAspect {

    /**
     * 切入点：所有 Controller 类的方法
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：记录请求和响应日志
     */
    @Around("controllerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        HttpServletRequest request = getRequest();
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";
        String queryString = request != null ? request.getQueryString() : null;
        String fullUrl = queryString != null ? uri + "?" + queryString : uri;
        String clientIp = getClientIp(request);

        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // 过滤敏感参数（如密码）
        String argsStr = filterSensitiveArgs(args);

        // 请求日志
        log.info(">>> {} {} [{}] {}.{}() - args: {}",
                method, fullUrl, clientIp,
                className.substring(className.lastIndexOf('.') + 1),
                methodName, argsStr);

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 响应日志
            log.info("<<< {} {} [{}] - {}ms - success",
                    method, fullUrl, clientIp, duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // 异常日志
            log.error("<<< {} {} [{}] - {}ms - ERROR: {}",
                    method, fullUrl, clientIp, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 获取当前请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多级代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 过滤敏感参数
     */
    private String filterSensitiveArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        // 简单实现：限制参数字符串长度
        String argsStr = Arrays.toString(args);
        if (argsStr.length() > 500) {
            argsStr = argsStr.substring(0, 500) + "... (truncated)";
        }
        return argsStr;
    }
}
