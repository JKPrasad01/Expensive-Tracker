package com.expensetracker.security;

public final class SecurityConstants {

    public static final String BEARER_PREFIX = "Bearer ";

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_JTI = "jti";

    private SecurityConstants() {
    }
}
