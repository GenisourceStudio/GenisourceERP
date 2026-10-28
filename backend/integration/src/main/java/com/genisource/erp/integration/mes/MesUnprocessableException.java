package com.genisource.erp.integration.mes;

import com.genisource.erp.platform.common.ApiException;

/**
 * MES sent something the ERP understood but will not accept.
 *
 * <p>Distinct from a plain bad request: an over-reported completion is a disagreement
 * between two systems about how many were made, and whoever reads the log needs to
 * see that it is not a malformed message but a real conflict.
 */
public class MesUnprocessableException extends RuntimeException {

    public MesUnprocessableException(String message) {
        super(message);
    }

    /** The wire shape: 422, with the reason. */
    public ApiException asApiException() {
        return ApiException.badRequest(getMessage());
    }
}
