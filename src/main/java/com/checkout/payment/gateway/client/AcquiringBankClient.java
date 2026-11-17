package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AcquiringBankClient {

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public AcquiringBankClient(
      RestTemplate restTemplate,
      @Value("${bank.simulator.url}")
      String bankUrl
  ) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public BankPaymentResponse processPayment(BankPaymentRequest request) {
    try {
      ResponseEntity<BankPaymentResponse> response =
          restTemplate.postForEntity(bankUrl, request, BankPaymentResponse.class);
      if (response.getBody() == null) {
        throw new BankUnavailableException("Empty response from acquiring bank");
      }
      return response.getBody();
    } catch (RestClientException ex) {
      throw new BankUnavailableException("Failed to communicate with acquiring bank");
    }
  }
}
