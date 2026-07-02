# =============================================================================
# Telco CRM - Root Makefile
#
# Single entry point for all developer operations. Delegates infrastructure
# commands to infra/Makefile; owns the Maven build and Postman setup here.
#
# Usage: make <target>   (run from the repo root)
# =============================================================================

INFRA := $(MAKE) --no-print-directory -C infra
S     ?=

# Pin Maven to Java 21 regardless of what JAVA_HOME is set to in the shell.
# Maven picks up JAVA_HOME; without it, it falls through to whatever JDK is
# on PATH (which on this machine is Homebrew Java 25, incompatible with
# SpotBugs and JaCoCo). /usr/libexec/java_home -v 21 returns the correct path
# on macOS when Oracle JDK 21 is installed.
export JAVA_HOME := $(shell /usr/libexec/java_home -v 21 2>/dev/null || echo "")

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show all available targets
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-28s\033[0m %s\n", $$1, $$2}'

# =============================================================================
# Build (Maven)
# =============================================================================

.PHONY: build-platform
build-platform: ## Build and install platform BOM, core modules, and starters (skip tests)
	cd platform && mvn -B -ntp clean install -DskipTests

.PHONY: build-services
build-services: ## Compile all microservices (skip tests)
	cd microservices && mvn -B -ntp clean compile

.PHONY: build
build: build-platform build-services ## Full reactor build: platform then all microservices

.PHONY: test
test: ## Run all microservice tests (builds platform first; skips JaCoCo for Java 21 + JaCoCo compat)
	cd platform && mvn -B -ntp clean install -DskipTests
	cd microservices && mvn -B -ntp clean verify -Djacoco.skip=true --fail-at-end

# =============================================================================
# Infrastructure (delegates to infra/Makefile)
# =============================================================================

.PHONY: infra-init
infra-init: ## Create infra/docker/.env from the template if missing
	$(INFRA) init

.PHONY: infra-up
infra-up: ## Start core infra: postgres, redis, kafka, schema-registry, kafka-connect, minio
	$(INFRA) up

.PHONY: infra-platform
infra-platform: ## Start core infra + Spring platform services (config, discovery, gateway)
	$(INFRA) platform

.PHONY: infra-platform-build
infra-platform-build: ## Build Docker images for the Spring platform services (first-time only)
	$(INFRA) platform-build

.PHONY: infra-auth
infra-auth: ## Start core infra + Keycloak
	$(INFRA) auth

.PHONY: infra-observability
infra-observability: ## Start core infra + observability (Prometheus, Grafana, Loki, Tempo)
	$(INFRA) observability

.PHONY: infra-tools
infra-tools: ## Start core infra + Kafka UI
	$(INFRA) tools

.PHONY: infra-up-all
infra-up-all: ## Start everything: core + platform + auth + observability + tools
	$(INFRA) up-all

.PHONY: infra-down
infra-down: ## Stop all infra containers (keeps data volumes)
	$(INFRA) down

.PHONY: infra-destroy
infra-destroy: ## Stop all infra containers AND delete all data volumes (full reset)
	$(INFRA) destroy

.PHONY: infra-ps
infra-ps: ## List running infra containers
	$(INFRA) ps

.PHONY: infra-logs
infra-logs: ## Tail infra logs (e.g. make infra-logs S=telco-kafka)
	$(INFRA) logs S=$(S)

.PHONY: infra-connectors
infra-connectors: ## Register Debezium CDC connectors after kafka-connect is healthy
	$(INFRA) register-connectors

# =============================================================================
# Postman
# =============================================================================

.PHONY: postman-hosts-mac
postman-hosts-mac: ## Add api.localhost -> 127.0.0.1 to /etc/hosts (macOS / Linux, needs sudo)
	./postman/scripts/setup-hosts.sh

.PHONY: postman-hosts-win
postman-hosts-win: ## Add api.localhost -> 127.0.0.1 to hosts file (Windows, run as Administrator)
	powershell -ExecutionPolicy Bypass -File postman/scripts/setup-hosts.ps1

# =============================================================================
# Developer workflows
# =============================================================================

.PHONY: setup
setup: infra-init build-platform ## First-time setup: create .env and build the platform
	@echo ""
	@echo "Setup complete. Run 'make dev' to start the local developer stack."

.PHONY: dev
dev: infra-auth ## Start the minimum dev stack (core infra + Keycloak) for local service development
	@echo ""
	@echo "Dev stack running."
	@echo "Start a service: cd microservices && mvn spring-boot:run -pl <service-name> -Dspring-boot.run.profiles=dev"

.PHONY: full
full: build infra-up-all ## Build everything and bring up the full stack (including observability)
