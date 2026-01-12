# ==============================================================================
# Makefile for ligitabl (Spring Boot + Maven + Docker)
# SAFETY-FIRST: No fallbacks, explicit environments, loud failures
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
# Environment Configuration (SAFETY-FIRST)
# ------------------------------------------------------------------------------
# Defaults to test for maximum safety
ENV ?= test

# Only allow specific environment values
VALID_ENVS := test dev prod
ifeq (,$(filter $(ENV),$(VALID_ENVS)))
    $(error ❌ Invalid ENV='$(ENV)'. Valid options: test, dev, prod)
endif

# Environment files (no fallbacks!)
ENV_FILE := .env.$(ENV)
ENV_LOCAL_FILE := .env.$(ENV).local

# Verify environment file exists - FAIL LOUDLY if not
ifeq (,$(wildcard $(ENV_FILE)))
    $(error ❌ $(ENV_FILE) not found!\
    \nCreate it with: cp env.$(ENV).template $(ENV_FILE)\
    \nThen edit with your settings.)
endif

# Load environment files
include $(ENV_FILE)
export

# Load local overrides if present (optional)
-include $(ENV_LOCAL_FILE)
export

# Seeding configuration
SEEDING_CONFIG ?= seeding-config.yaml

# ------------------------------------------------------------------------------
# Optional integrations
# ------------------------------------------------------------------------------
# The API expects FOOTBALL_DATA_API_TOKEN (see api application.yml). For local dev,
# we also allow providing API_FOOTBALL_DATA_KEY in the env files.
EXPORT_FOOTBALL_DATA_API_TOKEN = FOOTBALL_DATA_API_TOKEN="$${FOOTBALL_DATA_API_TOKEN:-$${API_FOOTBALL_DATA_KEY:-}}"; if [ -z "$$FOOTBALL_DATA_API_TOKEN" ] || [ "$$FOOTBALL_DATA_API_TOKEN" = "your-api-token-here" ]; then echo "⚠️  FOOTBALL_DATA_API_TOKEN is not set; football-data.org requests may fail."; echo "   Set API_FOOTBALL_DATA_KEY in $(ENV_LOCAL_FILE) (or $(ENV_FILE))."; else export FOOTBALL_DATA_API_TOKEN; fi

# ------------------------------------------------------------------------------
# Production Safety Checks
# ------------------------------------------------------------------------------
ifeq ($(ENV),prod)
    # Verify PROD_CONFIRMED is set to prevent accidental prod operations
    ifndef PROD_CONFIRMED
        $(error ❌ PRODUCTION ENVIRONMENT BLOCKED!\
        \n\
        \n⚠️⚠️⚠️  YOU ARE TARGETING PRODUCTION  ⚠️⚠️⚠️\
        \n\
        \nTo confirm, run:\
        \n  make <target> ENV=prod PROD_CONFIRMED=yes\
        \n\
        \nBe ABSOLUTELY CERTAIN this is what you want!)
    endif

    ifneq ($(PROD_CONFIRMED),yes)
        $(error ❌ PROD_CONFIRMED must be 'yes' (you provided: '$(PROD_CONFIRMED)')\
        \nRun: make <target> ENV=prod PROD_CONFIRMED=yes)
    endif

    # Extra warning for destructive operations
    PROD_WARNING := 🔥🔥🔥 PRODUCTION DATABASE 🔥🔥🔥
endif

# ------------------------------------------------------------------------------
# Database Name Safety Validation
# ------------------------------------------------------------------------------
# Prevent common production-like database names in test/dev environments
ifeq ($(ENV),test)
    # Test environment should have 'test' in the name
    ifeq (,$(findstring test,$(DB_NAME)))
        $(warning ⚠️  WARNING: DB_NAME='$(DB_NAME)' doesn't contain 'test')
        $(warning ⚠️  Expected something like: ligitabl_test)
        $(warning ⚠️  Double-check your .env.test file!)
    endif
endif

ifeq ($(ENV),dev)
    # Dev environment should have 'dev' in the name
    ifeq (,$(findstring dev,$(DB_NAME)))
        $(warning ⚠️  WARNING: DB_NAME='$(DB_NAME)' doesn't contain 'dev')
        $(warning ⚠️  Expected something like: ligitabl_dev)
        $(warning ⚠️  Double-check your .env.dev file!)
    endif
endif

# Block obvious production database names in test/dev
FORBIDDEN_NAMES := ligitabl_prod production prod_db db_prod
ifneq ($(ENV),prod)
    ifneq (,$(filter $(DB_NAME),$(FORBIDDEN_NAMES)))
        $(error ❌ BLOCKED: DB_NAME='$(DB_NAME)' looks like production!\
        \nYou're using ENV=$(ENV) but targeting a prod-like database.\
        \nCheck your $(ENV_FILE) file.)
    endif
endif

# ------------------------------------------------------------------------------
# Help & Info
# ------------------------------------------------------------------------------
.PHONY: help
help: ## Show this help message
	@echo "ligitabl Makefile - SAFETY-FIRST Edition"
	@echo ""
	@echo "Current environment: $(ENV)"
	@echo "Environment file: $(ENV_FILE) ✓"
	@echo "Local overrides: $(ENV_LOCAL_FILE) $(if $(wildcard $(ENV_LOCAL_FILE)),(found),(not present))"
	@echo "Database: $(DB_NAME)"
	@echo ""
	@echo "Environments:"
	@echo "  test (default) - Safe for experiments, destructive ops allowed"
	@echo "  dev            - Development database, requires confirmation for drops"
	@echo "  prod           - Production, requires PROD_CONFIRMED=yes flag"
	@echo ""
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-25s\033[0m %s\n", $$1, $$2}' | \
		sort

.PHONY: env-info
env-info: ## Show detailed environment configuration
	@echo "╔════════════════════════════════════════════════════════════╗"
	@echo "║                  Environment Configuration                  ║"
	@echo "╚════════════════════════════════════════════════════════════╝"
	@echo ""
	@echo "Environment: $(ENV)"
	@echo "Config file: $(ENV_FILE)"
	@echo "Local file:  $(ENV_LOCAL_FILE) $(if $(wildcard $(ENV_LOCAL_FILE)),(✓ present),(not present))"
	@echo ""
	@echo "Database Settings:"
	@echo "  Host:     $(DB_HOST)"
	@echo "  Port:     $(DB_PORT)"
	@echo "  Database: $(DB_NAME)"
	@echo "  User:     $(DB_USER)"
	@echo "  App Port: $(PORT)"
	@echo ""
	@echo "Usage Examples:"
	@echo "  Test DB:  make dev-reset"
	@echo "  Dev DB:   make dev-reset ENV=dev"
	@echo "  Prod DB:  make migrate ENV=prod PROD_CONFIRMED=yes"
	@echo ""
ifeq ($(ENV),prod)
	@echo "⚠️⚠️⚠️  YOU ARE IN PRODUCTION MODE  ⚠️⚠️⚠️"
	@echo ""
endif

.PHONY: env-check
env-check: ## Validate environment files exist
	@echo "Checking environment files..."
	@for env in test dev; do \
		if [ ! -f .env.$$env ]; then \
			echo "❌ .env.$$env not found"; \
			echo "   Create it: cp env.$$env.template .env.$$env"; \
		else \
			echo "✓ .env.$$env exists"; \
		fi; \
	done
	@echo ""
	@if [ -f .env.prod ]; then \
		echo "⚠️  .env.prod exists (should be gitignored)"; \
	else \
		echo "ℹ️  .env.prod not present (create only when needed)"; \
	fi

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
migrate: ## Run Liquibase migrations (ENV=$(ENV))
ifeq ($(ENV),prod)
	@echo "$(PROD_WARNING)"
	@echo "Running migrations on PRODUCTION: $(DB_NAME)"
	@echo ""
endif
	mvn -q -Pliquibase -DskipTests -f model/pom.xml \
		-DDB_HOST=$(DB_HOST) -DDB_PORT=$(DB_PORT) -DDB_NAME=$(DB_NAME) \
		-DDB_USER=$(DB_USER) -DDB_PASSWORD=$(DB_PASSWORD) \
		liquibase:update

.PHONY: drop-db
drop-db: ## ⚠️  Drop the database (ENV=$(ENV), DB=$(DB_NAME))
ifeq ($(ENV),prod)
	@echo "╔══════════════════════════════════════════════════════════════╗"
	@echo "║                    🔥🔥🔥 DANGER 🔥🔥🔥                         ║"
	@echo "║         YOU ARE ABOUT TO DROP PRODUCTION DATABASE            ║"
	@echo "╚══════════════════════════════════════════════════════════════╝"
	@echo ""
	@echo "Database: $(DB_NAME)"
	@echo "Host:     $(DB_HOST)"
	@echo ""
	@read -p "Type the database name '$(DB_NAME)' to confirm: " confirm; \
	if [ "$$confirm" != "$(DB_NAME)" ]; then \
		echo "❌ Aborted. Input did not match."; \
		exit 1; \
	fi
else ifeq ($(ENV),dev)
	@echo "⚠️  WARNING: About to drop DEV database: $(DB_NAME)"
	@read -p "Type 'yes' to confirm: " confirm; \
	if [ "$$confirm" != "yes" ]; then \
		echo "❌ Aborted."; \
		exit 1; \
	fi
else
	@echo "ℹ️  Dropping TEST database: $(DB_NAME)"
endif
	@if ! docker ps --format '{{.Names}}' | grep -q '^ligitabl-db$$'; then \
		echo "❌ Postgres container 'ligitabl-db' not running."; \
		echo "   Start it: make compose-up-db"; \
		exit 1; \
	fi
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 \
		-c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$(DB_NAME)';" || true
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 \
		-c "DROP DATABASE IF EXISTS $(DB_NAME) WITH (FORCE);"
	@echo "✓ Database $(DB_NAME) dropped"

.PHONY: reset-db
reset-db: ## ⚠️  Drop and recreate the database (ENV=$(ENV))
	$(MAKE) drop-db
	@echo "Creating database: $(DB_NAME)"
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 \
		-c "CREATE DATABASE $(DB_NAME) OWNER $(DB_USER);"
	@echo "✓ Database $(DB_NAME) created"

# ==============================================================================
# SEEDING TARGETS
# ==============================================================================

.PHONY: db-seed
db-seed: ## Seed reference data (ENV=$(ENV))
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default

.PHONY: db-seed-demo
db-seed-demo: ## Seed demo league data (ENV=$(ENV))
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/demo-main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default
	$(MAKE) db-seed-season SEEDING_CONFIG=seeding-config-demo.yaml

.PHONY: db-seed-season
db-seed-season: ## Seed season extras (ENV=$(ENV))
	$(MAKE) compose-up-db
	$(MAKE) api-build
	java -jar $(JAR) --spring.main.web-application-type=none --seed-season \
		--seeding.config=$(SEEDING_CONFIG)

.PHONY: db-seed-users
db-seed-users: ## Seed users for testing (ENV=$(ENV))
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar \
		--spring.profiles.active=default

.PHONY: db-seed-all
db-seed-all: ## Seed both reference and demo data (ENV=$(ENV))
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
import-competition: ## Import matches for a competition (COMP=XX, ENV=$(ENV))
	@if [ -z "$(COMP)" ]; then \
		echo "❌ Error: COMP is required"; \
		echo "Usage: make import-competition COMP=PL [ENV=test|dev|prod]"; \
		exit 1; \
	fi
	@FOOTBALL_DATA_API_TOKEN=$${FOOTBALL_DATA_API_TOKEN:-$${API_FOOTBALL_DATA_KEY:-}}; \
	if [ -z "$$FOOTBALL_DATA_API_TOKEN" ] || [ "$$FOOTBALL_DATA_API_TOKEN" = "your-api-token-here" ]; then \
		echo "❌ Error: FOOTBALL_DATA_API_TOKEN is not set"; \
		echo "Set API_FOOTBALL_DATA_KEY in $(ENV_LOCAL_FILE)"; \
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

# ==============================================================================
# STANDINGS WORKFLOW TARGETS
# ==============================================================================

.PHONY: calc-standings
calc-standings: ## Calculate standings for all rounds (ENV=$(ENV))
	$(MAKE) compose-up-db
	$(MAKE) api-build
	java -jar $(JAR) \
		--spring.main.web-application-type=none \
		--workflow.run-calc-standings=true \
		--workflow.exit-after=true

.PHONY: import-pl
import-pl: ## Import Premier League (ENV=$(ENV))
	$(MAKE) import-competition COMP=PL

.PHONY: import-bl
import-bl: ## Import Bundesliga (ENV=$(ENV))
	$(MAKE) import-competition COMP=BL

.PHONY: import-sa
import-sa: ## Import Serie A (ENV=$(ENV))
	$(MAKE) import-competition COMP=SA

.PHONY: import-pd
import-pd: ## Import La Liga (ENV=$(ENV))
	$(MAKE) import-competition COMP=PD

.PHONY: import-fl1
import-fl1: ## Import Ligue 1 (ENV=$(ENV))
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
compose-up-db: ## Start postgres (ENV=$(ENV), DB=$(DB_NAME))
	@echo "Starting database for ENV=$(ENV)"
	@echo "  Database: $(DB_NAME)"
	@echo "  Port:     $(DB_PORT)"
	DB_PORT=$(DB_PORT) $(DOCKER_COMPOSE) up -d db

.PHONY: compose-up-db-attached
compose-up-db-attached: ## Start postgres with logs
	DB_PORT=$(DB_PORT) $(DOCKER_COMPOSE) up db

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
compose-refresh: ## Rebuild and restart app (ENV=$(ENV))
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
run: $(JAR) ## Run the built JAR (ENV=$(ENV))
ifeq ($(ENV),prod)
	@echo "$(PROD_WARNING)"
	@echo "Starting application with PRODUCTION database"
	@echo ""
endif
	java -jar $(JAR)

.PHONY: run-app
run-app: ## Start DB and run the app JAR (ENV=$(ENV))
	$(MAKE) compose-up-db
	$(MAKE) run

.PHONY: run-api
run-api: ## Start DB and run API via spring-boot:run (ENV=$(ENV))
	$(MAKE) compose-up-db
	@$(EXPORT_FOOTBALL_DATA_API_TOKEN); mvn -q -f $(API_DIR)/pom.xml org.springframework.boot:spring-boot-maven-plugin:run

.PHONY: run-api-fake
run-api-fake: ## Start DB and run API with FAKE_DATA_ENABLED=true (ENV=$(ENV))
	$(MAKE) compose-up-db
	@$(EXPORT_FOOTBALL_DATA_API_TOKEN); FAKE_DATA_ENABLED=true mvn -q -f $(API_DIR)/pom.xml org.springframework.boot:spring-boot-maven-plugin:run

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

.PHONY: test-reset-run-api-fake
test-reset-run-api-fake: ## Reset test DB, migrate, codegen, seed reference data, then run API with fake data (DB_PORT=55433)
	$(MAKE) dev-reset ENV=test
	@$(EXPORT_FOOTBALL_DATA_API_TOKEN); FAKE_DATA_ENABLED=true DB_HOST=$(DB_HOST) DB_PORT=$(DB_PORT) DB_NAME=$(DB_NAME) DB_USER=$(DB_USER) DB_PASSWORD=$(DB_PASSWORD) \
		mvn -f $(API_DIR)/pom.xml org.springframework.boot:spring-boot-maven-plugin:run

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
