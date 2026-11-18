package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.RejectedPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.List;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.warn("Exception happened: {}", ex.getMessage());
    return new ResponseEntity<>(
        new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND
    );
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<RejectedPaymentResponse> handleException(PaymentRejectedException ex) {
    LOG.warn("Payment rejected: {}", ex.getMessage());
    return new ResponseEntity<>(
        new RejectedPaymentResponse(
            PaymentStatus.REJECTED,
            ex.getErrors()
        ),
        HttpStatus.BAD_REQUEST
    );
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleException(BankUnavailableException ex) {
    LOG.error("Acquiring Bank is unavailable", ex);
    return new ResponseEntity<>(
        new ErrorResponse("Acquiring bank is unavailable"),
        HttpStatus.BAD_GATEWAY
    );
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<RejectedPaymentResponse> handleException(HttpMessageNotReadableException ex) {
    LOG.warn("Malformed payment request payload");
    return new ResponseEntity<>(
        new RejectedPaymentResponse(
            PaymentStatus.REJECTED,
            List.of("Invalid request payload")
        ),
        HttpStatus.BAD_REQUEST
    );
  }
}
