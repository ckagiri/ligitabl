# ==============================================================================
# Makefile for ligitabl (Spring Boot + Maven + Docker)
# ==============================================================================

# ------------------------------------------------------------------------------
# Project Configuration
# ------------------------------------------------------------------------------
APP_NAME := ligitabl
API_DIR := api
API_POM := $(API_DIR)/pom.xml
ARTIFACT_ID := $(shell mvn -q -f $(API_POM) -DforceStdout help:evaluate -Dexpression=project.artifactId)
VERSION := $(shell mvn -q -f $(API_POM) -DforceStdout help:evaluate -Dexpression=project.version)
JAR := $(API_DIR)/target/$(ARTIFACT_ID)-$(VERSION).jar

# Docker configuration
IMAGE ?= $(APP_NAME):dev
PORT ?= 8080
DOCKER_COMPOSE ?= docker compose

# ------------------------------------------------------------------------------
# Environment Configuration (SIMPLIFIED)
# ------------------------------------------------------------------------------
# Safety first: Use test environment by default
ENV ?= test
HOST_DB_PORT ?= 55432

# Load environment file based on ENV
ENV_FILE := .env.$(ENV)
ENV_LOCAL_FILE := .env.$(ENV).local

# Load base environment file
-include $(ENV_FILE)
export

# Load local overrides (gitignored)
-include $(ENV_LOCAL_FILE)
export

# Seeding configuration
SEEDING_CONFIG ?= seeding-config.yaml

# ------------------------------------------------------------------------------
# Help & Info
# ------------------------------------------------------------------------------
.PHONY: help
help: ## Show this help message
	@echo "ligitabl Makefile - Current environment: $(ENV)"
	@echo "Environment file: $(ENV_FILE) $(if $(wildcard $(ENV_FILE)),(found),(NOT FOUND))"
	@echo "Local overrides: $(ENV_LOCAL_FILE) $(if $(wildcard $(ENV_LOCAL_FILE)),(found),(NOT FOUND))"
	@echo ""
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-25s\033[0m %s\n", $$1, $$2}' | \
		sort

.PHONY: env-info
env-info: ## Show current environment configuration
	@echo "Current environment: $(ENV)"
	@echo "Environment file: $(ENV_FILE)"
	@echo "Local overrides: $(ENV_LOCAL_FILE)"
	@echo ""
	@echo "Database configuration:"
	@echo "  DB_HOST: $(DB_HOST)"
	@echo "  DB_PORT: $(DB_PORT)"
	@echo "  DB_NAME: $(DB_NAME)"
	@echo "  DB_USER: $(DB_USER)"
	@echo "  HOST_DB_PORT: $(HOST_DB_PORT)"
	@echo ""
	@echo "To use dev environment: make <target> ENV=dev"

# ==============================================================================
# BUILD TARGETS
# ==============================================================================

.PHONY: build
build: ## Build the project (skip tests)
	mvn -q -DskipTests -pl $(API_DIR) -am clean package

.PHONY: api-build
api-build: ## Build the API module (skip tests)
	mvn -q -DskipTests -pl $(API_DIR) clean
	mvn -q -DskipTests -pl $(API_DIR) -am package

.PHONY: clean
clean: ## Clean build artifacts
	mvn -f $(API_DIR)/pom.xml clean

$(JAR):
	mvn -q -DskipTests -f $(API_DIR)/pom.xml package

# ==============================================================================
# CODE GENERATION TARGETS
# ==============================================================================

.PHONY: codegen
codegen: ## Run jOOQ code generation (full)
	mvn -q -DskipTests -pl jooq-codegen -am install
	mvn -q -DskipTests -Pwith-jooq -pl model -am \
		-DDB_HOST=$(DB_HOST) -DDB_PORT=$(DB_PORT) -DDB_NAME=$(DB_NAME) \
		-DDB_USER=$(DB_USER) -DDB_PASSWORD=$(DB_PASSWORD) \
		generate-sources

.PHONY: codegen-fast
codegen-fast: ## Run jOOQ code generation (assumes jooq-codegen installed)
	mvn -q -DskipTests -Pwith-jooq -pl model -am \
		-DDB_HOST=$(DB_HOST) -DDB_PORT=$(DB_PORT) -DDB_NAME=$(DB_NAME) \
		-DDB_USER=$(DB_USER) -DDB_PASSWORD=$(DB_PASSWORD) \
		generate-sources

.PHONY: model-compile
model-compile: ## Regenerate jOOQ and compile the model
	mvn -q -DskipTests -Pwith-jooq -pl model -am generate-sources compile

.PHONY: model-codegen-local
model-codegen-local: ## Start DB, run migrations, then jOOQ codegen
	$(MAKE) compose-up-db
	$(MAKE) migrate
	$(MAKE) codegen

# ==============================================================================
# DATABASE TARGETS
# ==============================================================================

.PHONY: migrate
migrate: ## Run Liquibase migrations
	mvn -q -Pliquibase -DskipTests -f model/pom.xml \
		-DDB_HOST=$(DB_HOST) -DDB_PORT=$(DB_PORT) -DDB_NAME=$(DB_NAME) \
		-DDB_USER=$(DB_USER) -DDB_PASSWORD=$(DB_PASSWORD) \
		liquibase:update

.PHONY: drop-db
drop-db: ## ⚠️  Drop the database (ENV=$(ENV))
	@echo "⚠️  WARNING: About to drop database '$(DB_NAME)' (ENV=$(ENV))"
	@if [ "$(ENV)" = "dev" ]; then \
		echo "⚠️  You are targeting the DEV environment!"; \
		read -p "Are you sure? Type 'yes' to confirm: " confirm; \
		if [ "$$confirm" != "yes" ]; then \
			echo "Aborted."; \
			exit 1; \
		fi; \
	fi
	@if ! docker ps --format '{{.Names}}' | grep -q '^ligitabl-db$$'; then \
		echo "Postgres container 'ligitabl-db' not running. Start it with 'make compose-up-db'"; \
		exit 1; \
	fi
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 \
		-c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$(DB_NAME)';" || true
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 \
		-c "DROP DATABASE IF EXISTS $(DB_NAME) WITH (FORCE);"

.PHONY: reset-db
reset-db: ## ⚠️  Drop and recreate the database (ENV=$(ENV))
	$(MAKE) drop-db
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 \
		-c "CREATE DATABASE $(DB_NAME) OWNER $(DB_USER);"

# ==============================================================================
# SEEDING TARGETS
# ==============================================================================

.PHONY: db-seed
db-seed: ## Seed reference data
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default

.PHONY: db-seed-demo
db-seed-demo: ## Seed demo league data
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/demo-main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default
	$(MAKE) db-seed-season SEEDING_CONFIG=seeding-config-demo.yaml

.PHONY: db-seed-season
db-seed-season: ## Seed season extras
	$(MAKE) compose-up-db
	$(MAKE) api-build
	java -jar $(JAR) --spring.main.web-application-type=none --seed-season \
		--seeding.config=$(SEEDING_CONFIG)

.PHONY: db-seed-users
db-seed-users: ## Seed users for testing
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default

.PHONY: db-seed-all
db-seed-all: ## Seed both reference and demo data
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default
	java -Dseed.main=seeding/demo-main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default

# ==============================================================================
# DATA IMPORT TARGETS
# ==============================================================================

.PHONY: import-competition
import-competition: ## Import matches for a competition (COMP=XX, ENV=test by default)
	@if [ -z "$(COMP)" ]; then \
		echo "Error: COMP is required"; \
		echo "Usage: make import-competition COMP=PL [ENV=test|dev]"; \
		exit 1; \
	fi; \
	FOOTBALL_DATA_API_TOKEN=$${FOOTBALL_DATA_API_TOKEN:-$${API_FOOTBALL_DATA_KEY:-}}; \
	if [ -z "$$FOOTBALL_DATA_API_TOKEN" ] || [ "$$FOOTBALL_DATA_API_TOKEN" = "your-api-token-here" ]; then \
		echo "Error: FOOTBALL_DATA_API_TOKEN is not set"; \
		echo "Set API_FOOTBALL_DATA_KEY in .env.$(ENV).local"; \
		exit 1; \
	fi; \
	export FOOTBALL_DATA_API_TOKEN; \
	echo "Importing $(COMP) to $(DB_NAME) (ENV=$(ENV))"; \
	$(MAKE) compose-up-db; \
	$(MAKE) db-seed; \
	$(MAKE) api-build; \
	java -jar $(JAR) \
		--spring.main.web-application-type=none \
		--workflow.run=true \
		--workflow.competition=$(COMP) \
		--workflow.exit-after=true

.PHONY: import-pl
import-pl: ## Import Premier League (ENV=test by default)
	$(MAKE) import-competition COMP=PL

.PHONY: import-bl
import-bl: ## Import Bundesliga (ENV=test by default)
	$(MAKE) import-competition COMP=BL

.PHONY: import-sa
import-sa: ## Import Serie A (ENV=test by default)
	$(MAKE) import-competition COMP=SA

.PHONY: import-pd
import-pd: ## Import La Liga (ENV=test by default)
	$(MAKE) import-competition COMP=PD

.PHONY: import-fl1
import-fl1: ## Import Ligue 1 (ENV=test by default)
	$(MAKE) import-competition COMP=FL1

# ==============================================================================
# DOCKER TARGETS
# ==============================================================================

.PHONY: docker-build
docker-build: ## Build Docker image
	docker build -t $(IMAGE) -f $(API_DIR)/Dockerfile $(API_DIR)

.PHONY: docker-run
docker-run: ## Run container on port $(PORT)
	@if [ -f $(ENV_FILE) ]; then ENV_ARG='--env-file $(ENV_FILE)'; else ENV_ARG=''; fi; \
	docker run --rm $$ENV_ARG -p $(PORT):8080 --name $(APP_NAME) \
		-e JAVA_OPTS="$(JAVA_OPTS)" $(IMAGE)

.PHONY: docker-stop
docker-stop: ## Stop running container
	- docker rm -f $(APP_NAME)

# ==============================================================================
# DOCKER COMPOSE TARGETS
# ==============================================================================

.PHONY: compose-up
compose-up: ## Start app + postgres
	$(DOCKER_COMPOSE) up -d --build

.PHONY: compose-up-db
compose-up-db: ## Start postgres (uses ENV=$(ENV))
	@echo "Starting database for ENV=$(ENV) ($(DB_NAME) on port $(DB_PORT))"
	DB_PORT=$(if $(DB_PORT),$(DB_PORT),$(HOST_DB_PORT)) $(DOCKER_COMPOSE) up -d db

.PHONY: compose-up-db-attached
compose-up-db-attached: ## Start postgres with logs
	DB_PORT=$(if $(DB_PORT),$(DB_PORT),$(HOST_DB_PORT)) $(DOCKER_COMPOSE) up db

.PHONY: compose-stop-db
compose-stop-db: ## Stop postgres
	$(DOCKER_COMPOSE) stop db

.PHONY: compose-up-app
compose-up-app: ## Start app container
	$(MAKE) api-build
	$(DOCKER_COMPOSE) up -d --build app

.PHONY: compose-up-app-fast
compose-up-app-fast: ## Start app without rebuild
	$(DOCKER_COMPOSE) up -d app

.PHONY: compose-logs-app
compose-logs-app: ## Tail app container logs
	$(DOCKER_COMPOSE) logs -f app

.PHONY: compose-stop-app
compose-stop-app: ## Stop app container
	$(DOCKER_COMPOSE) stop app

.PHONY: compose-restart-app
compose-restart-app: ## Restart app container
	$(DOCKER_COMPOSE) restart app

.PHONY: compose-refresh
compose-refresh: ## Rebuild and restart app
	$(MAKE) compose-stop-app
	$(MAKE) compose-stop-db
	$(MAKE) compose-up-db
	$(MAKE) compose-up-app

.PHONY: compose-ps
compose-ps: ## Show status of services
	$(DOCKER_COMPOSE) ps

.PHONY: compose-stop
compose-stop: ## Stop all services
	$(DOCKER_COMPOSE) stop

.PHONY: compose-down
compose-down: ## Stop and remove services/volumes
	$(DOCKER_COMPOSE) down -v

# ==============================================================================
# RUN TARGETS
# ==============================================================================

.PHONY: run
run: $(JAR) ## Run the built JAR
	java -jar $(JAR)

.PHONY: run-app
run-app: ## Start DB and run the app JAR
	$(MAKE) compose-up-db
	$(MAKE) run

.PHONY: run-api
run-api: ## Start DB and run API via spring-boot:run (ENV=$(ENV))
	$(MAKE) compose-up-db
	mvn -q -pl $(API_DIR) -am spring-boot:run

# ==============================================================================
# TEST TARGETS
# ==============================================================================

.PHONY: test
test: ## Run full API test suite
	mvn -pl $(API_DIR) -am test

.PHONY: test-unit
test-unit: ## Run pure unit tests
	mvn -q -P unit-tests,no-jooq -pl $(API_DIR) -am \
		-Dsurefire.failIfNoSpecifiedTests=false -Dtest='**/*GuardTest' test

.PHONY: test-api-core
test-api-core: ## Run core API tests (skip *IT)
	mvn -q -pl $(API_DIR) -am -DskipITs test

.PHONY: test-api-it
test-api-it: ## Run DB-backed integration tests
	mvn -q -pl $(API_DIR) -am -DskipITs=false -Dtest='**/*IT' \
		-Dsurefire.failIfNoSpecifiedTests=false test

.PHONY: test-api-all
test-api-all: ## Run all API tests
	mvn -pl $(API_DIR) -am test

.PHONY: test-all
test-all: ## Run full test suite
	mvn test

# ==============================================================================
# DEVELOPMENT WORKFLOW TARGETS
# ==============================================================================

.PHONY: dev-reset
dev-reset: ## Reset DB, migrate, codegen, seed (ENV=$(ENV))
	$(MAKE) compose-up-db
	$(MAKE) reset-db
	$(MAKE) migrate
	$(MAKE) codegen
	$(MAKE) db-seed

.PHONY: dev-reset-all
dev-reset-all: ## Reset DB and seed all data (ENV=$(ENV))
	$(MAKE) compose-up-db
	$(MAKE) reset-db
	$(MAKE) migrate
	$(MAKE) codegen
	$(MAKE) db-seed-all

# ==============================================================================
# CODE QUALITY TARGETS
# ==============================================================================

.PHONY: format
format: ## Format all Java sources
	mvn -q -pl api,model com.diffplug.spotless:spotless-maven-plugin:2.44.0:apply

.PHONY: format-check
format-check: ## Check formatting
	mvn -q -pl api,model com.diffplug.spotless:spotless-maven-plugin:2.44.0:check
