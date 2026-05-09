package com.smart.admin.listener;

import com.smart.admin.service.OnlineUserService;
import com.smart.admin.service.SysLoginLogService;
import com.smart.common.core.event.LoginEventType;
import com.smart.common.core.event.LoginLogEvent;
import com.smart.common.security.service.SmartUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link LoginLogEvent}，把登录 / 登出 / 失败事件落入 sys_login_log，
 * 并维护 Redis 中的在线用户。
 *
 * <p>使用 {@link Async} 异步处理，避免拖慢登录响应。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogEventListener {

    private final SysLoginLogService sysLoginLogService;
    private final OnlineUserService onlineUserService;

    @Async
    @EventListener
    public void handle(LoginLogEvent event) {
        try {
            LoginEventType type = event.getEventType();
            switch (type) {
                case LOGIN_SUCCESS -> handleLoginSuccess(event);
                case LOGIN_FAILURE -> sysLoginLogService.recordLoginFailure(
                        event.getUsername(), event.getTenantId(),
                        event.getIp(), event.getUserAgent(), event.getMsg());
                case ACCOUNT_LOCKED -> sysLoginLogService.recordAccountLocked(
                        event.getUsername(), event.getTenantId(),
                        event.getIp(), event.getUserAgent());
                case LOGOUT -> handleLogout(event);
                default -> log.warn("Unknown login event type: {}", type);
            }
        } catch (Exception e) {
            // 日志写入失败不能影响主流程
            log.warn("Failed to persist login log event: {}", event, e);
        }
    }

    private void handleLoginSuccess(LoginLogEvent event) {
        sysLoginLogService.recordLoginSuccess(
                event.getUserId(), event.getUsername(), event.getTenantId(),
                event.getIp(), event.getUserAgent());

        // 维护在线用户：source 是 SmartUser 即可写入 Redis
        // 由于事件发布发生在 token 生成之前（password grant 父类 flow），accessToken 一般为 null，
        // 用 username + 登录时间合成一个临时 token 标识，避免漏写。
        // 真实 token 后续在 OAuth2TokenGenerator 阶段如果有需要可以再发一个 TokenIssuedEvent 增强，
        // 当前能保证"在线用户列表里能看到登录的人 + 强退按 userId 生效"已经满足业务诉求。
        if (event.getSource() instanceof SmartUser smartUser) {
            String tokenForOnline = event.getAccessToken();
            if (tokenForOnline == null || tokenForOnline.isEmpty()) {
                tokenForOnline = "session:" + smartUser.getTenantId() + ":" + smartUser.getUserId()
                        + ":" + System.currentTimeMillis();
            }
            onlineUserService.saveOnlineUser(tokenForOnline, smartUser, event.getIp(), event.getUserAgent());
        }
    }

    private void handleLogout(LoginLogEvent event) {
        sysLoginLogService.recordLogout(
                event.getUserId(), event.getUsername(), event.getTenantId(),
                event.getIp(), event.getUserAgent());
        onlineUserService.removeOnlineUser(event.getTenantId(), event.getUserId());
    }
}
