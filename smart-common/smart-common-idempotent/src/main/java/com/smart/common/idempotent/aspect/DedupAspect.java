package com.smart.common.idempotent.aspect;

import com.smart.common.core.exception.BusinessException;
import com.smart.common.idempotent.annotation.Dedup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Aspect that enforces idempotency by checking Redis for duplicate request keys.
 *
 * 通过检查 Redis 中的重复请求键来强制幂等性的切面。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DedupAspect {

    private static final String DEDUP_KEY_PREFIX = "smart:dedup:";
    private final RedissonClient redissonClient;

    @Around("@annotation(com.smart.common.idempotent.annotation.Dedup)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Dedup dedup = method.getAnnotation(Dedup.class);

        String dedupKey = buildKey(dedup, method, joinPoint.getArgs());
        RMapCache<String, String> mapCache = redissonClient.getMapCache(DEDUP_KEY_PREFIX + dedupKey);

        String previous = mapCache.putIfAbsent(dedupKey, "1", dedup.duration(), dedup.timeUnit());
        if (previous != null) {
            log.warn("Duplicate request blocked: key={}", dedupKey);
            throw new BusinessException(dedup.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            mapCache.remove(dedupKey);
            throw ex;
        }
    }

    private String buildKey(Dedup dedup, Method method, Object[] args) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(method.getDeclaringClass().getSimpleName())
                .append(":")
                .append(method.getName());

        if (StringUtils.hasText(dedup.key())) {
            ExpressionParser parser = new SpelExpressionParser();
            EvaluationContext context = new StandardEvaluationContext();
            String[] paramNames = getParameterNames(method);
            for (int i = 0; i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            String spelResult = parser.parseExpression(dedup.key()).getValue(context, String.class);
            keyBuilder.append(":").append(spelResult);
        } else {
            for (Object arg : args) {
                keyBuilder.append(":").append(arg != null ? arg.toString() : "null");
            }
        }

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            keyBuilder.append(":").append(request.getRequestURI());
        }

        return keyBuilder.toString();
    }

    private String[] getParameterNames(Method method) {
        return java.util.Arrays.stream(method.getParameters())
                .map(java.lang.reflect.Parameter::getName)
                .toArray(String[]::new);
    }
}