package com.mysillydreams.payment.domain;

public enum PayoutStatus {
    INIT,       // Payout initiated internally, before calling Razorpay
    PENDING,    // Razorpay Payout API called, waiting for webhook confirmation (e.g., status 'pending' or 'processing' from Razorpay)
    PROCESSING, // Razorpay Payout status is 'processing' (intermediate state from webhook)
    SUCCESS,    // Razorpay Payout successful (e.g., status 'processed' from webhook)
    FAILED,     // Razorpay Payout failed (either API call failed or webhook confirmed failure)
    CANCELLED   // If cancellation is possible and implemented
}
