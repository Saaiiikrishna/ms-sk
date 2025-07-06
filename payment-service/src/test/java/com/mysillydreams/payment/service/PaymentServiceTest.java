package com.mysillydreams.payment.service;

import com.mysillydreams.payment.domain.PaymentTransaction;
import com.mysillydreams.payment.dto.PaymentRequestedEvent;
import com.mysillydreams.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException; // Import this
import org.json.JSONObject; // Import this
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;


import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OutboxEventService outboxEventService;
    @Mock
    private RazorpayClient razorpayClient;

    // Mocks for Razorpay entities if direct method chaining is done (e.g., razorpayClient.Orders)
    @Mock
    private com.razorpay.Orders razorpayOrdersClient; // Mock for razorpayClient.Orders
    @Mock
    private com.razorpay.Payments razorpayPaymentsClient; // Mock for razorpayClient.Payments


    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final String SUCCEEDED_TOPIC = "order.payment.succeeded";
    private final String FAILED_TOPIC = "order.payment.failed";
    private final String orderIdString = UUID.randomUUID().toString();
    private final PaymentRequestedEvent sampleEvent = new PaymentRequestedEvent(orderIdString, 100.00, "INR");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "paymentSucceededTopic", SUCCEEDED_TOPIC);
        ReflectionTestUtils.setField(paymentService, "paymentFailedTopic", FAILED_TOPIC);

        // Setup mocks for chained calls like razorpayClient.Orders.create(...)
        when(razorpayClient.Orders).thenReturn(razorpayOrdersClient);
        when(razorpayClient.Payments).thenReturn(razorpayPaymentsClient);


        // Mock initial save of PaymentTransaction to return the transaction with an ID
        // This allows subsequent operations on the same transaction object.
        when(paymentRepository.save(any(PaymentTransaction.class)))
            .thenAnswer(invocation -> {
                PaymentTransaction tx = invocation.getArgument(0);
                if (tx.getId() == null) { // Assuming ID is set on first save
                    tx.setId(UUID.randomUUID());
                }
                return tx;
            });
    }

    @Test
    void processPaymentRequest_whenRazorpaySucceeds_shouldSaveSucceededTransactionAndPublishSuccess() throws RazorpayException {
        // Arrange
        Order mockRazorpayOrder = mock(Order.class);
        when(mockRazorpayOrder.get("id")).thenReturn("order_rp_123");

        Payment mockRazorpayPayment = mock(Payment.class);
        when(mockRazorpayPayment.get("id")).thenReturn("pay_rp_123");
        when(mockRazorpayPayment.get("status")).thenReturn("captured");


        when(razorpayOrdersClient.create(any(JSONObject.class))).thenReturn(mockRazorpayOrder);
        // Simulate fetching payments for the order
        when(razorpayOrdersClient.fetchPayments("order_rp_123")).thenReturn(Collections.singletonList(mockRazorpayPayment));
        // If capture is explicit:
        // when(razorpayPaymentsClient.capture(eq("pay_rp_123"), any(JSONObject.class))).thenReturn(mockRazorpayPayment);


        // Act
        paymentService.processPaymentRequest(sampleEvent);

        // Assert
        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, times(2)).save(transactionCaptor.capture()); // Initial save, then final save
        PaymentTransaction savedTx = transactionCaptor.getAllValues().get(1); // Get the final saved transaction

        assertEquals("SUCCEEDED", savedTx.getStatus());
        assertEquals("order_rp_123", savedTx.getRazorpayOrderId());
        assertEquals("pay_rp_123", savedTx.getRazorpayPaymentId());
        assertNull(savedTx.getErrorMessage());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(
                eq("Payment"),
                eq(savedTx.getId().toString()),
                eq(SUCCEEDED_TOPIC),
                payloadCaptor.capture()
        );
        assertEquals(sampleEvent.getOrderId(), payloadCaptor.getValue().get("orderId"));
        assertEquals("pay_rp_123", payloadCaptor.getValue().get("paymentId"));
    }

    @Test
    void processPaymentRequest_whenRazorpayOrderCreationFails_shouldSaveFailedTransactionAndPublishFailure() throws RazorpayException {
        // Arrange
        when(razorpayOrdersClient.create(any(JSONObject.class)))
                .thenThrow(new RazorpayException("Order creation failed"));

        // Act
        paymentService.processPaymentRequest(sampleEvent);

        // Assert
        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        // Should be saved twice: once for PENDING, once for FAILED
        verify(paymentRepository, times(2)).save(transactionCaptor.capture());
        PaymentTransaction savedTx = transactionCaptor.getAllValues().get(1); // Get the final state

        assertEquals("FAILED", savedTx.getStatus());
        assertEquals("Order creation failed", savedTx.getErrorMessage());
        assertNull(savedTx.getRazorpayOrderId()); // Order ID might not be set if creation failed early

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(
                eq("Payment"),
                eq(savedTx.getId().toString()),
                eq(FAILED_TOPIC),
                payloadCaptor.capture()
        );
        assertEquals(sampleEvent.getOrderId(), payloadCaptor.getValue().get("orderId"));
        assertEquals("Order creation failed", payloadCaptor.getValue().get("reason"));
    }


    @Test
    void processPaymentRequest_whenRazorpayPaymentCaptureFails_shouldSaveFailedTransactionAndPublishFailure() throws RazorpayException {
        // Arrange
        Order mockRazorpayOrder = mock(Order.class);
        when(mockRazorpayOrder.get("id")).thenReturn("order_rp_fail_capture");
        when(razorpayOrdersClient.create(any(JSONObject.class))).thenReturn(mockRazorpayOrder);

        // Simulate scenario where fetching payments returns an empty list or a payment that cannot be captured
        // Here, let's simulate fetchPayments throwing an exception or returning non-capturable payment.
        when(razorpayOrdersClient.fetchPayments("order_rp_fail_capture"))
            .thenThrow(new RazorpayException("Simulated capture/verification failure"));
        // Or, if simulating a payment that is not 'captured' or 'authorized':
        // Payment nonCapturablePayment = mock(Payment.class);
        // when(nonCapturablePayment.get("status")).thenReturn("created"); // or some other status
        // when(razorpayOrdersClient.fetchPayments("order_rp_fail_capture")).thenReturn(Collections.singletonList(nonCapturablePayment));


        // Act
        paymentService.processPaymentRequest(sampleEvent);

        // Assert
        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, times(2)).save(transactionCaptor.capture());
        PaymentTransaction savedTx = transactionCaptor.getAllValues().get(1);

        assertEquals("FAILED", savedTx.getStatus());
        assertEquals("order_rp_fail_capture", savedTx.getRazorpayOrderId()); // Order was created
        assertEquals("Simulated capture/verification failure", savedTx.getErrorMessage());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(
                eq("Payment"),
                eq(savedTx.getId().toString()),
                eq(FAILED_TOPIC),
                payloadCaptor.capture()
        );
        assertEquals(sampleEvent.getOrderId(), payloadCaptor.getValue().get("orderId"));
        assertTrue(((String)payloadCaptor.getValue().get("reason")).contains("Simulated capture/verification failure"));
    }

    // TODO: Add tests for webhook handlers (handleWebhookPaymentAuthorized, handleWebhookPaymentFailed)
    // These would involve:
    // 1. Mocking paymentRepository.findByRazorpayPaymentId (or findByRazorpayOrderId)
    // 2. Verifying the transaction status is updated correctly.
    // 3. Verifying paymentRepository.save is called.
    // 4. Verifying outboxEventService.publish is called if the webhook handling results in new events.

    // Example for handleWebhookPaymentAuthorized
    /*
    @Test
    void handleWebhookPaymentAuthorized_updatesTransactionAndMayPublish() {
        // Arrange
        PaymentAuthorizedWebhookDto dto = new PaymentAuthorizedWebhookDto();
        PaymentAuthorizedWebhookDto.RazorpayPaymentEntityDto paymentEntity = new PaymentAuthorizedWebhookDto.RazorpayPaymentEntityDto();
        paymentEntity.setId("pay_webhook_123");
        paymentEntity.setOrder_id("order_webhook_123");
        paymentEntity.setStatus("authorized");
        dto.setPayment(paymentEntity);

        PaymentTransaction existingTx = new PaymentTransaction(
            UUID.randomUUID().toString(), 100.0, "INR", "PENDING", "order_webhook_123", null);
        existingTx.setId(UUID.randomUUID()); // Ensure it has an ID

        when(paymentRepository.findByRazorpayPaymentId("pay_webhook_123")).thenReturn(Optional.of(existingTx));

        // Act
        paymentService.handleWebhookPaymentAuthorized(dto);

        // Assert
        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository).save(txCaptor.capture());
        assertEquals("AUTHORIZED", txCaptor.getValue().getStatus()); // Or SUCCEEDED if capture is implied
        assertEquals("pay_webhook_123", txCaptor.getValue().getRazorpayPaymentId());

        // Optionally verify outbox publish if webhook triggers further events
        // verify(outboxEventService).publish( ... );
    }
    */
}
