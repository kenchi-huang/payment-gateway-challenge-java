package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.List;
import java.util.UUID;
import com.checkout.payment.gateway.validator.PaymentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final PaymentValidator paymentValidator;
  private final AcquiringBankClient acquiringBankClient;

  public PaymentGatewayService(
      PaymentsRepository paymentsRepository,
      PaymentValidator paymentValidator,
      AcquiringBankClient acquiringBankClient
  ) {
    this.paymentsRepository = paymentsRepository;
    this.paymentValidator = paymentValidator;
    this.acquiringBankClient = acquiringBankClient;
  }

  public PaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    UUID paymentId = UUID.randomUUID();
    LOG.debug("Processing payment with ID {}", paymentId);

    List<String> errors = paymentValidator.validate(paymentRequest);
    if (!errors.isEmpty()) {
      throw new PaymentRejectedException(errors);
    }

    BankPaymentRequest bankRequest = new BankPaymentRequest(
        paymentRequest.getCardNumber(),
        paymentRequest.getExpiryDate(),
        paymentRequest.getCurrency(),
        paymentRequest.getAmount(),
        paymentRequest.getCvv()
    );
    BankPaymentResponse response = acquiringBankClient.processPayment(bankRequest);

    PaymentResponse paymentResponse = bankResponseToPaymentResponse(paymentRequest, paymentId, response);
    paymentsRepository.add(paymentResponse);

    return paymentResponse;
  }

  private static PaymentResponse bankResponseToPaymentResponse(PostPaymentRequest paymentRequest,
      UUID paymentId, BankPaymentResponse response) {
    return new PaymentResponse(
        paymentId,
        response.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
        paymentRequest.getCardNumberLastFour(),
        paymentRequest.getExpiryMonth(),
        paymentRequest.getExpiryYear(),
        paymentRequest.getCurrency(),
        paymentRequest.getAmount()
    );
  }
}
