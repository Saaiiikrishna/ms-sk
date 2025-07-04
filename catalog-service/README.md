# Catalog Service

The Catalog Service is a Spring Boot microservice responsible for managing product and service catalogs, inventory, pricing, and shopping carts for the My Silly Dreams E-commerce Platform.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [API Documentation](#api-documentation)
- [Running Locally](#running-locally)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Build & Run](#build--run)
- [Running with Docker](#running-with-docker)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Search Integration](#search-integration)
- [Caching](#caching)
- [Eventing](#eventing)

## Features

- Hierarchical category management for products and services.
- Catalog item (product/service) CRUD operations.
- Real-time stock level tracking for products (with optimistic locking).
- Price history and bulk/volume discount rule management.
- Shopping cart functionality (add, update, remove items, calculate totals).
- Kafka-based event publishing for changes in categories, items, stock, prices, and cart checkouts.
- OpenSearch integration for faceted search across catalog items.
- Redis caching for active shopping carts.
- Role-based access control for API endpoints.

## Tech Stack

- Java 17
- Spring Boot 2.7.x (or latest compatible 2.x)
- Spring Data JPA (Hibernate)
- Spring Web
- Spring Security (OAuth2 Resource Server / Basic Auth for local)
- Spring Kafka
- Spring Data Redis
- PostgreSQL (Database)
- OpenSearch (Search Index)
- Redis (Cache)
- Maven (Build Tool)
- Docker (Containerization)
- Kubernetes (Orchestration)
- Micrometer + Prometheus (Metrics)
- Lombok
- Springdoc OpenAPI (Swagger UI for API docs)

## API Documentation

API documentation is provided via Swagger UI. Once the service is running, it's typically available at:

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI Spec: `http://localhost:8082/v3/api-docs`

(Port `8082` is the default configured in `application.yml`.)

## Running Locally

### Prerequisites

- Java 17 JDK
- Maven 3.6+
- PostgreSQL instance
- Kafka instance
- OpenSearch instance
- Redis instance

### Configuration

The main configuration is in `src/main/resources/application.yml`. For local development, you might use the default profile or create an `application-local.yml`.

Key properties to configure:
- `spring.datasource.url`, `username`, `password` (for PostgreSQL)
- `spring.kafka.bootstrap-servers`
- `opensearch.uris`
- `spring.redis.host`, `port`
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (if using JWT auth with Keycloak/IdP)

If `jwk-set-uri` is not provided, the application will fall back to Basic Authentication with in-memory users (see `SecurityConfig.java`) for `local-dev` or `test` profiles. Default in-memory users:
- `user` / `password` (ROLE_USER)
- `manager` / `password` (ROLE_CATALOG_MANAGER, ROLE_INVENTORY_MANAGER)
- `admin` / `password` (ROLE_ADMIN, etc.)

### Build & Run

1.  **Build:**
    ```bash
    mvn clean package
    ```
2.  **Run:**
    ```bash
    java -jar target/catalog-service-0.0.1-SNAPSHOT.jar
    ```
    You can activate specific Spring profiles:
    ```bash
    java -jar target/catalog-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local-dev
    ```

## Running with Docker

1.  **Build the Docker image:**
    ```bash
    docker build -t your-docker-registry/catalog-service:latest .
    ```
    (Replace `your-docker-registry` with your actual registry if pushing)

2.  **Run the Docker container:**
    ```bash
    docker run -p 8082:8082 \
      -e SPRING_PROFILES_ACTIVE=docker \
      -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host_ip_or_docker_internal_host>:5432/catalog_db \
      -e SPRING_DATASOURCE_USERNAME=youruser \
      -e SPRING_DATASOURCE_PASSWORD=yourpass \
      -e SPRING_KAFKA_BOOTSTRAP_SERVERS=<kafka_host:port> \
      -e OPENSEARCH_URIS=http://<opensearch_host:port> \
      -e SPRING_REDIS_HOST=<redis_host> \
      your-docker-registry/catalog-service:latest
    ```
    Ensure the dependent services (Postgres, Kafka, OpenSearch, Redis) are accessible from the Docker container. For local Docker setup, they might be other containers on the same Docker network.

## Kubernetes Deployment

Basic Kubernetes manifests (`deployment.yaml`, `service.yaml`, `configmap.yaml`, `secret.yaml`) are provided in the `/k8s` directory.
These require customization for your specific environment (e.g., image name in deployment, ConfigMap data, Secret data).

Apply them using `kubectl apply -f k8s/`.

## Search Integration

- Catalog items are indexed into OpenSearch.
- The `CatalogItemIndexerService` listens to Kafka events (item/category changes) to keep the search index up-to-date.
- Search API is available at `GET /api/v1/items/search`.

## Caching

- Active user shopping carts (`CartDto`) are cached in Redis to improve performance.
- Cache is populated on first access and updated/invalidated on cart mutations or checkout.
- Configured in `RedisConfig.java` and used by `CartService`.

## Eventing

The service publishes domain events to Kafka topics for changes related to:
- Categories (`category.created`, `category.updated`, `category.deleted`)
- Catalog Items (`catalog.item.created`, `catalog.item.updated`, `catalog.item.deleted`)
- Item Prices (`catalog.price.updated`)
- Stock Levels (`stock.level.changed`)
- Bulk Pricing Rules (`bulk.pricing.rule.added`, `.updated`, `.deleted` - actual topic name in `application.yml`)
- Cart Checkout (`cart.checked_out`)

Topic names are configurable in `application.yml`.

---

This README provides a starting point. Further details on specific configurations, advanced deployment scenarios, or operational procedures would be added as the project evolves.
