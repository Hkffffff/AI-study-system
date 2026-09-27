package com.aistudy.common.error;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 7807 {@link ProblemDetail} with an extra {@code code}
 * property (and {@code errors} for validation failures).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return build(ex.getErrorCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.title(), errors);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "服务器内部错误，请查看后端日志", null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorItem(e.getField(), e.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorItem> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(e -> new FieldErrorItem(result.getMethodParameter().getParameterName(),
                                e.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    /** Adds a generic {@code code} to problem details produced by Spring MVC's own handlers. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
            problem.setProperty(CODE, ErrorCode.fromStatus(response.getStatusCode()).name());
        }
        return response;
    }

    private static ProblemDetail validationProblem(List<FieldErrorItem> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ErrorCode.VALIDATION_FAILED.status(), ErrorCode.VALIDATION_FAILED.title());
        problem.setTitle(ErrorCode.VALIDATION_FAILED.title());
        problem.setProperty(CODE, ErrorCode.VALIDATION_FAILED.name());
        problem.setProperty(ERRORS, errors);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> build(ErrorCode code, String detail, List<FieldErrorItem> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty(CODE, code.name());
        if (errors != null) {
            problem.setProperty(ERRORS, errors);
        }
        return ResponseEntity.status(code.status()).body(problem);
    }
}
