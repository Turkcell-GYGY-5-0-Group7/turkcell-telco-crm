# Telco CRM - local infrastructure helper.
# Run from the infra/ directory: `make up`, `make observability`, `make down`, etc.

COMPOSE_FILE := docker/compose.yml
ENV_FILE := docker/.env
ENV_EXAMPLE := docker/.env.example
COMPOSE := docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE)
CONNECT_URL ?= http://localhost:8083

# --- Sprint 16 E2E subset ---------------------------------------------------
# The full stack (23 containers, ~15 JVMs) does not fit in the ~8.6 GB a typical
# dev machine can give Docker - it OOM-hangs the engine. This is the exact subset
# the Sprint 16 E2E needs (login -> catalog -> register/KYC -> order -> pay ->
# activate -> usage -> invoice), which fits with headroom. See the MEMORY BUDGET
# block in docker/compose.yml for the per-container budget and for why
# identity/notification/ticket/mongo/kafka-ui/schema-registry are all excluded.
# kafka-connect IS included: Debezium delivers the outbox, without which the
# onboarding saga never completes.
SPRINT16_SERVICES := postgres redis kafka kafka-connect minio createbuckets \
                     keycloak keycloak-config config-server discovery-server api-gateway \
                     product-catalog-service customer-service order-service payment-service \
                     subscription-service usage-service billing-service web-bff

# Only the services above have an outbox_event table (Flyway creates it at service
# startup), so only their connectors can be registered.
SPRINT16_CONNECTORS := customer product-catalog order payment subscription usage billing

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show available targets
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

$(ENV_FILE):
	@cp $(ENV_EXAMPLE) $(ENV_FILE) && echo "Created $(ENV_FILE) from template."

.PHONY: init
init: $(ENV_FILE) ## Create docker/.env from the template if missing

.PHONY: up
up: init ## Start core infra (postgres, redis, kafka, schema-registry, kafka-connect)
	$(COMPOSE) up -d

.PHONY: platform
platform: init ## Start core infra + Spring platform services (config-server, discovery-server, api-gateway)
	$(COMPOSE) --profile platform up -d

.PHONY: platform-build
platform-build: init ## Build Docker images for Spring platform services (run once before 'make platform')
	$(COMPOSE) --profile platform build

.PHONY: up-all
up-all: init ## Start everything (core + platform + tools + observability + auth)
	$(COMPOSE) --profile platform --profile tools --profile observability --profile auth up -d

.PHONY: apps-build
apps-build: init ## Build Docker images for all 10 domain services (apps profile)
	$(COMPOSE) --profile apps build

.PHONY: apps
apps: init ## Start core + platform + all 10 domain services (apps profile)
	$(COMPOSE) --profile platform --profile apps up -d

.PHONY: up-apps
up-apps: apps ## Alias for 'apps'

.PHONY: up-full-stack
up-full-stack: init ## Start core + auth + platform + all domain services (full acceptance stack)
	$(COMPOSE) --profile auth --profile platform --profile apps up -d

.PHONY: observability
observability: init ## Start core + observability stack
	$(COMPOSE) --profile observability up -d

.PHONY: tools
tools: init ## Start core + kafka-ui
	$(COMPOSE) --profile tools up -d

.PHONY: auth
auth: init ## Start core + keycloak
	$(COMPOSE) --profile auth up -d

.PHONY: ps
ps: ## List running containers
	$(COMPOSE) ps

.PHONY: logs
logs: ## Tail logs (use S=<service> for one service)
	$(COMPOSE) logs -f $(S)

.PHONY: sprint16-e2e-build
sprint16-e2e-build: init ## Build images for the Sprint 16 E2E subset (first-time only)
	$(COMPOSE) --profile platform --profile apps build

.PHONY: sprint16-e2e
sprint16-e2e: init ## Start ONLY the services the Sprint 16 E2E needs (fits ~8.6 GB; see compose.yml MEMORY BUDGET)
	$(COMPOSE) --profile platform --profile auth --profile apps up -d $(SPRINT16_SERVICES)
	@echo ""
	@echo "Sprint 16 E2E subset starting (18 containers, est. ~7.75 GB RSS)."
	@echo "Once kafka-connect is healthy, register the connectors:"
	@echo "    make -C infra sprint16-connectors"

.PHONY: sprint16-connectors
sprint16-connectors: ## Register only the Debezium connectors the Sprint 16 E2E needs
	./docker/kafka-connect/register-connectors.sh $(CONNECT_URL) $(SPRINT16_CONNECTORS)

.PHONY: register-connectors
register-connectors: ## Register Debezium connectors (C="customer order" registers only those)
	./docker/kafka-connect/register-connectors.sh $(CONNECT_URL) $(C)

.PHONY: down
down: ## Stop and remove containers (keeps data volumes)
	$(COMPOSE) --profile tools --profile observability --profile auth --profile platform --profile apps down

.PHONY: destroy
destroy: ## Stop and remove containers AND data volumes (full reset)
	$(COMPOSE) --profile tools --profile observability --profile auth --profile platform --profile apps down -v
