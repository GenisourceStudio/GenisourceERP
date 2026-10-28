package com.genisource.erp.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 problem details so that clients (and the SPA) can
 * branch on a stable {@code code} instead of parsing prose.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        // A server-side failure is ours, not the caller's: log it with its cause so
        // the log holds the reason instead of only the request that failed.
        if (ex.status().is5xxServerError()) {
            log.error("Server-side failure on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), localize(ex.code(), ex.getMessage()));
        problem.setType(URI.create("urn:genisource:erp:error:" + ex.code().key()));
        problem.setTitle(ex.status().getReasonPhrase());
        problem.setProperty("code", ex.code().key());
        problem.setProperty("path", request.getRequestURI());
        ex.properties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, localize(ErrorCode.VALIDATION_FAILED, "Request validation failed"));
        problem.setType(URI.create("urn:genisource:erp:error:validation_failed"));
        problem.setTitle("Bad Request");
        problem.setProperty("code", ErrorCode.VALIDATION_FAILED.key());
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return securityProblem(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return securityProblem(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, localize(ErrorCode.INTERNAL_ERROR, "Internal server error"));
        problem.setType(URI.create("urn:genisource:erp:error:internal_error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("code", ErrorCode.INTERNAL_ERROR.key());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    private ProblemDetail securityProblem(ErrorCode code, HttpStatus status, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, localize(code, status.getReasonPhrase()));
        problem.setType(URI.create("urn:genisource:erp:error:" + code.key()));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.key());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    private String localize(ErrorCode code, String fallback) {
        return messages.getMessage(code.messageKey(), null, fallback, LocaleContextHolder.getLocale());
    }
}
