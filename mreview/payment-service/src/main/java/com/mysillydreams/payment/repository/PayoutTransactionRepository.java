package com.mysillydreams.payment.repository;

import com.mysillydreams.payment.domain.PayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, UUID> {

    // Find by Razorpay Payout ID (useful for webhook processing)
    Optional<PayoutTransaction> findByRazorpayPayoutId(String razorpayPayoutId);

    // Find by the original PaymentTransaction ID (if needed, though less common for direct lookup)
    // List<PayoutTransaction> findByPaymentTransactionId(UUID paymentTransactionId);

    // Find by vendor ID (to see all payouts for a vendor)
    // List<PayoutTransaction> findByVendorId(UUID vendorId);

}
