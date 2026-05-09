package com.smart.common.core.auth;

/**
 * OAuth2 grant type identifiers.
 */
public final class GrantTypes {

    private GrantTypes() {
    }

    public static final String PASSWORD = "password";
    public static final String SMS = "sms";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String AUTHORIZATION_CODE = "authorization_code";
}