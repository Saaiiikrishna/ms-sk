package com.mysillydreams.userservice.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
@Component
public class AdminActionAuditAspect {

    private static final Logger auditLogger = LoggerFactory.getLogger("AdminActionAuditLogger");
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    // Pointcut for all public methods in the AdminController
    @Pointcut("execution(public * com.mysillydreams.userservice.web.admin.AdminController.*(..))")
    public void adminControllerMethods() {}

    // TODO: Consider adding pointcuts for specific service methods if admins can bypass normal flows
    // and directly invoke powerful service methods not exposed via AdminController.
    // @Pointcut("execution(public * com.mysillydreams.userservice.service.*.*(..)) && @annotation(AdminCallable)")
    // public void adminServiceMethods() {} // Requires a custom @AdminCallable annotation

    @Before("adminControllerMethods()")
    public void logAdminActionStart(JoinPoint joinPoint) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && isAdmin(authentication)) {
            String adminUsername = authentication.getName();
            String methodName = joinPoint.getSignature().getName();
            String args = Arrays.stream(joinPoint.getArgs())
                                .map(String::valueOf) // Simple string representation
                                .collect(Collectors.joining(", "));
            // Avoid logging sensitive data from args. Consider masking or selective logging.
            // For complex objects, objectMapper.writeValueAsString might be used with care.
            auditLogger.info("[ADMIN_ACTION_START] User: '{}', Method: '{}', Args: [{}]",
                             adminUsername, methodName, args);
        }
    }

    @AfterReturning(pointcut = "adminControllerMethods()", returning = "result")
    public void logAdminActionSuccess(JoinPoint joinPoint, Object result) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && isAdmin(authentication)) {
            String adminUsername = authentication.getName();
            String methodName = joinPoint.getSignature().getName();
            // Avoid logging potentially large or sensitive response objects directly.
            // Log a summary or confirmation.
            auditLogger.info("[ADMIN_ACTION_SUCCESS] User: '{}', Method: '{}', Result: Success (details omitted for brevity/security)",
                             adminUsername, methodName);
        }
    }

    @AfterThrowing(pointcut = "adminControllerMethods()", throwing = "exception")
    public void logAdminActionFailure(JoinPoint joinPoint, Throwable exception) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && isAdmin(authentication)) {
            String adminUsername = authentication.getName();
            String methodName = joinPoint.getSignature().getName();
            auditLogger.error("[ADMIN_ACTION_FAILURE] User: '{}', Method: '{}', Exception: {}, Message: {}",
                              adminUsername, methodName, exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    // To use a dedicated logger that writes to a separate file, configure it in logback-spring.xml or log4j2.xml:
    /*
    <logger name="AdminActionAuditLogger" level="INFO" additivity="false">
        <appender-ref ref="ADMIN_AUDIT_FILE_APPENDER"/>
    </logger>

    <appender name="ADMIN_AUDIT_FILE_APPENDER" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/admin-audit.log</file>
        ... (rolling policy, encoder, etc.) ...
    </appender>
    */
}
