package com.genisource.erp.masterdata.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the "copy to a new revision" endpoints shared by BOMs and routings. */
public record VersionRequest(
        @NotBlank(message = "{validation.version.required}")
        @Size(max = 16, message = "{validation.version.size}")
        String version) {
}
