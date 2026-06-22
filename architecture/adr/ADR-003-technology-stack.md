# ADR-003 Technology Stack

Status: Accepted

Date: 2026-06-19

## Context

The platform requires:

* High scalability
* Event-driven communication
* Cloud-native deployment
* Long-term maintainability
* Enterprise support
* AI-assisted development

Technology choices should prioritize ecosystem maturity and operational reliability.

## Decision

### Language

Java 21 LTS

### Framework

Spring Boot 4.1.x

### Build System

Maven

### SQL Database

PostgreSQL 17

### Document Database

MongoDB 8.x

MongoDB is not the default persistence technology and shall only be used for approved use cases.

### Cache

Redis 8.x

### Event Streaming

Apache Kafka 4.x

### Change Data Capture

Debezium

### Search Engine

OpenSearch

### API Gateway

Spring Cloud Gateway

### Service Discovery

Dual Mode Strategy

Development:

* Eureka

Production:

* Kubernetes Native Discovery

### Configuration Management

Dual Mode Strategy

Development:

* Spring Cloud Config Server

Production:

* Kubernetes ConfigMaps and Secrets

### Container Runtime

Docker

### Orchestration

Kubernetes

### Monitoring

Prometheus

### Dashboards

Grafana

### Logging

Loki

### Distributed Tracing

Tempo

### Telemetry

OpenTelemetry

### Database Migration

Flyway

### Authentication

Keycloak

### Authorization

OAuth2 + OpenID Connect

### Internal Service Security

JWT + mTLS

## Consequences

### Positive

* Mature ecosystem
* Cloud-native architecture
* Strong observability
* Enterprise-grade security

### Negative

* Increased operational complexity
* Higher infrastructure footprint

## Alternatives Considered

* RabbitMQ
* NATS JetStream
* MySQL
* Elasticsearch
* Consul

These alternatives may be reconsidered in future ADRs if platform requirements change.

## Related ADRs

* ADR-005 Service Communication
* ADR-006 Database Strategy
* ADR-009 Event Driven Architecture
* ADR-011 Security Foundation
