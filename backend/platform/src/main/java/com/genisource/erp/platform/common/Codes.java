package com.genisource.erp.platform.common;

import java.util.Locale;

/**
 * Business-key normalisation.
 *
 * <p>Codes are compared case-insensitively because PostgreSQL unique indexes are not:
 * without folding, {@code abc-01} and {@code ABC-01} would be two different materials
 * and someone would eventually book stock against the wrong one. Upper case is also the
 * convention floor staff expect on a printed traveller.
 */
public final class Codes {

    private Codes() {
    }

    /** Trims and upper-cases a code, or returns {@code null} for blank input. */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
