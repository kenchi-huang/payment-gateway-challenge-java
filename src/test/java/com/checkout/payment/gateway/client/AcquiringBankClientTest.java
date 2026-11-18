package com.checkout.payment.gateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AcquiringBankClientTest {

  private static final String BANK_URL = "http://localhost:8080/payments";

  private MockRestServiceServer mockServer;
  private AcquiringBankClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    client = new AcquiringBankClient(restTemplate, BANK_URL);
  }

  @Test
  void whenBankReturnsAuthorizedThenResponseIsMappedAndRequestUsesExpectedJsonFormat() {
    mockServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.card_number").value("2222405343248877"))
        .andExpect(jsonPath("$.expiry_date").value("04/2027"))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$.cvv").value("012"))
        .andRespond(withSuccess(
            """
            {
              "authorized": true,
              "authorization_code": "0bb07405-6d44-4b50-a14f-7ae0beff13ad"
            }
            """,
            MediaType.APPLICATION_JSON
        ));

    BankPaymentRequest request = new BankPaymentRequest(
        "2222405343248877", "04/2027", "GBP", 100, "012");
    BankPaymentResponse response = client.processPayment(request);

    assertTrue(response.authorized());
    assertEquals("0bb07405-6d44-4b50-a14f-7ae0beff13ad", response.authorizationCode());
    mockServer.verify();
  }

  @Test
  void whenBankReturnsDeclinedThenUnauthorizedResponseIsReturned() {
    mockServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(
            """
            {
              "authorized": false,
              "authorization_code": ""
            }
            """,
            MediaType.APPLICATION_JSON
        ));

    BankPaymentRequest request = new BankPaymentRequest(
        "2222405343248878", "04/2027", "GBP", 100, "123");
    BankPaymentResponse response = client.processPayment(request);

    assertFalse(response.authorized());
    mockServer.verify();
  }

  @Test
  void whenBankReturns503ThenBankUnavailableExceptionIsThrown() {
    mockServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    BankPaymentRequest request = new BankPaymentRequest(
        "2222405343248870", "04/2027", "GBP", 100, "123");

    assertThrows(BankUnavailableException.class, () -> client.processPayment(request));
    mockServer.verify();
  }

  @Test
  void whenBankReturnsEmptyBodyThenBankUnavailableExceptionIsThrown() {
    mockServer.expect(requestTo(BANK_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK));

    BankPaymentRequest request = new BankPaymentRequest(
        "2222405343248877", "04/2027", "GBP", 100, "123");

    assertThrows(BankUnavailableException.class, () -> client.processPayment(request));
    mockServer.verify();
  }
}
