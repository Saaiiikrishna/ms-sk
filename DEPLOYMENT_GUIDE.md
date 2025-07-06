# Deployment Guide

This project consists of multiple Spring Boot microservices. To run them locally or deploy to a Kubernetes cluster you need the following software installed.

## Required software

| Software | Version | Download link |
|----------|---------|---------------|
| Java JDK | 17 | https://adoptium.net/en-GB/temurin/releases/?version=17 |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| Kubernetes (kubectl) | 1.27+ | https://kubernetes.io/docs/tasks/tools/ |
| Kind (local k8s) or Minikube | latest | https://kind.sigs.k8s.io/docs/user/quick-start/ |
| PostgreSQL | 13+ | https://www.postgresql.org/download/ |
| Redis | 6+ | https://redis.io/download |

## Local build

1. Install the software listed above.
2. Ensure `JAVA_HOME` points to JDK 17.
3. Clone this repository and run `mvn package` from the project root to build all modules.
4. Each service has a `Dockerfile`. Build images using for example:
   ```bash
   docker build -t auth-service:latest auth-service
   ```
   Repeat for other services.

## Running with Docker Compose

1. Create a docker network and start PostgreSQL, Redis, and Kafka as needed. Example compose files can be authored using the ports expected in each service's `application.yml`.
2. Start each service container pointing to the shared network. Environment variables for database connection, Kafka brokers, etc., are defined in the `application.yml` of each service.

## Running on Kubernetes

1. Install a local Kubernetes cluster with Kind or Minikube.
2. Build Docker images and load them into the cluster using `kind load docker-image`. Alternatively push them to a registry accessible by the cluster.
3. Inside each service directory under `k8s` you will find deployment YAMLs. Apply them using:
   ```bash
   kubectl apply -f k8s/
   ```
4. Ensure secrets and config maps for database credentials, Kafka bootstrap servers and Keycloak settings are created. Adjust `k8s/` manifests if necessary to reference these secrets.
5. Expose services via Ingress or port-forwarding. Example:
   ```bash
   kubectl port-forward svc/auth-service 8080:8080
   ```

## Notes
- The services assume external dependencies such as Keycloak, Kafka and Redis are available. In development these can be started via Docker containers.
- Database schemas are automatically created by Flyway on application startup.
- For a production deployment you should configure persistent databases and secure secret management.

