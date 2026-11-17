package com.checkout.payment.gateway.exception;

import java.util.List;

public class PaymentRejectedException extends RuntimeException {
  private final List<String> errors;

  public PaymentRejectedException(List<String> errors) {
    this.errors = errors;
  }

  public List<String> getErrors() {
    return this.errors;
  }
}
