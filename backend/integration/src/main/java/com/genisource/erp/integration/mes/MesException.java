package com.genisource.erp.integration.mes;

/**
 * Something went wrong talking to MES.
 *
 * <p>Kept separate from the platform's API exception because this one is not the
 * caller's fault: the caller asked the ERP to do its job, and the ERP could not
 * reach another system. The message never reaches a person; it goes in the outbox
 * with the attempt count, and a retry tries again.
 */
public class MesException extends RuntimeException {

    public MesException(String message) {
        super(message);
    }

    public MesException(String message, Throwable cause) {
        super(message, cause);
    }
}
