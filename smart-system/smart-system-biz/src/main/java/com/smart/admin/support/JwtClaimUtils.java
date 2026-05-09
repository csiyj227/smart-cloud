package com.smart.admin.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;

public final class JwtClaimUtils {

    private JwtClaimUtils() {
    }

    public static JwtAuthenticationToken asJwtAuth(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken;
        }
        throw new IllegalStateException("Current authentication is not JwtAuthenticationToken");
    }

    public static Long getLong(Authentication authentication, String claim) {
        return asLong(asJwtAuth(authentication).getToken().getClaim(claim));
    }

    public static String getString(Authentication authentication, String claim) {
        Object value = asJwtAuth(authentication).getToken().getClaim(claim);
        return value != null ? String.valueOf(value) : null;
    }

    public static String getString(Authentication authentication, String claim, String defaultValue) {
        String value = getString(authentication, claim);
        return value != null ? value : defaultValue;
    }

    public static Map<String, Object> getClaims(Authentication authentication) {
        return asJwtAuth(authentication).getToken().getClaims();
    }

    public static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            return Long.parseLong(str);
        }
        return null;
    }
}
