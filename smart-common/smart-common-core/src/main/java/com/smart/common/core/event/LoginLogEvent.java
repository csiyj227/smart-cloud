package com.smart.common.core.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 登录相关事件，用于解耦 smart-auth 与 smart-system-biz。
 *
 * <p>auth 模块负责发布事件（成功 / 失败 / 锁定 / 登出），
 * system-biz 模块通过 {@code @EventListener} 监听并落库 {@code sys_login_log} 与维护在线用户。
 */
@Getter
@ToString(callSuper = false)
public class LoginLogEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型
     */
    private final LoginEventType eventType;

    /**
     * 用户 ID（登录失败 / 锁定时可能为 null）
     */
    private final Long userId;

    /**
     * 用户名
     */
    private final String username;

    /**
     * 租户 ID
     */
    private final Long tenantId;

    /**
     * 客户端 IP
     */
    private final String ip;

    /**
     * 客户端 UserAgent
     */
    private final String userAgent;

    /**
     * 附加消息（错误原因 / 状态描述等）
     */
    private final String msg;

    /**
     * access token（成功时携带；可截断或哈希后保存）
     */
    private final String accessToken;

    public LoginLogEvent(Object source,
                         LoginEventType eventType,
                         Long userId,
                         String username,
                         Long tenantId,
                         String ip,
                         String userAgent,
                         String msg,
                         String accessToken) {
        super(source);
        this.eventType = eventType;
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.msg = msg;
        this.accessToken = accessToken;
    }
}
