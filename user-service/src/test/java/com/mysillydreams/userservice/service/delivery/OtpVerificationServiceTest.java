package com.mysillydreams.userservice.service.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OtpVerificationServiceTest {

    private OtpVerificationService otpVerificationService;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        otpVerificationService = new OtpVerificationService();
        orderId = UUID.randomUUID();
        otpVerificationService.storeOtp(orderId, "1234");
    }

    @Test
    void verifyOtp_correctOtp_returnsTrue() {
        assertTrue(otpVerificationService.verifyOtp(orderId, "1234"));
    }

    @Test
    void verifyOtp_wrongOtp_returnsFalse() {
        assertFalse(otpVerificationService.verifyOtp(orderId, "0000"));
    }
}
