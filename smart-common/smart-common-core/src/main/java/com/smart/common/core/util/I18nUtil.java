package com.smart.common.core.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

/**
 * I18n utility — resolves messages from the shared {@link MessageSource}.
 * <p>
 * Usage:
 * <pre>
 *   I18nUtil.get("error.access.denied")           // → "没有操作权限" (zh) / "Access denied" (en)
 *   I18nUtil.get("error.not.found", "User", "42")  // → "User(42)未找到" (zh) / "User(42) not found" (en)
 * </pre>
 */
public final class I18nUtil {

    private static MessageSource messageSource;

    private I18nUtil() {
    }

    /**
     * Called by {@link com.smart.common.core.config.I18nConfiguration#messageSource()}
     * during bean creation.
     */
    public static void setMessageSource(MessageSource source) {
        messageSource = source;
    }

    /**
     * Resolve a message for the current request locale.
     *
     * @param code message key
     * @param args optional substitution arguments
     * @return resolved message, or the code itself if not found
     */
    public static String get(String code, Object... args) {
        if (messageSource == null) {
            return code;
        }
        return messageSource.getMessage(code, args, code, getLocale());
    }

    /**
     * Determine the current locale.
     * Prefers the servlet-request locale (set by {@code AcceptHeaderLocaleResolver});
     * falls back to {@link LocaleContextHolder} (useful in async / non-web threads).
     */
    private static Locale getLocale() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String langParam = request.getParameter("lang");
                if (langParam != null && !langParam.isBlank()) {
                    return Locale.forLanguageTag(langParam.replace("_", "-"));
                }
            }
        } catch (Exception ignored) {
            // non-web context
        }
        return LocaleContextHolder.getLocale();
    }
}
