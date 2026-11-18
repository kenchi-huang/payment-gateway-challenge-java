package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validator.PaymentValidator;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private PaymentValidator paymentValidator;

  @Mock
  private AcquiringBankClient acquiringBankClient;

  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  private PostPaymentRequest validRequest() {
    YearMonth future = YearMonth.now().plusYears(1);
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(future.getYear());
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  @Test
  void whenValidPaymentIsAuthorizedThenSavedAndReturnedWithAuthorizedStatus() {
    PostPaymentRequest request = validRequest();
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(acquiringBankClient.processPayment(any(BankPaymentRequest.class)))
        .thenReturn(new BankPaymentResponse(true, "auth-123"));

    PaymentResponse response = paymentGatewayService.processPayment(request);

    assertNotNull(response.id());
    assertEquals(PaymentStatus.AUTHORIZED, response.status());
    assertEquals("8877", response.cardNumberLastFour());
    assertEquals(request.getExpiryMonth(), response.expiryMonth());
    assertEquals(request.getExpiryYear(), response.expiryYear());
    assertEquals("GBP", response.currency());
    assertEquals(100, response.amount());

    ArgumentCaptor<BankPaymentRequest> bankCaptor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(acquiringBankClient).processPayment(bankCaptor.capture());
    assertEquals("04/" + request.getExpiryYear(), bankCaptor.getValue().expiryDate());
    assertEquals("2222405343248877", bankCaptor.getValue().cardNumber());

    verify(paymentsRepository).add(response);
  }

  @Test
  void whenValidPaymentIsDeclinedThenSavedAndReturnedWithDeclinedStatus() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("2222405343248878");
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(acquiringBankClient.processPayment(any(BankPaymentRequest.class)))
        .thenReturn(new BankPaymentResponse(false, ""));

    PaymentResponse response = paymentGatewayService.processPayment(request);

    assertNotNull(response.id());
    assertEquals(PaymentStatus.DECLINED, response.status());
    assertEquals("8878", response.cardNumberLastFour());
    verify(paymentsRepository).add(response);
  }

  @Test
  void whenPaymentIsInvalidThenRejectedExceptionThrownAndBankNotCalled() {
    PostPaymentRequest request = validRequest();
    when(paymentValidator.validate(request)).thenReturn(List.of("Invalid card number"));

    PaymentRejectedException ex = assertThrows(
        PaymentRejectedException.class,
        () -> paymentGatewayService.processPayment(request)
    );

    assertEquals(List.of("Invalid card number"), ex.getErrors());
    verify(acquiringBankClient, never()).processPayment(any());
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void whenBankIsUnavailableThenExceptionPropagatesAndPaymentIsNotSaved() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("2222405343248870");
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(acquiringBankClient.processPayment(any(BankPaymentRequest.class)))
        .thenThrow(new BankUnavailableException("Bank simulator unavailable"));

    assertThrows(
        BankUnavailableException.class,
        () -> paymentGatewayService.processPayment(request)
    );

    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void whenPaymentExistsThenGetPaymentByIdReturnsIt() {
    UUID id = UUID.randomUUID();
    PaymentResponse expected = new PaymentResponse(
        id,
        PaymentStatus.AUTHORIZED,
        "4321",
        12,
        2026,
        "GBP",
        100
    );
    when(paymentsRepository.get(id)).thenReturn(Optional.of(expected));

    PaymentResponse actual = paymentGatewayService.getPaymentById(id);
    assertEquals(expected, actual);
  }

  @Test
  void whenPaymentDoesNotExistThenGetPaymentByIdThrowsException() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThrows(EventProcessingException.class, () -> paymentGatewayService.getPaymentById(id));
  }
}
