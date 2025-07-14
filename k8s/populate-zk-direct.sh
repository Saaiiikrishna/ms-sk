#!/bin/bash

# Direct Zookeeper configuration population script
echo "Populating Zookeeper with microservice configurations..."

# Get Zookeeper pod name
ZK_POD=$(kubectl get pods -n mysillydreams-dev -l app=zookeeper --no-headers | awk '{print $1}')
echo "Using Zookeeper pod: $ZK_POD"

# Function to set configuration in Zookeeper
set_zk_config() {
    local path=$1
    local config=$2
    
    echo "Setting configuration for $path"
    kubectl exec -n mysillydreams-dev $ZK_POD -- /usr/bin/zookeeper-shell localhost:2181 <<EOF
create $path "$config"
quit
EOF
    
    # If create fails, try to set (update)
    if [ $? -ne 0 ]; then
        echo "Path exists, updating configuration for $path"
        kubectl exec -n mysillydreams-dev $ZK_POD -- /usr/bin/zookeeper-shell localhost:2181 <<EOF
set $path "$config"
quit
EOF
    fi
}

# Auth Service Configuration
AUTH_CONFIG='spring:
  datasource:
    url: jdbc:postgresql://postgres-auth.mysillydreams-dev:5432/authdb
    username: authuser
    password: authpass123
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
  redis:
    host: redis.mysillydreams-dev
    port: 6379
    database: 0
    timeout: 2000ms
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server.mysillydreams-dev:8761/eureka/
keycloak:
  auth-server-url: http://keycloak.mysillydreams-dev:8080
  realm: MySillyDreams-Realm
jwt:
  secret: mySecretKey123456789012345678901234567890
  expiration-ms: 86400000
app:
  simple-encryption:
    secret-key: TestEncryptionKeyForProduction123456789!
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  security:
    enabled: false
  zipkin:
    tracing:
      endpoint: http://zipkin.mysillydreams-dev:9411/api/v2/spans
vault:
  uri: http://vault.mysillydreams-dev:8200
  token: dev-only-token'

# User Service Configuration  
USER_CONFIG='spring:
  datasource:
    url: jdbc:postgresql://postgres-user.mysillydreams-dev:5432/userdb
    username: useruser
    password: userpass123
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
  redis:
    host: redis.mysillydreams-dev
    port: 6379
    database: 1
    timeout: 2000ms
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server.mysillydreams-dev:8761/eureka/
keycloak:
  auth-server-url: http://keycloak.mysillydreams-dev:8080
  realm: MySillyDreams-Realm
jwt:
  secret: mySecretKey123456789012345678901234567890
  expiration-ms: 86400000
app:
  simple-encryption:
    secret-key: TestEncryptionKeyForProduction123456789!
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  security:
    enabled: false
  zipkin:
    tracing:
      endpoint: http://zipkin.mysillydreams-dev:9411/api/v2/spans
vault:
  uri: http://vault.mysillydreams-dev:8200
  token: dev-only-token'

# Set configurations
set_zk_config "/mysillydreams/dev/auth-service" "$AUTH_CONFIG"
set_zk_config "/mysillydreams/dev/user-service" "$USER_CONFIG"

echo "Zookeeper configuration population completed!"
