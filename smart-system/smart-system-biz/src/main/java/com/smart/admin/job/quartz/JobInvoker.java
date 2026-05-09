package com.smart.admin.job.quartz;

import com.smart.common.core.exception.BusinessException;
import com.smart.common.core.spring.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;

/**
 * 解析 {@code invokeTarget} 字符串并反射调用 Spring Bean 方法。
 *
 * <p>支持格式：
 * <pre>
 *   sysJobInternalTask.cleanLoginLog                    // 无参
 *   sysJobInternalTask.cleanLoginLog(30)                // 内联整型参数
 *   sysJobInternalTask.cleanLoginLog("logs",30)         // 多参数（字符串需引号）
 *   sysJobInternalTask.process({"days":30})             // 单 JSON 参数（建议用 jobParam）
 * </pre>
 *
 * <p>当 {@code jobParam} 不空时，作为 String 单参传入（推荐方式）。
 */
@Slf4j
public final class JobInvoker {

    private JobInvoker() {}

    /**
     * 调用目标方法，返回方法返回值的 toString（写入 sys_job_log.result）。
     *
     * @param invokeTarget 形如 beanName.methodName 或 beanName.methodName(args)
     * @param jobParam     额外参数；非空时作为唯一 String 入参传入
     */
    public static String invoke(String invokeTarget, String jobParam) {
        if (invokeTarget == null || invokeTarget.isBlank()) {
            throw new BusinessException("invokeTarget is empty");
        }

        ParsedTarget parsed = parse(invokeTarget);
        // ApplicationContextProvider 没有单参 getBean(String) 重载，直接走底层 ApplicationContext。
        // 注意：beanName 找不到时 ApplicationContext 会抛 NoSuchBeanDefinitionException，
        // 这里捕获后转成业务异常，避免任务日志里堆栈太深。
        Object bean;
        try {
            bean = ApplicationContextProvider.context().getBean(parsed.beanName);
        } catch (org.springframework.beans.BeansException e) {
            throw new BusinessException("Bean not found: " + parsed.beanName);
        }

        // 解包 AOP 代理拿到真实 class，避免找不到方法
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        // jobParam 非空时优先用单 String 入参方式
        if (jobParam != null && !jobParam.isBlank()) {
            Method method = findMethod(targetClass, parsed.methodName, new Class<?>[]{String.class});
            if (method == null) {
                // 退化到无参（任务方法可能确实不接受参数）
                method = findMethod(targetClass, parsed.methodName, new Class<?>[0]);
                if (method == null) {
                    throw new BusinessException("No matching method: " + parsed.methodName);
                }
                return invokeAndStringify(bean, method, new Object[0]);
            }
            return invokeAndStringify(bean, method, new Object[]{jobParam});
        }

        // 走 invokeTarget 内联参数
        Object[] args = parsed.args;
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        Method method = findMethod(targetClass, parsed.methodName, paramTypes);
        if (method == null) {
            throw new BusinessException("No matching method: " + parsed.methodName
                    + " with " + args.length + " args");
        }
        return invokeAndStringify(bean, method, args);
    }

    private static String invokeAndStringify(Object bean, Method method, Object[] args) {
        try {
            method.setAccessible(true);
            Object ret = method.invoke(bean, args);
            return ret == null ? "OK" : String.valueOf(ret);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Job invoke failed: {}.{}", bean.getClass().getSimpleName(), method.getName(), cause);
            throw new BusinessException("Job invoke failed: " + cause.getMessage());
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) {
        // 精确匹配
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {
            // 退化：按参数个数模糊匹配
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramTypes.length) {
                    return m;
                }
            }
        }
        return null;
    }

    private static ParsedTarget parse(String invokeTarget) {
        String s = invokeTarget.trim();
        int paren = s.indexOf('(');
        String head;
        Object[] args;
        if (paren < 0) {
            head = s;
            args = new Object[0];
        } else {
            head = s.substring(0, paren);
            String argStr = s.substring(paren + 1, s.lastIndexOf(')')).trim();
            args = argStr.isEmpty() ? new Object[0] : parseArgs(argStr);
        }
        int dot = head.lastIndexOf('.');
        if (dot < 0) {
            throw new BusinessException("Invalid invokeTarget: " + invokeTarget);
        }
        ParsedTarget pt = new ParsedTarget();
        pt.beanName = head.substring(0, dot);
        pt.methodName = head.substring(dot + 1);
        pt.args = args;
        return pt;
    }

    /** 简单 CSV 解析：支持 "string"、'string'、整数、小数、true/false */
    private static Object[] parseArgs(String s) {
        // 不解析嵌套 JSON，遇到 { 直接当作整体 String
        java.util.List<Object> out = new java.util.ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ',') { i++; continue; }
            if (c == '"' || c == '\'') {
                int end = s.indexOf(c, i + 1);
                if (end < 0) { out.add(s.substring(i + 1)); break; }
                out.add(s.substring(i + 1, end));
                i = end + 1;
            } else if (c == '{' || c == '[') {
                // 当成完整 JSON 字符串处理（取到匹配的右括号）
                int depth = 0, end = i;
                for (; end < n; end++) {
                    char cc = s.charAt(end);
                    if (cc == '{' || cc == '[') depth++;
                    else if (cc == '}' || cc == ']') { depth--; if (depth == 0) { end++; break; } }
                }
                out.add(s.substring(i, end));
                i = end;
            } else {
                int end = i;
                while (end < n && s.charAt(end) != ',') end++;
                String token = s.substring(i, end).trim();
                out.add(coerce(token));
                i = end;
            }
        }
        return out.toArray();
    }

    private static Object coerce(String t) {
        if ("true".equalsIgnoreCase(t)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(t)) return Boolean.FALSE;
        try { return Integer.valueOf(t); } catch (NumberFormatException ignored) {}
        try { return Long.valueOf(t); } catch (NumberFormatException ignored) {}
        try { return Double.valueOf(t); } catch (NumberFormatException ignored) {}
        return t;
    }

    private static class ParsedTarget {
        String beanName;
        String methodName;
        Object[] args;
    }
}
