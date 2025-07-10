package com.mysillydreams.auth.service;

import com.mysillydreams.auth.config.SecurityConstants;
import com.mysillydreams.auth.controller.dto.JwtResponse;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import com.mysillydreams.auth.entity.AdminMfaConfig;
import com.mysillydreams.auth.exception.MfaAuthenticationRequiredException;
import com.mysillydreams.auth.repository.AdminMfaConfigRepository;
import com.mysillydreams.auth.util.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
 // If using Jwt as Principal
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;


@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final KeycloakAuthenticationService keycloakAuthenticationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminMfaConfigRepository adminMfaConfigRepository;
    private final AdminMfaService adminMfaService; // To verify OTP during login

    @Autowired
    public AuthService(KeycloakAuthenticationService keycloakAuthenticationService,
                       JwtTokenProvider jwtTokenProvider,
                       AdminMfaConfigRepository adminMfaConfigRepository,
                       AdminMfaService adminMfaService) {
        this.keycloakAuthenticationService = keycloakAuthenticationService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminMfaConfigRepository = adminMfaConfigRepository;
        this.adminMfaService = adminMfaService;
    }

    @Transactional(readOnly = true) // readOnly as it reads MFA config, but no writes here
    public JwtResponse login(LoginRequest loginRequest) {
        logger.info("Processing login for user: {}", loginRequest.getUsername());

        // Authenticate against Keycloak
        KeycloakAuthenticationService.KeycloakUserInfo userInfo = keycloakAuthenticationService
                .authenticateUser(loginRequest.getUsername(), loginRequest.getPassword());

        // Post-authentication checks (e.g., MFA for admins)
        boolean isAdmin = userInfo.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SecurityConstants.ROLE_ADMIN::equals);

        if (isAdmin) {
            logger.debug("Admin user {} authenticated by password. Checking MFA status.", loginRequest.getUsername());
            UUID adminUserId = userInfo.getUserId();
            if (adminUserId == null) {
                logger.error("Could not extract User ID for admin {} from Keycloak.", loginRequest.getUsername());
                throw new BadCredentialsException("Admin user identifier not found after authentication.");
            }

            AdminMfaConfig mfaConfig = adminMfaConfigRepository.findByUserId(adminUserId).orElse(null);

            if (mfaConfig != null && mfaConfig.getIsEnabled()) {
                logger.info("MFA is enabled for admin {}. Verifying OTP.", loginRequest.getUsername());
                if (loginRequest.getOtp() == null || loginRequest.getOtp().trim().isEmpty()) {
                    logger.warn("MFA required for admin {}, but OTP not provided.", loginRequest.getUsername());
                    throw new MfaAuthenticationRequiredException("MFA OTP is required for this admin account.");
                }
                boolean otpValid = adminMfaService.verifyOtp(adminUserId, loginRequest.getOtp());
                if (!otpValid) {
                    logger.warn("Invalid MFA OTP provided for admin {}.", loginRequest.getUsername());
                    throw new BadCredentialsException("Invalid MFA OTP provided.");
                }
                logger.info("MFA OTP successfully verified for admin {}.", loginRequest.getUsername());
            } else {
                // Admin, but MFA not enabled or no config found (treat as MFA not required yet)
                logger.info("MFA not enabled or not configured for admin {}. Proceeding without OTP check.", loginRequest.getUsername());
            }
        }

        // If all checks pass (including MFA for relevant admins), generate JWT
        String jwt = jwtTokenProvider.generateTokenForUser(userInfo.getUsername(), userInfo.getAuthorities());
        Long expiresIn = jwtTokenProvider.getExpiryDateFromToken(jwt) - System.currentTimeMillis();
        logger.info("User {} logged in successfully. JWT generated.", loginRequest.getUsername());
        return new JwtResponse(jwt, expiresIn);
    }



    /**
     * Refreshes a JWT token with additional business logic validation.
     * This method can be extended to add business rules for token refresh.
     *
     * @param oldToken The current JWT token to be refreshed
     * @return JwtResponse containing the new token and expiry information
     * @throws BadCredentialsException if the token is invalid or cannot be refreshed
     */
    public JwtResponse refreshToken(String oldToken) {
        logger.debug("Processing token refresh request");

        if (!jwtTokenProvider.validateToken(oldToken)) {
            logger.warn("Invalid token provided for refresh");
            throw new BadCredentialsException("Invalid token for refresh");
        }

        Authentication authentication = jwtTokenProvider.getAuthentication(oldToken);

        // Additional business logic can be added here, such as:
        // - Checking if user is still active
        // - Validating refresh frequency limits
        // - Checking for security policy changes

        String newJwt = jwtTokenProvider.generateToken(authentication);
        Long expiresIn = jwtTokenProvider.getExpiryDateFromToken(newJwt) - System.currentTimeMillis();

        logger.info("Token refreshed successfully for user: {}", authentication.getName());
        return new JwtResponse(newJwt, expiresIn);
    }

    /**
     * Validates a JWT token and returns token information.
     * This method can be extended to add additional validation logic.
     *
     * @param token The JWT token to validate
     * @return Map containing token validation results and user information
     */
    public Map<String, Object> validateToken(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            logger.warn("Token validation failed");
            return Map.of("status", "invalid");
        }

        Authentication authentication = jwtTokenProvider.getAuthentication(token);
        logger.debug("Token validated successfully for user: {}", authentication.getName());

        return Map.of(
            "status", "valid",
            "user", authentication.getName(),
            "authorities", authentication.getAuthorities()
        );
    }
}
