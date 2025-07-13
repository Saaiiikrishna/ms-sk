package com.mysillydreams.zookeeper.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration properties for Zookeeper Service
 * Provides type-safe configuration with validation
 */
@ConfigurationProperties(prefix = "app.config")
@Validated
public class ZookeeperServiceProperties {

    @NotEmpty(message = "At least one environment must be configured")
    private List<String> environments;

    @NotEmpty(message = "At least one service must be configured")
    private List<String> services;

    @Valid
    @NotNull
    private Security security = new Security();

    @Valid
    @NotNull
    private Performance performance = new Performance();

    // Getters and setters
    public List<String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<String> environments) {
        this.environments = environments;
    }

    public List<String> getServices() {
        return services;
    }

    public void setServices(List<String> services) {
        this.services = services;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Performance getPerformance() {
        return performance;
    }

    public void setPerformance(Performance performance) {
        this.performance = performance;
    }

    /**
     * Security configuration properties
     */
    public static class Security {
        @Valid
        @NotNull
        private Encryption encryption = new Encryption();

        @Valid
        @NotNull
        private Audit audit = new Audit();

        public Encryption getEncryption() {
            return encryption;
        }

        public void setEncryption(Encryption encryption) {
            this.encryption = encryption;
        }

        public Audit getAudit() {
            return audit;
        }

        public void setAudit(Audit audit) {
            this.audit = audit;
        }

        public static class Encryption {
            @NotBlank(message = "Encryption algorithm must be specified")
            private String algorithm = "AES";

            @Min(value = 128, message = "Key length must be at least 128 bits")
            @Max(value = 256, message = "Key length must not exceed 256 bits")
            private int keyLength = 256;

            public String getAlgorithm() {
                return algorithm;
            }

            public void setAlgorithm(String algorithm) {
                this.algorithm = algorithm;
            }

            public int getKeyLength() {
                return keyLength;
            }

            public void setKeyLength(int keyLength) {
                this.keyLength = keyLength;
            }
        }

        public static class Audit {
            private boolean enabled = true;

            @Min(value = 1, message = "Retention days must be at least 1")
            @Max(value = 365, message = "Retention days must not exceed 365")
            private int retentionDays = 90;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getRetentionDays() {
                return retentionDays;
            }

            public void setRetentionDays(int retentionDays) {
                this.retentionDays = retentionDays;
            }
        }
    }

    /**
     * Performance configuration properties
     */
    public static class Performance {
        @Valid
        @NotNull
        private Cache cache = new Cache();

        @Valid
        @NotNull
        private ConnectionPool connectionPool = new ConnectionPool();

        public Cache getCache() {
            return cache;
        }

        public void setCache(Cache cache) {
            this.cache = cache;
        }

        public ConnectionPool getConnectionPool() {
            return connectionPool;
        }

        public void setConnectionPool(ConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
        }

        public static class Cache {
            private boolean enabled = true;

            @Min(value = 60, message = "TTL must be at least 60 seconds")
            @Max(value = 3600, message = "TTL must not exceed 3600 seconds")
            private int ttl = 300;

            @Min(value = 100, message = "Max size must be at least 100")
            @Max(value = 10000, message = "Max size must not exceed 10000")
            private int maxSize = 1000;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getTtl() {
                return ttl;
            }

            public void setTtl(int ttl) {
                this.ttl = ttl;
            }

            public int getMaxSize() {
                return maxSize;
            }

            public void setMaxSize(int maxSize) {
                this.maxSize = maxSize;
            }
        }

        public static class ConnectionPool {
            @Min(value = 10, message = "Max connections must be at least 10")
            @Max(value = 200, message = "Max connections must not exceed 200")
            private int maxConnections = 50;

            @Min(value = 1, message = "Min connections must be at least 1")
            @Max(value = 50, message = "Min connections must not exceed 50")
            private int minConnections = 5;

            @Min(value = 5000, message = "Connection timeout must be at least 5000ms")
            @Max(value = 60000, message = "Connection timeout must not exceed 60000ms")
            private int connectionTimeout = 30000;

            public int getMaxConnections() {
                return maxConnections;
            }

            public void setMaxConnections(int maxConnections) {
                this.maxConnections = maxConnections;
            }

            public int getMinConnections() {
                return minConnections;
            }

            public void setMinConnections(int minConnections) {
                this.minConnections = minConnections;
            }

            public int getConnectionTimeout() {
                return connectionTimeout;
            }

            public void setConnectionTimeout(int connectionTimeout) {
                this.connectionTimeout = connectionTimeout;
            }
        }
    }
}
