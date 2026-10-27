package com.genisource.erp.platform.common;

import jakarta.validation.constraints.NotNull;

/**
 * Body of the status endpoints.
 *
 * <p>Activation is exposed as its own endpoint rather than a field on the update payload
 * so that the "can this be disabled" checks cannot be bypassed by a full-form save.
 */
public record StatusRequest(@NotNull RecordStatus status) {
}
