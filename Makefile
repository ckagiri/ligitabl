# Makefile for ligitabl (Spring Boot + Maven + Docker)

APP_NAME := ligitabl
API_DIR := api
API_POM := $(API_DIR)/pom.xml
ARTIFACT_ID := $(shell mvn -q -f $(API_POM) -DforceStdout help:evaluate -Dexpression=project.artifactId)
VERSION := $(shell mvn -q -f $(API_POM) -DforceStdout help:evaluate -Dexpression=project.version)
JAR := $(API_DIR)/target/$(ARTIFACT_ID)-$(VERSION).jar
IMAGE ?= $(APP_NAME):dev
PORT ?= 8080
DOCKER_COMPOSE ?= docker compose
# Default host port mapping for Postgres when using compose
HOST_DB_PORT ?= 55432

# Load variables from .env if present
ifneq (,$(wildcard .env))
	include .env
	export
endif

.PHONY: help build api-build model-compile test clean run run-no-db run-app bootstrap-run docker-build docker-run docker-stop compose-up compose-up-db compose-stop-db compose-up-app compose-up-app-fast compose-logs-app compose-stop-app compose-restart-app compose-refresh compose-refresh-gen compose-refresh-db compose-ps compose-stop compose-down codegen codegen-fast migrate seed seed-local prep-team prep-team-local drop-db reset-db db-bootstrap format format-check format-all test-unit test-api-no-jooq test-all

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | sed 's/:.*## /\t- /' | sort

build: ## Build the project (skip tests) - builds api and required modules (model, jooq-codegen)
	mvn -q -DskipTests -pl $(API_DIR) -am clean package

api-build: ## Build the API module (skip tests) - includes dependencies (model, jooq-codegen)
	mvn -q -DskipTests -pl $(API_DIR) -am package

test: ## Run API tests (default profile)
	mvn -f $(API_DIR)/pom.xml test

test-unit: ## Run pure unit tests (no Spring) in API module (no-jooq profile to avoid DB/codegen)
	mvn -q -P unit-tests,no-jooq -pl $(API_DIR) -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='**/*GuardTest' test

test-webmvc: ## Run MVC slice tests (@WebMvcTest) in API module (no-jooq profile to avoid DB/codegen)
	mvn -q -P unit-tests,webmvc-tests,no-jooq -pl $(API_DIR) -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='**/*WebMvcTest' test

test-api-no-jooq: ## Run API tests with no DB/codegen (skips jOOQ infra)
	mvn -P no-jooq -pl $(API_DIR) -am test

test-all: ## Run full test suite across modules
	mvn test

clean: ## Clean build artifacts
	mvn -f $(API_DIR)/pom.xml clean

run: $(JAR) ## Run the built JAR (DB required unless liquibase disabled)
	java -jar $(JAR)

run-no-db: $(JAR) ## Run without requiring DB (skips DataSource auto-config)
	java -jar $(JAR) --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

run-app: ## Start DB (compose) and run the app JAR
	$(MAKE) compose-up-db
	$(MAKE) run

bootstrap-run: ## Bootstrap DB (reset+migrate+codegen+seed) then run the app
	$(MAKE) db-bootstrap
	$(MAKE) run

$(JAR):
	mvn -q -DskipTests -f $(API_DIR)/pom.xml package

docker-build: ## Build Docker image
	docker build -t $(IMAGE) -f $(API_DIR)/Dockerfile $(API_DIR)

docker-run: ## Run container on port $(PORT)
	@if [ -f .env ]; then ENV_FILE='--env-file .env'; else ENV_FILE=''; fi; \
	  docker run --rm $$ENV_FILE -p $(PORT):8080 --name $(APP_NAME) -e JAVA_OPTS="$(JAVA_OPTS)" $(IMAGE)

docker-run-no-db: ## Run container without requiring DB (skips DataSource auto-config)
	@if [ -f .env ]; then ENV_FILE='--env-file .env'; else ENV_FILE=''; fi; \
	  docker run --rm $$ENV_FILE -p $(PORT):8080 --name $(APP_NAME) -e JAVA_OPTS="$(JAVA_OPTS)" \
	  -e SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration \
	  $(IMAGE)

docker-stop: ## Stop running container
	- docker rm -f $(APP_NAME)

compose-up: ## Start app + postgres via Docker Compose
	$(DOCKER_COMPOSE) up -d --build

compose-up-db: ## Start only postgres via Docker Compose with host port $(HOST_DB_PORT) -> 5432
	DB_PORT=$(HOST_DB_PORT) $(DOCKER_COMPOSE) up -d db

compose-stop-db: ## Stop only postgres via Docker Compose (keeps volumes)
	$(DOCKER_COMPOSE) stop db

compose-up-app: ## Start only the app (and its db dependency) via Docker Compose in the background
	$(MAKE) api-build
	$(DOCKER_COMPOSE) up -d --build app

compose-up-app-fast: ## Start only the app without rebuild (requires image to exist)
	$(DOCKER_COMPOSE) up -d app

compose-logs-app: ## Tail logs from the dockerized app (Ctrl+C to stop following)
	$(DOCKER_COMPOSE) logs -f app

compose-stop-app: ## Stop the dockerized app container (db keeps running)
	$(DOCKER_COMPOSE) stop app

compose-restart-app: ## Restart the dockerized app container
	$(DOCKER_COMPOSE) restart app

# Short, primary refresh targets
compose-refresh: ## Stop app+db, start db, then start app (rebuild image) — no codegen
	$(MAKE) compose-stop-app
	$(MAKE) compose-stop-db
	$(MAKE) compose-up-db
	$(MAKE) compose-up-app

compose-refresh-gen: ## Stop app+db, start db, run codegen, then start app (rebuild image)
	$(MAKE) compose-stop-app
	$(MAKE) compose-stop-db
	$(MAKE) compose-up-db
	$(MAKE) codegen
	$(MAKE) compose-up-app

compose-refresh-db: ## Stop app+db, start db, run migrate+codegen, then start app (rebuild image)
	$(MAKE) compose-stop-app
	$(MAKE) compose-stop-db
	$(MAKE) compose-up-db
	$(MAKE) migrate
	$(MAKE) codegen
	$(MAKE) compose-up-app


compose-ps: ## Show status of compose services
	$(DOCKER_COMPOSE) ps

compose-stop: ## Stop all compose services (keeps volumes)
	$(DOCKER_COMPOSE) stop

compose-down: ## Stop and remove compose services/volumes
	$(DOCKER_COMPOSE) down -v

codegen: ## Run jOOQ code generation in model/ (full, robust)
	# Ensure the jOOQ codegen strategy module is installed, then run codegen in model
	mvn -q -DskipTests -pl jooq-codegen -am install
	mvn -q -DskipTests -pl model -am generate-sources

codegen-fast: ## Run jOOQ code generation (lean) - assumes jooq-codegen is already installed
	mvn -q -DskipTests -pl model -am generate-sources

model-compile: ## Regenerate jOOQ and compile the model (ensures generated getters are available)
	mvn -q -DskipTests -pl model -am generate-sources compile

.PHONY: migrate
migrate: ## Run Liquibase migrations in model/ (uses DB_* from .env)
	mvn -q -Pliquibase -DskipTests -f model/pom.xml liquibase:update

seed: ## Seed teams using Dockerized Postgres (reads .env for DB creds)
	@if ! docker ps --format '{{.Names}}' | grep -q '^ligitabl-db$$'; then \
		echo "Postgres container 'ligitabl-db' not running. Start it with '$(DOCKER_COMPOSE) up -d db'"; \
		exit 1; \
	fi
	@if [ ! -f scripts/sql/seed-teams.sql ]; then \
		echo "Missing scripts/sql/seed-teams.sql"; \
		exit 1; \
	fi
	cat scripts/sql/seed-teams.sql | docker exec -i ligitabl-db psql -U $(DB_USER) -d $(DB_NAME)

prep-team: ## Recreate t_team table with desired schema (Docker)
	@if ! docker ps --format '{{.Names}}' | grep -q '^ligitabl-db$$'; then \
		echo "Postgres container 'ligitabl-db' not running. Start it with '$(DOCKER_COMPOSE) up -d db'"; \
		exit 1; \
	fi
	cat scripts/sql/create-team.sql | docker exec -i ligitabl-db psql -U $(DB_USER) -d $(DB_NAME)

seed-local: ## Seed teams using local psql client to localhost:$(DB_PORT)
	@if ! command -v psql >/dev/null 2>&1; then \
		echo "psql is not installed. Use 'make seed' (docker) or install psql."; \
		exit 1; \
	fi
	@if [ -z "$(DB_PASSWORD)" ]; then \
		echo "DB_PASSWORD not set. Add it to .env or export it."; \
		exit 1; \
	fi
	PGPASSWORD=$(DB_PASSWORD) psql -h localhost -p $(DB_PORT) -U $(DB_USER) -d $(DB_NAME) -f scripts/sql/seed-teams.sql

prep-team-local: ## Recreate t_team table with desired schema (local psql)
	@if ! command -v psql >/dev/null 2>&1; then \
		echo "psql is not installed."; \
		exit 1; \
	fi
	@if [ -z "$(DB_PASSWORD)" ]; then \
		echo "DB_PASSWORD not set. Add it to .env or export it."; \
		exit 1; \
	fi
	PGPASSWORD=$(DB_PASSWORD) psql -h localhost -p $(DB_PORT) -U $(DB_USER) -d $(DB_NAME) -f scripts/sql/create-team.sql

drop-db: ## Drop the database (Docker) using maintenance DB 'postgres'
	@if ! docker ps --format '{{.Names}}' | grep -q '^ligitabl-db$$'; then \
		echo "Postgres container 'ligitabl-db' not running. Start it with '$(DOCKER_COMPOSE) up -d db'"; \
		exit 1; \
	fi
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$(DB_NAME)';" || true
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $(DB_NAME) WITH (FORCE);"

reset-db: ## Drop and recreate the database (Docker)
	$(MAKE) drop-db
	docker exec -i ligitabl-db psql -U $(DB_USER) -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $(DB_NAME) OWNER $(DB_USER);"

db-bootstrap: ## Compose up DB, reset DB, migrate, codegen, then seed
	$(MAKE) compose-up-db
	$(MAKE) reset-db
	$(MAKE) migrate
	$(MAKE) codegen
	$(MAKE) seed

format: ## Format all Java sources (api, model) using Spotless (4-space indentation)
	mvn -q -pl api,model com.diffplug.spotless:spotless-maven-plugin:2.44.0:apply

format-check: ## Check formatting without changing files (fails if formatting needed)
	mvn -q -pl api,model com.diffplug.spotless:spotless-maven-plugin:2.44.0:check

format-all: format ## Alias: format api and model modules
