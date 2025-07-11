package com.mysillydreams.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.payment.dto.PaymentAuthorizedWebhookDto;
import com.mysillydreams.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RazorpayWebhookController.class)
@ActiveProfiles("test") // Use application-test.yml for webhook secret
class RazorpayWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService; // Mock the service dependency

    @Autowired
    private ObjectMapper objectMapper; // For creating JSON payloads

    @Autowired
    private WebApplicationContext webApplicationContext;

    // This secret must match what's in application-test.yml for payment.razorpay.webhook.secret
    private static final String TEST_WEBHOOK_SECRET = "test_webhook_secret";

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.razorpay.webhook.secret", () -> TEST_WEBHOOK_SECRET);
        // Mock other essential properties if controller depends on them via @Value at field level.
        // For RazorpayWebhookController, only webhook.secret is directly @Value injected.
    }

    @BeforeEach
    public void setup() {
        // Ensure MockMvc is initialized properly if not using @Autowired MockMvc directly from Spring context
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }


    private String calculateSignature(String payload, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] signatureBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(signatureBytes);
    }

    @Test
    void handleRazorpayWebhook_withValidSignatureAndPaymentAuthorizedEvent_shouldCallServiceAndReturnOk() throws Exception {
        // Arrange
        Map<String, Object> paymentEntity = Map.of(
                "id", "pay_test_123",
                "order_id", "order_test_123",
                "amount", 10000L, // paise
                "currency", "INR",
                "status", "authorized"
        );
        Map<String, Object> payloadMap = Map.of(
                "event", "payment.authorized",
                "payload", Map.of("payment", Map.of("entity", paymentEntity))
        );
        String payloadJson = objectMapper.writeValueAsString(payloadMap);
        String signature = calculateSignature(payloadJson, TEST_WEBHOOK_SECRET);

        // Act & Assert
        mockMvc.perform(post("/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .content(payloadJson))
                .andExpect(status().isOk());

        // Verify that the correct service method was called
        ArgumentCaptor<PaymentAuthorizedWebhookDto> dtoCaptor = ArgumentCaptor.forClass(PaymentAuthorizedWebhookDto.class);
        verify(paymentService).handleWebhookPaymentAuthorized(dtoCaptor.capture());
        assertEquals("pay_test_123", dtoCaptor.getValue().getPayment().getId());
    }

    @Test
    void handleRazorpayWebhook_withInvalidSignature_shouldReturnUnauthorized() throws Exception {
        // Arrange
        String payloadJson = "{\"event\":\"payment.authorized\",\"payload\":{}}"; // Simple payload
        String invalidSignature = "invalid_signature_ cafes";

        // Act & Assert
        mockMvc.perform(post("/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", invalidSignature)
                        .content(payloadJson))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).handleWebhookPaymentAuthorized(any());
        verify(paymentService, never()).handleWebhookPaymentFailed(any());
    }

    @Test
    void handleRazorpayWebhook_withMissingSignatureHeader_shouldReturnBadRequest() throws Exception {
        String payloadJson = "{\"event\":\"payment.authorized\",\"payload\":{}}";
        mockMvc.perform(post("/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson)) // No X-Razorpay-Signature header
                .andExpect(status().isBadRequest());
    }

    @Test
    void handleRazorpayWebhook_withUnhandledEvent_shouldReturnOkButNotCallSpecificHandlers() throws Exception {
        Map<String, Object> payloadMap = Map.of("event", "some.other.event", "payload", Map.of());
        String payloadJson = objectMapper.writeValueAsString(payloadMap);
        String signature = calculateSignature(payloadJson, TEST_WEBHOOK_SECRET);

        mockMvc.perform(post("/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .content(payloadJson))
                .andExpect(status().isOk()); // Acknowledges receipt

        verify(paymentService, never()).handleWebhookPaymentAuthorized(any());
        verify(paymentService, never()).handleWebhookPaymentFailed(any());
    }

     @Test
    void handleRazorpayWebhook_withEmptyPayload_shouldReturnBadRequest() throws Exception {
        String payloadJson = ""; // Empty payload
        String signature = calculateSignature(payloadJson, TEST_WEBHOOK_SECRET); // Signature of empty string

        mockMvc.perform(post("/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .content(payloadJson))
                .andExpect(status().isBadRequest());
    }

    // --- Payout Webhook Event Tests ---

    /*
    @Test
    void handleRazorpayWebhook_withValidPayoutProcessedEvent_shouldCallVendorServiceAndReturnOk() throws Exception {
        // Arrange
        String rzpPayoutId = "pout_test_webhook_proc";
        Map<String, Object> payoutEntity = Map.of(
                "id", rzpPayoutId,
                "status", "processed",
                "created_at", Instant.now().getEpochSecond()
                // ... other necessary fields for PayoutTransaction update by VendorPayoutService
        );
        Map<String, Object> payloadMap = Map.of(
                "event", "payout.processed",
                "payload", Map.of("payout", Map.of("entity", payoutEntity))
        );
        String payloadJson = objectMapper.writeValueAsString(payloadMap);
        String signature = calculateSignature(payloadJson, TEST_WEBHOOK_SECRET);

        // Act & Assert
        mockMvc.perform(post("/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .content(payloadJson))
                .andExpect(status().isOk());

        // Verify that the correct service method was called
        verify(vendorPayoutService).handlePayoutSuccess(eq(rzpPayoutId), any(Instant.class));
    }
    */

    /*
    @Test
    void handleRazorpayWebhook_withValidPayoutFailedEvent_shouldCallVendorServiceAndReturnOk() throws Exception {
        // Arrange
        String rzpPayoutId = "pout_test_webhook_fail";
        Map<String, Object> payoutEntity = Map.of(
                "id", rzpPayoutId,
                "status", "failed",
                "failure_reason", "test_failure_code",
                "status_details", Map.of("description", "Test failure description"),
                "created_at", Instant.now().getEpochSecond()
        );
         Map<String, Object> payloadMap = Map.of(
                "event", "payout.failed",
                "payload", Map.of("payout", Map.of("entity", payoutEntity))
        );
        String payloadJson = objectMapper.writeValueAsString(payloadMap);
        String signature = calculateSignature(payloadJson, TEST_WEBHOOK_SECRET);

        // Act & Assert
        mockMvc.perform(post("/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .content(payloadJson))
                .andExpect(status().isOk());

        verify(vendorPayoutService).handlePayoutFailed(
            eq(rzpPayoutId),
            eq("test_failure_code"),
            eq("Test failure description"),
            any(Instant.class)
        );
    }
    */
}
