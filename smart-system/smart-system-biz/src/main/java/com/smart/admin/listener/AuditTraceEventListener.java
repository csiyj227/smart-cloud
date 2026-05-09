package com.smart.admin.listener;

import com.smart.admin.api.dto.SysLogDTO;
import com.smart.admin.service.SysLogService;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.log.event.AuditTraceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link AuditTraceEvent}，将操作日志异步持久化到 sys_log 表。
 *
 * <p>使用 {@link Async} 异步处理，避免拖慢业务接口响应。
 * <p>注意：createBy 和 tenantId 已在 {@link com.smart.common.log.aspect.OpLogAspect}
 * 的请求线程中提取并存入 AuditTraceEvent，此处直接使用，不依赖异步线程中的 SecurityContext。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTraceEventListener {

    private final SysLogService sysLogService;

    @Async
    @EventListener
    public void handle(AuditTraceEvent event) {
        // 将事件中携带的 tenantId 设入当前异步线程的 TenantContext，
        // 确保 MyBatis-Plus 租户拦截器在 INSERT 时能正确追加 tenant_id。
        Long tenantId = event.getTenantId();
        if (tenantId != null) {
            TenantContext.set(tenantId);
        }
        try {
            SysLogDTO logDTO = new SysLogDTO();
            logDTO.setLogType("0");
            logDTO.setTitle(event.getTitle());
            logDTO.setServiceId(event.getServiceId());
            logDTO.setRemoteAddr(event.getRemoteAddr());
            logDTO.setRequestUri(event.getRequestUri());
            logDTO.setHttpMethod(event.getHttpMethod());
            logDTO.setClassName(event.getClassName());
            logDTO.setMethodName(event.getMethodName());
            logDTO.setParams(event.getParams());
            logDTO.setExecutionTime(event.getExecutionTime());
            logDTO.setException(event.getException());
            logDTO.setTraceId(event.getTraceId());
            logDTO.setCreateBy(event.getCreateBy());
            logDTO.setTenantId(tenantId);

            sysLogService.saveLog(logDTO);
        } catch (Exception e) {
            log.warn("Failed to persist operation log: {}", event.getTitle(), e);
        } finally {
            TenantContext.clear();
        }
    }
}
