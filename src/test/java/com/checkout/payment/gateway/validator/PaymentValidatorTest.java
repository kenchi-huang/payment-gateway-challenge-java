package com.checkout.payment.gateway.validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentValidatorTest {

  private PaymentValidator validator;
  private YearMonth now;

  @BeforeEach
  void setUp() {
    validator = new PaymentValidator();
    now = YearMonth.now();
  }

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4242424242424241");
    request.setExpiryMonth(now.getMonthValue());
    request.setExpiryYear(now.getYear() + 1);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  @Test
  void whenRequestIsValidThenNoErrorsAreReturned() {
    List<String> errors = validator.validate(validRequest());
    assertTrue(errors.isEmpty());
  }

  @Test
  void whenRequestIsNullThenErrorIsReturned() {
    List<String> errors = validator.validate(null);
    assertFalse(errors.isEmpty());
  }

  // --- Card Number ---

  @ParameterizedTest
  @ValueSource(strings = {"12345678901234", "1234567890123456789"})
  void whenCardNumberIs14To19DigitsThenValid(String cardNumber) {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(cardNumber);

    List<String> errors = validator.validate(request);
    assertTrue(errors.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "1234567890123", "12345678901234567890", "1234567890123a", "1234-5678-9012-3456"})
  void whenCardNumberIsInvalidThenErrorIsReturned(String cardNumber) {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(cardNumber);

    List<String> errors = validator.validate(request);
    assertFalse(errors.isEmpty());
  }

  // --- Expiry Month & Year ---

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 13})
  void whenExpiryMonthIsOutOfRangeThenErrorIsReturned(int month) {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(month);

    List<String> errors = validator.validate(request);
    assertFalse(errors.isEmpty());
  }

  @Test
  void whenExpiryIsCurrentMonthAndYearThenValid() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(now.getMonthValue());
    request.setExpiryYear(now.getYear());

    List<String> errors = validator.validate(request);
    assertTrue(errors.isEmpty());
  }

  @Test
  void whenExpiryIsInPastYearThenErrorIsReturned() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(12);
    request.setExpiryYear(now.getYear() - 1);

    List<String> errors = validator.validate(request);
    assertFalse(errors.isEmpty());
  }

  @Test
  void whenExpiryIsPreviousMonthInCurrentYearThenErrorIsReturned() {
    YearMonth lastMonth = now.minusMonths(1);
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(lastMonth.getMonthValue());
    request.setExpiryYear(lastMonth.getYear());

    List<String> errors = validator.validate(request);
    assertFalse(errors.isEmpty());
  }

  // --- Currency ---

  @ParameterizedTest
  @ValueSource(strings = {"USD", "GBP", "EUR"})
  void whenCurrencyIsSupportedThenValid(String currency) {
    PostPaymentRequest request = validRequest();
    request.setCurrency(currency);

    List<String> errors = validator.validate(request);
    assertTrue(errors.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"US", "USDT", "JPY", "gbp"})
  void whenCurrencyIsInvalidOrUnsupportedThenErrorIsReturned(String currency) {
    PostPaymentRequest request = validRequest();
    request.setCurrency(currency);

    List<String> errors = validator.validate(request);
    assertFalse(errors.isEmpty());
  }

  // --- Amount ---

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -500})
  void whenAmountIsNotPositiveThenErrorIsReturned(int amount) {
    PostPaymentRequest request = validRequest();
    request.setAmount(amount);

    List<String> errors = validator.validate(request);
    assertFalse(errors.isEmpty());
  }

  // --- CVV ---

  @ParameterizedTest
  @ValueSource(strings = {"012", "123", "0123", "9999"})
  void whenCvvIs3Or4DigitsThenValid(String cvv) {
    PostPaymentRequest request = validRequest();
    request.setCvv(cvv);

    List<String> errors = validator.validate(request);
    assertTrue(errors.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "12", "12345", "12a"})
  void whenCvvIsInvalidThenErrorIsReturned(String cvv) {
    PostPaymentRequest request = validRequest();
    request.setCvv(cvv);

    List<String> errors = validator.validate(request);
    assertFalse(errors.isEmpty());
  }
}
