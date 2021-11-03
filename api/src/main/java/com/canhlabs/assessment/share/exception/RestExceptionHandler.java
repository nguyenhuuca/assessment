package com.canhlabs.assessment.share.exception;

import com.canhlabs.assessment.share.ResultErrorInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

import javax.persistence.EntityNotFoundException;
import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
    /**
     * Handle MissingServletRequestParameterException. Triggered when a 'required'
     * request parameter is missing.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleMissingServletRequestParameter(@NonNull MissingServletRequestParameterException ex,
                                                                          @NonNull HttpHeaders headers, @NonNull HttpStatus status, @NonNull WebRequest request) {
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(new Error(BAD_REQUEST, ex.getParameterName() + " parameter is missing", ex));
    }

    /**
     * Handle HttpMediaTypeNotSupportedException. This one triggers when JSON is
     * invalid as well.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(@NonNull HttpMediaTypeNotSupportedException ex,
                                                                     @NonNull HttpHeaders headers, @NonNull HttpStatus status, @NonNull WebRequest request) {
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
    @NonNull
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers, @NonNull HttpStatus status, @NonNull WebRequest request) {
        Error apiError = new Error(BAD_REQUEST);
        apiError.setMessage("Validation error");
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
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers, @NonNull HttpStatus status, @NonNull WebRequest request) {
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(new Error(HttpStatus.BAD_REQUEST, "Malformed JSON request", ex));
    }

    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMessageNotWritable(@NonNull HttpMessageNotWritableException ex,
                                                                  @NonNull HttpHeaders headers, @NonNull HttpStatus status, @NonNull WebRequest request) {
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(new Error(HttpStatus.INTERNAL_SERVER_ERROR, "Error writing JSON output", ex));
    }

    /**
     * Handle NoHandlerFoundException.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleNoHandlerFoundException(@NonNull NoHandlerFoundException ex, @NonNull HttpHeaders headers,
                                                                   @NonNull HttpStatus status, @NonNull WebRequest request) {
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
        Error error = new Error(BAD_REQUEST);
        error.setMessage("Invalid email address or password");
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(error);
    }


    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ResultErrorInfo> handleCustomException(CustomException ex) {
        log.error(ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ex.buildErrorMessage());
    }

}
