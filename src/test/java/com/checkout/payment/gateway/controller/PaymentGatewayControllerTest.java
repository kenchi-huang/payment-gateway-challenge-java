package com.checkout.payment.gateway.controller;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @MockBean
  private AcquiringBankClient acquiringBankClient;
  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PaymentResponse payment = new PaymentResponse(
        UUID.randomUUID(),
        PaymentStatus.AUTHORIZED,
        "4321",
        12,
        2024,
        "USD",
        10
    );

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.status().getName()))
        .andExpect(jsonPath("$.card_number_last_four").value(payment.cardNumberLastFour()))
        .andExpect(jsonPath("$.expiry_month").value(payment.expiryMonth()))
        .andExpect(jsonPath("$.expiry_year").value(payment.expiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.currency()))
        .andExpect(jsonPath("$.amount").value(payment.amount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenPaymentIsProcessedAndAuthorisedThenItShouldBeRetrievable() throws Exception {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(generateCardNumber(1));
    request.setAmount(10);
    request.setCurrency("GBP");
    request.setCvv("123");
    request.setExpiryMonth(5);
    request.setExpiryYear(2999);

    when(acquiringBankClient.processPayment(any(BankPaymentRequest.class)))
        .thenReturn(new BankPaymentResponse(true, "auth-123"));

    MvcResult createResult = mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("Authorized"))
      .andReturn();

    PaymentResponse created = objectMapper.readValue(
        createResult.getResponse().getContentAsString(), PaymentResponse.class
    );

    MvcResult getResult = mvc.perform(MockMvcRequestBuilders.get("/payment/" + created.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(created.id().toString()))
        .andReturn();

    PaymentResponse retrieved = objectMapper.readValue(
        getResult.getResponse().getContentAsString(), PaymentResponse.class
    );

    assertEquals(created, retrieved);
  }

  @Test
  void whenPaymentIsProcessedAndDeclinedThenItShouldBeRetrievable() throws Exception {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(generateCardNumber(2));
    request.setAmount(10);
    request.setCurrency("GBP");
    request.setCvv("123");
    request.setExpiryMonth(5);
    request.setExpiryYear(2999);

    when(acquiringBankClient.processPayment(any(BankPaymentRequest.class)))
        .thenReturn(new BankPaymentResponse(false, ""));

    MvcResult createResult = mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andReturn();

    PaymentResponse created = objectMapper.readValue(
        createResult.getResponse().getContentAsString(), PaymentResponse.class
    );

    MvcResult getResult = mvc.perform(MockMvcRequestBuilders.get("/payment/" + created.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(created.id().toString()))
        .andReturn();

    PaymentResponse retrieved = objectMapper.readValue(
        getResult.getResponse().getContentAsString(), PaymentResponse.class
    );

    assertEquals(created, retrieved);
  }

  @Test
  void whenPaymentRequestHasInvalidParametersThenA400BadRequestShouldBeReturned() throws Exception {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("1234");
    request.setAmount(-1);
    request.setCurrency("HKD");
    request.setCvv("1234");
    request.setExpiryMonth(13);
    request.setExpiryYear(1);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty())
        .andReturn();

    verifyNoInteractions(acquiringBankClient);
  }

  @Test
  void whenPaymentIsProcessedAndTheBankIsUnavailableThenA502BadGatewayShouldBeReturned() throws Exception {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(generateCardNumber(0));
    request.setAmount(10);
    request.setCurrency("GBP");
    request.setCvv("123");
    request.setExpiryMonth(5);
    request.setExpiryYear(2999);

    when(acquiringBankClient.processPayment(any(BankPaymentRequest.class)))
        .thenThrow(new BankUnavailableException("Bank unavailable"));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadGateway())
        .andReturn();
  }

  @Test
  void whenPaymentRequestIsMalformedThenA400BadRequestShouldBeReturned() throws Exception {
    String malformedJson = """
          {
            "card_number": "4242424242424241",
            "amount": "not-an-integer"
          }
          """;

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(malformedJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0]").value("Invalid request payload"));

    verifyNoInteractions(acquiringBankClient);
  }

  /**
   * A simple helper function to generate a random card number
   * @param lastDigit replicates logic from the simulator where 1 maps to authorized, 2 declined, and 0 a 503
   * @return a card number string
   */
  private String generateCardNumber(int lastDigit) {
    StringBuilder sb = new StringBuilder(16);
    for (int i = 0; i < 15; i++) {
      sb.append(ThreadLocalRandom.current().nextInt(10));
    }
    sb.append(lastDigit);
    return sb.toString();
  }
}
