package com.mysillydreams.payment.service;

import com.mysillydreams.payment.config.CommissionProperties;
import com.mysillydreams.payment.domain.PaymentTransaction;
import com.mysillydreams.payment.domain.PayoutStatus;
import com.mysillydreams.payment.domain.PayoutTransaction;
import com.mysillydreams.payment.repository.PaymentRepository;
import com.mysillydreams.payment.repository.PayoutTransactionRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.AsyncResult; // For mocking @Async return if not void
import org.springframework.test.util.ReflectionTestUtils;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorPayoutServiceTest {

    @Mock private PayoutTransactionRepository payoutTransactionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RazorpayClient razorpayClient;
    @Mock private com.razorpay.Payouts razorpayPayoutsClient; // For razorpayClient.Payouts
    @Mock private OutboxEventService outboxEventService;
    @Spy private CommissionProperties commissionProperties = new CommissionProperties(); // Use Spy to set actual value

    @InjectMocks private VendorPayoutService vendorPayoutService;

    private final String INITIATED_TOPIC = "vendor.payout.initiated";
    private final String SUCCEEDED_TOPIC = "vendor.payout.succeeded";
    private final String FAILED_TOPIC = "vendor.payout.failed";
    private final String RAZORPAY_X_ACCOUNT_ID = "acc_test_razorpay_x";

    private UUID paymentTxId;
    private UUID vendorId;
    private PaymentTransaction mockPaymentTransaction;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(vendorPayoutService, "vendorPayoutInitiatedTopic", INITIATED_TOPIC);
        ReflectionTestUtils.setField(vendorPayoutService, "vendorPayoutSucceededTopic", SUCCEEDED_TOPIC);
        ReflectionTestUtils.setField(vendorPayoutService, "vendorPayoutFailedTopic", FAILED_TOPIC);
        ReflectionTestUtils.setField(vendorPayoutService, "razorpayXAccountId", RAZORPAY_X_ACCOUNT_ID);

        // Setup CommissionProperties
        commissionProperties.setPercent(new BigDecimal("10.0")); // 10% commission

        paymentTxId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        mockPaymentTransaction = new PaymentTransaction();
        mockPaymentTransaction.setId(paymentTxId);
        mockPaymentTransaction.setAmount(new BigDecimal("1000.00"));
        mockPaymentTransaction.setCurrency("INR");

        when(paymentRepository.findById(paymentTxId)).thenReturn(Optional.of(mockPaymentTransaction));
        when(razorpayClient.Payouts).thenReturn(razorpayPayoutsClient);

        // Mock PayoutTransaction save to return the argument with an ID if not set
        when(payoutTransactionRepository.save(any(PayoutTransaction.class))).thenAnswer(invocation -> {
            PayoutTransaction pt = invocation.getArgument(0);
            if (pt.getId() == null) pt.setId(UUID.randomUUID());
            return pt;
        });
    }

    @Test
    void initiatePayout_shouldCreateInitRecordAndPublishInitiatedEvent() {
        // Arrange
        // Spying on the service to prevent @Async method execution in this unit test,
        // or ensure it can be called synchronously if that's simpler for testing.
        // For now, we'll just verify the call to the async method.
        VendorPayoutService spyVendorPayoutService = spy(vendorPayoutService);
        doNothing().when(spyVendorPayoutService).processRazorpayPayoutAsync(any(PayoutTransaction.class));


        // Act
        UUID payoutId = spyVendorPayoutService.initiatePayout(paymentTxId, vendorId, new BigDecimal("1000.00"), "INR");

        // Assert
        assertNotNull(payoutId);

        ArgumentCaptor<PayoutTransaction> ptCaptor = ArgumentCaptor.forClass(PayoutTransaction.class);
        verify(payoutTransactionRepository).save(ptCaptor.capture());
        PayoutTransaction savedPt = ptCaptor.getValue();
        assertEquals(payoutId, savedPt.getId());
        assertEquals(paymentTxId, savedPt.getPaymentTransaction().getId());
        assertEquals(vendorId, savedPt.getVendorId());
        assertEquals(0, new BigDecimal("100.00").compareTo(savedPt.getCommissionAmount())); // 10% of 1000
        assertEquals(0, new BigDecimal("900.00").compareTo(savedPt.getNetAmount()));
        assertEquals(PayoutStatus.INIT, savedPt.getStatus());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventService).publish(eq("VendorPayout"), eq(payoutId.toString()), eq(INITIATED_TOPIC), payloadCaptor.capture());
        assertEquals(payoutId.toString(), payloadCaptor.getValue().get("payoutId"));
        assertEquals(900.00, payloadCaptor.getValue().get("netAmount"));

        verify(spyVendorPayoutService).processRazorpayPayoutAsync(savedPt); // Verify async method was called
    }

    @Test
    void processRazorpayPayoutAsync_whenApiSucceeds_updatesStatusToPending() throws RazorpayException {
        // Arrange
        PayoutTransaction initPt = new PayoutTransaction(mockPaymentTransaction, vendorId, new BigDecimal("1000"),
                new BigDecimal("100"), new BigDecimal("900"), "INR", PayoutStatus.INIT);
        initPt.setId(UUID.randomUUID()); // Ensure it has an ID
        when(payoutTransactionRepository.findById(initPt.getId())).thenReturn(Optional.of(initPt));

        com.razorpay.Payout mockRazorpayPayout = mock(com.razorpay.Payout.class);
        when(mockRazorpayPayout.get("id")).thenReturn("pout_rp_123");
        when(mockRazorpayPayout.get("status")).thenReturn("pending"); // Example status

        // Mock the static method call if VendorPayoutService uses a static method for fund account lookup,
        // or inject a mock for the service that provides it.
        // For the placeholder `lookupVendorFundAccount`, we might need to use PowerMockito if it were static,
        // or ensure it's an instance method on a mockable bean.
        // Here, it's an instance method, so if `vendorPayoutService` was spied, we could mock it.
        // Or, since it's a public method in the same class, we can't easily mock it when testing another public method
        // unless the class itself is spied. We'll assume for this test it returns a valid ID.
        VendorPayoutService partialMockService = spy(vendorPayoutService);
        doReturn("fa_mock_fund_account_id_1").when(partialMockService).lookupVendorFundAccount(vendorId);


        when(razorpayPayoutsClient.create(any(JSONObject.class))).thenReturn(mockRazorpayPayout);


        // Act
        partialMockService.processRazorpayPayoutAsync(initPt);

        // Assert
        ArgumentCaptor<PayoutTransaction> ptCaptor = ArgumentCaptor.forClass(PayoutTransaction.class);
        verify(payoutTransactionRepository, times(1)).save(ptCaptor.capture()); // called once inside processRazorpayPayoutAsync
        PayoutTransaction updatedPt = ptCaptor.getValue();

        assertEquals(PayoutStatus.PENDING, updatedPt.getStatus());
        assertEquals("pout_rp_123", updatedPt.getRazorpayPayoutId());
        verify(outboxEventService, never()).publish(anyString(), anyString(), eq(FAILED_TOPIC), anyMap());
    }


    @Test
    void processRazorpayPayoutAsync_whenApiThrowsException_updatesStatusToFailedAndPublishesFailure() throws RazorpayException {
        // Arrange
        PayoutTransaction initPt = new PayoutTransaction(mockPaymentTransaction, vendorId, new BigDecimal("1000"),
                new BigDecimal("100"), new BigDecimal("900"), "INR", PayoutStatus.INIT);
        initPt.setId(UUID.randomUUID());
        when(payoutTransactionRepository.findById(initPt.getId())).thenReturn(Optional.of(initPt));

        VendorPayoutService partialMockService = spy(vendorPayoutService);
        doReturn("fa_mock_fund_account_id_1").when(partialMockService).lookupVendorFundAccount(vendorId);

        when(razorpayPayoutsClient.create(any(JSONObject.class)))
                .thenThrow(new RazorpayException("Payout API Error"));


        // Act
        partialMockService.processRazorpayPayoutAsync(initPt);

        // Assert
        ArgumentCaptor<PayoutTransaction> ptCaptor = ArgumentCaptor.forClass(PayoutTransaction.class);
        verify(payoutTransactionRepository, times(1)).save(ptCaptor.capture());
        PayoutTransaction updatedPt = ptCaptor.getValue();

        assertEquals(PayoutStatus.FAILED, updatedPt.getStatus());
        assertTrue(updatedPt.getErrorMessage().contains("Payout API Error"));

        verify(outboxEventService).publish(eq("VendorPayout"), eq(initPt.getId().toString()), eq(FAILED_TOPIC), anyMap());
    }

    @Test
    void handlePayoutSuccess_updatesStatusAndPublishesSuccessEvent() {
        // Arrange
        String rzpPayoutId = "pout_rp_success_wh";
        PayoutTransaction pendingPt = new PayoutTransaction(mockPaymentTransaction, vendorId, new BigDecimal("1000"),
                new BigDecimal("100"), new BigDecimal("900"), "INR", PayoutStatus.PENDING);
        pendingPt.setId(UUID.randomUUID());
        pendingPt.setRazorpayPayoutId(rzpPayoutId); // Important: set the ID it will be looked up by
        when(payoutTransactionRepository.findByRazorpayPayoutId(rzpPayoutId)).thenReturn(Optional.of(pendingPt));

        // Act
        vendorPayoutService.handlePayoutSuccess(rzpPayoutId, Instant.now());

        // Assert
        ArgumentCaptor<PayoutTransaction> ptCaptor = ArgumentCaptor.forClass(PayoutTransaction.class);
        verify(payoutTransactionRepository).save(ptCaptor.capture());
        assertEquals(PayoutStatus.SUCCESS, ptCaptor.getValue().getStatus());

        verify(outboxEventService).publish(eq("VendorPayout"), eq(pendingPt.getId().toString()), eq(SUCCEEDED_TOPIC), anyMap());
    }


    @Test
    void handlePayoutFailed_updatesStatusAndPublishesFailedEvent() {
        // Arrange
        String rzpPayoutId = "pout_rp_failed_wh";
         PayoutTransaction pendingPt = new PayoutTransaction(mockPaymentTransaction, vendorId, new BigDecimal("1000"),
                new BigDecimal("100"), new BigDecimal("900"), "INR", PayoutStatus.PENDING);
        pendingPt.setId(UUID.randomUUID());
        pendingPt.setRazorpayPayoutId(rzpPayoutId);
        when(payoutTransactionRepository.findByRazorpayPayoutId(rzpPayoutId)).thenReturn(Optional.of(pendingPt));

        // Act
        vendorPayoutService.handlePayoutFailed(rzpPayoutId, "ERR_CODE_01", "Webhook failure", Instant.now());

        // Assert
        ArgumentCaptor<PayoutTransaction> ptCaptor = ArgumentCaptor.forClass(PayoutTransaction.class);
        verify(payoutTransactionRepository).save(ptCaptor.capture());
        PayoutTransaction savedPt = ptCaptor.getValue();
        assertEquals(PayoutStatus.FAILED, savedPt.getStatus());
        assertEquals("ERR_CODE_01", savedPt.getErrorCode());
        assertEquals("Webhook failure", savedPt.getErrorMessage());

        verify(outboxEventService).publish(eq("VendorPayout"), eq(pendingPt.getId().toString()), eq(FAILED_TOPIC), anyMap());
    }

    // TODO: Test for initiatePayout when net amount is zero or negative.
    // TODO: Test for lookupVendorFundAccount failure scenarios.
    // TODO: Test idempotency of webhook handlers (e.g., if SUCCESS webhook received for already SUCCESS PayoutTransaction).
}
