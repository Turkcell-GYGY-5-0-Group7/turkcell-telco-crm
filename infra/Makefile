# Telco CRM - local infrastructure helper.
# Run from the infra/ directory: `make up`, `make observability`, `make down`, etc.

COMPOSE_FILE := docker/compose.yml
ENV_FILE := docker/.env
ENV_EXAMPLE := docker/.env.example
COMPOSE := docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE)

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

.PHONY: register-connectors
register-connectors: ## Register Debezium connectors from docker/kafka-connect/connectors
	./docker/kafka-connect/register-connectors.sh

.PHONY: down
down: ## Stop and remove containers (keeps data volumes)
	$(COMPOSE) --profile tools --profile observability --profile auth down

.PHONY: destroy
destroy: ## Stop and remove containers AND data volumes (full reset)
	$(COMPOSE) --profile tools --profile observability --profile auth down -v
