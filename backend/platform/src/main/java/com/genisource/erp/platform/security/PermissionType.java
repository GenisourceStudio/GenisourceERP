package com.genisource.erp.platform.security;

/**
 * Permission granularity, checked at different layers:
 * {@link #MENU} and {@link #BUTTON} drive the SPA, {@link #API} guards the endpoint.
 */
public enum PermissionType {
    MENU,
    BUTTON,
    API
}
