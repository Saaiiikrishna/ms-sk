package com.mysillydreams.auth.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Enhanced logging configuration for production monitoring.
 * Provides structured logging with security event tracking.
 */
@Configuration
public class LoggingConfiguration {

    @Value("${logging.level.com.mysillydreams.auth:INFO}")
    private String authLogLevel;

    @Value("${logging.file.path:/var/log/auth-service}")
    private String logFilePath;

    @Value("${logging.pattern.console:%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId},%X{spanId}] %logger{36} - %msg%n}")
    private String consolePattern;

    @Value("${logging.pattern.file:%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId},%X{spanId}] %logger{50} - %msg%n}")
    private String filePattern;

    @PostConstruct
    public void configureLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Configure console appender
        configureConsoleAppender(context);
        
        // Configure file appender for security events
        configureSecurityFileAppender(context);
        
        // Configure general application file appender
        configureApplicationFileAppender(context);
    }

    private void configureConsoleAppender(LoggerContext context) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("CONSOLE");

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(consolePattern);
        encoder.start();

        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(consoleAppender);
    }

    private void configureSecurityFileAppender(LoggerContext context) {
        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName("SECURITY_FILE");
        fileAppender.setFile(logFilePath + "/security.log");

        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logFilePath + "/security.%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(30); // Keep 30 days
        rollingPolicy.start();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(filePattern);
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();

        // Add to security-related loggers
        Logger securityLogger = context.getLogger("com.mysillydreams.auth.security");
        securityLogger.addAppender(fileAppender);
        securityLogger.setAdditive(false);
    }

    private void configureApplicationFileAppender(LoggerContext context) {
        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName("APPLICATION_FILE");
        fileAppender.setFile(logFilePath + "/application.log");

        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logFilePath + "/application.%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(7); // Keep 7 days
        rollingPolicy.start();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(filePattern);
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();

        Logger appLogger = context.getLogger("com.mysillydreams.auth");
        appLogger.addAppender(fileAppender);
    }

    /**
     * Security event logger for audit trails
     */
    @Bean
    public SecurityEventLogger securityEventLogger() {
        return new SecurityEventLogger();
    }

    /**
     * Security event logger implementation
     */
    public static class SecurityEventLogger {
        private static final org.slf4j.Logger securityLogger = 
            LoggerFactory.getLogger("com.mysillydreams.auth.security");

        public void logAuthenticationSuccess(String username, String ipAddress) {
            securityLogger.info("AUTHENTICATION_SUCCESS: user={}, ip={}", username, ipAddress);
        }

        public void logAuthenticationFailure(String username, String ipAddress, String reason) {
            securityLogger.warn("AUTHENTICATION_FAILURE: user={}, ip={}, reason={}", username, ipAddress, reason);
        }

        public void logTokenGenerated(String username, String tokenType) {
            securityLogger.info("TOKEN_GENERATED: user={}, type={}", username, tokenType);
        }

        public void logTokenRefreshed(String username, String ipAddress) {
            securityLogger.info("TOKEN_REFRESHED: user={}, ip={}", username, ipAddress);
        }

        public void logTokenRevoked(String username, String reason) {
            securityLogger.info("TOKEN_REVOKED: user={}, reason={}", username, reason);
        }

        public void logPasswordRotation(String username, String adminUser) {
            securityLogger.info("PASSWORD_ROTATED: user={}, admin={}", username, adminUser);
        }

        public void logMfaSetup(String username) {
            securityLogger.info("MFA_SETUP: user={}", username);
        }

        public void logMfaVerification(String username, boolean success) {
            securityLogger.info("MFA_VERIFICATION: user={}, success={}", username, success);
        }

        public void logSuspiciousActivity(String username, String ipAddress, String activity) {
            securityLogger.warn("SUSPICIOUS_ACTIVITY: user={}, ip={}, activity={}", username, ipAddress, activity);
        }

        public void logAdminAction(String adminUser, String action, String target) {
            securityLogger.info("ADMIN_ACTION: admin={}, action={}, target={}", adminUser, action, target);
        }
    }
}
