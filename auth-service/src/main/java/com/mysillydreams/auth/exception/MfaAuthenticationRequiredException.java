package com.mysillydreams.auth.exception;

import org.springframework.security.authentication.BadCredentialsException;

/**
 * Custom exception for MFA specific failures during login.
 * Thrown when an admin user requires MFA but doesn't provide an OTP.
 */
public class MfaAuthenticationRequiredException extends BadCredentialsException {
    
    public MfaAuthenticationRequiredException(String msg) {
        super(msg);
    }
    
    public MfaAuthenticationRequiredException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
