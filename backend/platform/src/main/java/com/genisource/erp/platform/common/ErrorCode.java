package com.genisource.erp.platform.common;

/**
 * Machine-readable error codes. Each one maps to a message key under
 * {@code error.<code>} in {@code i18n/messages*.properties}, so the same failure
 * renders in Chinese or English depending on the request locale.
 */
public enum ErrorCode {

    // --- authentication / authorization -------------------------------------
    UNAUTHORIZED("unauthorized"),
    INVALID_CREDENTIALS("invalid_credentials"),
    ACCOUNT_DISABLED("account_disabled"),
    TOKEN_EXPIRED("token_expired"),
    TOKEN_INVALID("token_invalid"),
    FORBIDDEN("forbidden"),

    // --- tenancy -------------------------------------------------------------
    TENANT_NOT_FOUND("tenant_not_found"),
    TENANT_MISSING("tenant_missing"),

    // --- generic -------------------------------------------------------------
    VALIDATION_FAILED("validation_failed"),
    NOT_FOUND("not_found"),
    CONFLICT("conflict"),
    INTERNAL_ERROR("internal_error"),

    // --- master data ---------------------------------------------------------
    DUPLICATE_CODE("duplicate_code"),
    IN_USE("in_use"),
    INVALID_STATE("invalid_state"),
    BOM_CYCLE("bom_cycle");

    private final String key;

    ErrorCode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String messageKey() {
        return "error." + key;
    }
}
