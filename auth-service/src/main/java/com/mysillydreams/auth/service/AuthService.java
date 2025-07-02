package com.mysillydreams.auth.service;

import com.mysillydreams.auth.config.SecurityConstants; // Assuming this might be created for ROLE_ADMIN
import com.mysillydreams.auth.controller.dto.JwtResponse;
import com.mysillydreams.auth.controller.dto.LoginRequest;
import com.mysillydreams.auth.domain.AdminMfaConfig;
import com.mysillydreams.auth.repository.AdminMfaConfigRepository;
import com.mysillydreams.auth.util.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt; // If using Jwt as Principal
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.UUID;

// Custom exception for MFA specific failures during login
class MfaAuthenticationRequiredException extends BadCredentialsException {
    public MfaAuthenticationRequiredException(String msg) {
        super(msg);
    }
    public MfaAuthenticationRequiredException(String msg, Throwable cause) {
        super(msg, cause);
    }
}


@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final String ROLE_ADMIN = "ROLE_ADMIN"; // Using literal for now

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminMfaConfigRepository adminMfaConfigRepository;
    private final AdminMfaService adminMfaService; // To verify OTP during login

    @Autowired
    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       AdminMfaConfigRepository adminMfaConfigRepository,
                       AdminMfaService adminMfaService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminMfaConfigRepository = adminMfaConfigRepository;
        this.adminMfaService = adminMfaService;
    }

    @Transactional(readOnly = true) // readOnly as it reads MFA config, but no writes here
    public JwtResponse login(LoginRequest loginRequest) {
        logger.info("Processing login for user: {}", loginRequest.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        // Post-authentication checks (e.g., MFA for admins)
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);

        if (isAdmin) {
            logger.debug("Admin user {} authenticated by password. Checking MFA status.", loginRequest.getUsername());
            UUID adminUserId = getUserIdFromAuthentication(authentication);
            if (adminUserId == null) {
                // Should not happen if authentication principal is as expected (e.g. KeycloakAuthenticationToken or Jwt)
                logger.error("Could not extract User ID for admin {} from authentication object.", loginRequest.getUsername());
                throw new BadCredentialsException("Admin user identifier not found after authentication.");
            }

            AdminMfaConfig mfaConfig = adminMfaConfigRepository.findByUserId(adminUserId).orElse(null);

            if (mfaConfig != null && mfaConfig.isMfaEnabled()) {
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
        String jwt = jwtTokenProvider.generateToken(authentication);
        Long expiresIn = jwtTokenProvider.getExpiryDateFromToken(jwt) - System.currentTimeMillis();
        logger.info("User {} logged in successfully. JWT generated.", loginRequest.getUsername());
        return new JwtResponse(jwt, expiresIn);
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt) { // Common if using Spring Security OAuth2 Resource Server with Keycloak
            Jwt jwtPrincipal = (Jwt) principal;
            String sub = jwtPrincipal.getSubject();
            try {
                return UUID.fromString(sub);
            } catch (IllegalArgumentException e) {
                logger.error("Subject claim '{}' in JWT is not a valid UUID.", sub, e);
                return null;
            }
        }
        // Add other principal types if needed, e.g., KeycloakAuthenticationToken
        // else if (principal instanceof KeycloakPrincipal) {
        //    KeycloakPrincipal kcPrincipal = (KeycloakPrincipal) principal;
        //    return UUID.fromString(kcPrincipal.getKeycloakSecurityContext().getToken().getSubject());
        // }
        logger.warn("Could not determine User ID from principal type: {}", principal.getClass().getName());
        return null;
    }

    // TODO: Add method for token refresh if it needs business logic beyond JwtTokenProvider
}
