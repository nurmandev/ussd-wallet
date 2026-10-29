package com.nurudeen.ussd_wallet.common.exception;

import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPhone(InvalidPhoneNumberException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateCustomerException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletAlreadyExists(
            WalletAlreadyExistsException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletNotFound(WalletNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InsufficientWalletFundsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientFunds(
            InsufficientWalletFundsException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(WalletTransferConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletTransferConflict(
            WalletTransferConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(CustomerIdentityUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomerIdentityUnavailable(
            CustomerIdentityUnavailableException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(PinAlreadyConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePinAlreadyConfigured(
            PinAlreadyConfiguredException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PinNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePinNotConfigured(PinNotConfiguredException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidTransactionPinException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPin(InvalidTransactionPinException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(PinVerificationLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePinLocked(PinVerificationLockedException exception) {
        return response(HttpStatus.LOCKED, exception.getMessage());
    }

    @ExceptionHandler({
        WeakPinException.class,
        PinConfirmationMismatchException.class,
        PinReuseException.class
    })
    public ResponseEntity<ApiResponse<Void>> handlePinRequestRejection(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidPinRecoveryAuthorizationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPinRecovery(
            InvalidPinRecoveryAuthorizationException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(OtpRecoveryUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpRecoveryUnavailable(
            OtpRecoveryUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest() {
        return response(HttpStatus.BAD_REQUEST, "Request body is invalid");
    }

    @ExceptionHandler(ResourceCreationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreationFailure(ResourceCreationException exception) {
        LOGGER.error("Customer registration could not be completed ({})",
                exception.getCause() == null
                        ? "unknown"
                        : exception.getCause().getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        LOGGER.error("Unexpected request failure ({})", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(message));
    }
}
