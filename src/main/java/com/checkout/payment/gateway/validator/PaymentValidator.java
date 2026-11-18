package com.checkout.payment.gateway.validator;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.springframework.stereotype.Component;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PaymentValidator {
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "GBP", "EUR");
  private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^\\d{14,19}$");
  private static final Pattern CVV_PATTERN = Pattern.compile("^\\d{3,4}$");

  public List<String> validate(PostPaymentRequest request) {
    if (request == null) {
      return List.of("Request must not be null");
    }

    List<String> errors = new ArrayList<>();

    if (request.getCardNumber() == null || !CARD_NUMBER_PATTERN.matcher(request.getCardNumber()).matches()) {
      errors.add("Invalid card number");
    }

    if (request.getExpiryMonth() >= 1 && request.getExpiryMonth() <= 12 && request.getExpiryYear() <= 9999 && request.getExpiryYear() >= 0) {
      if (YearMonth.of(request.getExpiryYear(), request.getExpiryMonth()).isBefore(YearMonth.now())) {
        errors.add("Card is expired");
      }
    } else {
      errors.add("Invalid expiry month or year");
    }

    if (request.getCurrency() == null || !SUPPORTED_CURRENCIES.contains(request.getCurrency())) {
      errors.add("Invalid currency");
    }

    if (request.getAmount() <= 0) {
      errors.add("Amount cannot be less or equal to 0");
    }

    if (request.getCvv() == null || !CVV_PATTERN.matcher(request.getCvv()).matches()) {
      errors.add("Invalid CVV");
    }

    return errors;
  }
}
