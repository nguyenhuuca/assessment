package com.canhlabs.funnyapp.exception;

import com.canhlabs.funnyapp.dto.webapi.ResultErrorInfo;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    static final String KEY_VALIDATION = "error.validation.failed";
    static final String KEY_MALFORMED_JSON = "error.request.malformedJson";
    static final String KEY_MISSING_PARAM = "error.request.missingParameter";
    static final String KEY_RATE_LIMIT = "error.rateLimit.exceeded";
    static final String KEY_BAD_CREDENTIALS = "INVALID_CREDENTIALS";

    private final MessageSource messageSource;

    public RestExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Resolves {@code key} for the current request locale; falls back to {@code fallback} when absent. */
    private String resolve(String key, Object[] args, String fallback) {
        return messageSource.getMessage(key, args, fallback, LocaleContextHolder.getLocale());
    }

    private Error localized(HttpStatus status, String key, String fallback, Object... args) {
        Error error = new Error(status);
        error.setCode(key);
        error.setMessage(resolve(key, args, fallback));
        return error;
    }

    /**
     * Handle MissingServletRequestParameterException. Triggered when a 'required'
     * request parameter is missing.
     */

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(localized(BAD_REQUEST, KEY_MISSING_PARAM,
                ex.getParameterName() + " parameter is missing", ex.getParameterName()));
    }

    /**
     * Handle HttpMediaTypeNotSupportedException. This one triggers when JSON is
     * invalid as well.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(ex.getContentType());
        builder.append(" media type is not supported. Supported media types are ");
        ex.getSupportedMediaTypes().forEach(t -> builder.append(t).append(", "));
        log.error(builder.toString(), ex);
        return buildResponseEntity(
                new Error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, builder.substring(0, builder.length() - 2), ex));
    }

    /**
     * Handle MethodArgumentNotValidException. Triggered when an object fails @Valid
     * validation.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Error apiError = localized(BAD_REQUEST, KEY_VALIDATION, "Validation error");
        apiError.addValidationErrors(ex.getBindingResult().getFieldErrors());
        apiError.addValidationError(ex.getBindingResult().getGlobalErrors());
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(apiError);
    }

    /**
     * Handles javax.validation.ConstraintViolationException. Thrown when @Validated
     * fails.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        log.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResultErrorInfo.builder()
                .message("SYSTEM_ERROR")
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now().toString())
                .build());
    }

    /**
     * Handles EntityNotFoundException. Created to encapsulate errors with more
     * detail than javax.persistence.EntityNotFoundException.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFound(EntityNotFoundException ex) {
        Error apiError = new Error(BAD_REQUEST);
        apiError.setMessage(ex.getMessage());
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(apiError);
    }


    /**
     * Handle HttpMessageNotReadableException. Happens when request JSON is
     * malformed.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(localized(BAD_REQUEST, KEY_MALFORMED_JSON, "Malformed JSON request"));
    }

    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMessageNotWritable(HttpMessageNotWritableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(new Error(HttpStatus.INTERNAL_SERVER_ERROR, "Error writing JSON output", ex));
    }

    /**
     * Handle NoHandlerFoundException.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
      Error apiError = new Error(BAD_REQUEST);
        apiError.setMessage(
                String.format("Could not find the %s method for URL %s", ex.getHttpMethod(), ex.getRequestURL()));
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(apiError);
    }


    /**
     * Handle DataIntegrityViolationException, inspects the cause for different DB
     * causes.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(new Error(HttpStatus.CONFLICT, "SYSTEM_ERROR", ex.getCause()));
    }

    /**
     * Handle Exception, handle generic Exception.class
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Error apiError = new Error(BAD_REQUEST);
        Class<?> classes = ex.getRequiredType();
        String name = classes != null ? classes.getSimpleName() : StringUtils.EMPTY;
        apiError.setMessage(String.format("The parameter '%s' of value '%s' could not be converted to type '%s'",
                ex.getName(), ex.getValue(), name));
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(apiError);
    }

    /**
     * Handle javax.persistence.EntityNotFoundException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        Error apiError = new Error(BAD_REQUEST);
        apiError.setMessage(ex.getMessage());
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(apiError);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Object> handleAuthenticationException(HttpClientErrorException ex) {
        // catch the exception when call API from ro-auth
        log.error(ex.getResponseBodyAsString(), ex);
        JSONObject jsonObjectError = new JSONObject(ex.getResponseBodyAsString());
        return ResponseEntity.status(ex.getStatusCode()).body(jsonObjectError.toMap());
    }

    private ResponseEntity<Object> buildResponseEntity(Error apiError) {
        HashMap<String, Object> errorWrapper = new HashMap<>();
        errorWrapper.put("status", "FAILED");
        errorWrapper.put("error", apiError);
        return ResponseEntity.status(apiError.getStatus()).body(errorWrapper);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
        Error error = localized(BAD_REQUEST, KEY_BAD_CREDENTIALS, "Invalid email address or password");
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(error);
    }


    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleCustomException(CustomException ex) {
        HttpStatus httpStatus = (ex.getStatus() != null) ? ex.getStatus() : BAD_REQUEST;
        String message = ex.getMessageKey() != null
                ? resolve(ex.getMessageKey(), ex.getArgs(), ex.getMessage())
                : ex.getMessage();
        Error error = new Error(httpStatus, ex.getSubCode(), message);
        error.setCode(ex.getMessageKey());
        log.error(ex.getMessage());
        return buildResponseEntity(error);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Object> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn(ex.getMessage());
        return buildResponseEntity(localized(HttpStatus.TOO_MANY_REQUESTS, KEY_RATE_LIMIT, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorizedException(UnauthorizedException ex) {
        Error apiError = new Error(BAD_REQUEST);
        apiError.setMessage(ex.getMessage());
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(apiError);
    }

}
