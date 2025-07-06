package com.mysillydreams.userservice.service.delivery;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple service for verifying OTP codes for an order.
 * In a real implementation this would call an external notification
 * or order service to validate the OTP that was sent to the customer.
 */
@Service
public class OtpVerificationService {

    private final Map<UUID, String> otpStore = new ConcurrentHashMap<>();

    /**
     * Store an OTP for an order. Primarily used for tests.
     */
    public void storeOtp(UUID orderId, String otp) {
        otpStore.put(orderId, otp);
    }

    /**
     * Verify the provided OTP for the given order.
     *
     * @param orderId order identifier
     * @param otp     otp string from customer
     * @return true if the otp matches what was previously stored
     */
    public boolean verifyOtp(UUID orderId, String otp) {
        if (orderId == null || otp == null) {
            return false;
        }
        String expected = otpStore.get(orderId);
        return otp.equals(expected);
    }
}
