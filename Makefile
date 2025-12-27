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

.PHONY: help build api-build model-compile test clean run run-no-db run-app bootstrap-run docker-build docker-run docker-stop compose-up compose-up-db compose-stop-db compose-up-app compose-up-app-fast compose-logs-app compose-stop-app compose-restart-app compose-refresh compose-refresh-gen compose-refresh-db compose-ps compose-stop compose-down codegen codegen-fast migrate drop-db reset-db format format-check format-all test-unit test-api-no-jooq test-api-fast test-api-core test-auth-smoke test-all test-model test-model-fast test-api-it test-api-all test-dev model-codegen-local seed-competition-cli db-seed db-seed-demo db-seed-season dev-reset test-api-rebuild db-seed-all db-seed-users run-api run-api-test test-seeding-auth

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | sed 's/:.*## /\t- /' | sort

build: ## Build the project (skip tests) - builds api and required modules (model, jooq-codegen)
	mvn -q -DskipTests -pl $(API_DIR) -am clean package

api-build: ## Build the API module (skip tests) - includes dependencies (model, jooq-codegen)
	# Clean only the API module to avoid packaging stale IDE-compiled classes,
	# but do not clean dependencies (model) since that would wipe generated jOOQ sources.
	mvn -q -DskipTests -pl $(API_DIR) clean
	mvn -q -DskipTests -pl $(API_DIR) -am package

test: ## Run full API test suite (build deps too; may include *IT depending on config)
	mvn -pl $(API_DIR) -am test

test-unit: ## Run pure unit tests (no Spring) in API module (no-jooq profile to avoid DB/codegen)
	mvn -q -P unit-tests,no-jooq -pl $(API_DIR) -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='**/*GuardTest' test

test-webmvc: ## Run MVC slice tests (@WebMvcTest) in API module (no-jooq profile to avoid DB/codegen)
	mvn -q -P unit-tests,webmvc-tests,no-jooq -pl $(API_DIR) -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='**/*WebMvcTest' test

test-api-no-jooq: ## Run API tests with no DB/codegen (skips jOOQ infra)
	# Ensure latest model jar is installed without jOOQ/codegen or tests
	mvn -q -pl model -P no-jooq -Dmaven.test.skip=true -DskipTests=true install
	# Run API tests against that model using the no-jooq profile (do not rebuild/test model)
	mvn -q -P no-jooq -pl $(API_DIR) -DskipITs test

test-api-fast: ## Run all API unit tests quickly (pre-install model w/o tests; skip jOOQ codegen)
	# 1) Install model to local repo without compiling or running its tests
	mvn -q -pl model -Dmaven.test.skip=true -DskipITs=true install
	# 2) Run API tests with no-jooq profile (no DB/codegen)
	mvn -q -pl $(API_DIR) -P no-jooq -DskipITs test

test-api-core: ## Run core API tests (build deps, skip *IT via -DskipITs)
	mvn -q -pl $(API_DIR) -am -DskipITs test

test-api-it: ## Run DB-backed API integration tests (*IT via Testcontainers + Liquibase)
	mvn -q -pl $(API_DIR) -am -DskipITs=false -Dtest='**/*IT' -Dsurefire.failIfNoSpecifiedTests=false test

test-api-all: ## Run typical API flow: fast no-jOOQ tests + DB-backed *IT tests
	$(MAKE) test-api-no-jooq
	$(MAKE) test-api-it

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

run-api: ## Start DB (compose) and run the API via spring-boot:run
	$(MAKE) compose-up-db
	mvn -q -pl $(API_DIR) -am spring-boot:run

run-api-test: ## Start DB (compose) and run the API using .env.test (DB=ligitabl_test, DB_PORT=55433, PORT=8081)
	@set -a; \
	  if [ -f .env.test ]; then . ./.env.test; fi; \
	  set +a; \
	  $(MAKE) compose-up-db; \
	  mvn -q -pl $(API_DIR) -am spring-boot:run

bootstrap-run: ## Reset DB, migrate, codegen, seed reference data, then run the app
	$(MAKE) dev-reset
	$(MAKE) run

bootstrap-all-run: ## Reset DB, migrate, codegen, seed reference data, then run the app
	$(MAKE) dev-reset-all
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

compose-up-db-attached: ## Start only postgres via Docker Compose (attached, show logs)
	DB_PORT=$(HOST_DB_PORT) $(DOCKER_COMPOSE) up db

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
	mvn -q -DskipTests -Pwith-jooq -pl model -am \
		-DDB_HOST=$(DB_HOST) -DDB_PORT=$(DB_PORT) -DDB_NAME=$(DB_NAME) \
		-DDB_USER=$(DB_USER) -DDB_PASSWORD=$(DB_PASSWORD) \
		generate-sources

codegen-fast: ## Run jOOQ code generation (lean) - assumes jooq-codegen is already installed
	mvn -q -DskipTests -Pwith-jooq -pl model -am \
		-DDB_HOST=$(DB_HOST) -DDB_PORT=$(DB_PORT) -DDB_NAME=$(DB_NAME) \
		-DDB_USER=$(DB_USER) -DDB_PASSWORD=$(DB_PASSWORD) \
		generate-sources

model-compile: ## Regenerate jOOQ and compile the model (ensures generated getters are available)
	mvn -q -DskipTests -Pwith-jooq -pl model -am generate-sources compile

model-codegen-local: ## Start DB (compose), run Liquibase migrations, then jOOQ codegen for model
	$(MAKE) compose-up-db
	$(MAKE) migrate
	$(MAKE) codegen

test-model: ## Run model integration tests (starts DB, migrates, codegen, then tests)
	$(MAKE) compose-up-db
	$(MAKE) migrate
	$(MAKE) codegen
	mvn -pl model -am test

test-model-fast: ## Run model tests assuming jOOQ codegen has already been run
	mvn -q -pl model -am test

test-dev: ## Run typical developer tests: model (fast) + core API tests
	$(MAKE) test-model-fast
	$(MAKE) test-api-core

test-api-rebuild: ## Clean API+deps, regenerate jOOQ, rebuild model, then run core API tests
	mvn clean -pl $(API_DIR) -am
	$(MAKE) codegen
	mvn -pl model -am test
	mvn -pl $(API_DIR) -am -DskipITs test

.PHONY: migrate
migrate: ## Run Liquibase migrations in model/ (uses DB_* from .env)
	mvn -q -Pliquibase -DskipTests -f model/pom.xml \
		-DDB_HOST=$(DB_HOST) -DDB_PORT=$(DB_PORT) -DDB_NAME=$(DB_NAME) \
		-DDB_USER=$(DB_USER) -DDB_PASSWORD=$(DB_PASSWORD) \
		liquibase:update

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

test-seeding-auth: ## Run seeding+auth smoke tests (uses .env.test: DB_PORT=55433, PORT=8081; starts API; DB reset is destructive)
	START_API=1 ./scripts/TestAuthAndSeeding.sh

dev-reset: ## For local dev: reset DB, run migrations, codegen, then seed reference data
	$(MAKE) compose-up-db
	$(MAKE) reset-db
	$(MAKE) migrate
	$(MAKE) codegen
	$(MAKE) db-seed

dev-reset-all: ## Reset DB, run migrations, codegen, then seed reference and demo data
	$(MAKE) compose-up-db
	$(MAKE) reset-db
	$(MAKE) migrate
	$(MAKE) codegen
	$(MAKE) db-seed-all

db-seed: ## Seed reference data (competition, season, round) using the dedicated seed module against the dev DB
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar --spring.profiles.active=default

db-seed-demo: ## Seed demo league (teams, competition, season, round, matches) using the seed module
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/demo-main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar --spring.profiles.active=default
	$(MAKE) db-seed-season SEEDING_CONFIG=seeding-config-demo.yaml

SEEDING_CONFIG ?= seeding-config.yaml

db-seed-season: ## Seed season demo extras (predictions, swaps, round finalization) via SeedSeasonCommandLineRunner
	$(MAKE) compose-up-db
	$(MAKE) api-build
	java -jar $(JAR) --spring.main.web-application-type=none --seed-season --seeding.config=$(SEEDING_CONFIG)

seed-season-cli: db-seed-season ## Alias: run SeedSeasonCommandLineRunner (override with SEEDING_CONFIG=...)

db-seed-users: ## Seed users (admin/player/superuser) needed for scripts/TestAuth.sh using the seed module
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar --spring.profiles.active=default

test-auth-smoke: ## Reset DB, migrate, seed users, start API (compose), run scripts/TestAuth.sh
	@bash -lc 'set -euo pipefail; \
		echo "[smoke] Starting DB..."; \
		$(MAKE) compose-up-db; \
		echo "[smoke] Resetting DB..."; \
		$(MAKE) reset-db; \
		echo "[smoke] Running migrations..."; \
		$(MAKE) migrate; \
		echo "[smoke] Seeding users..."; \
		$(MAKE) db-seed-users; \
		trap "echo \"[smoke] Stopping app...\"; $(MAKE) compose-stop-app >/dev/null 2>&1 || true" EXIT; \
		echo "[smoke] Building + starting API (compose)..."; \
		$(MAKE) compose-up-app; \
		echo "[smoke] Waiting for API to be reachable..."; \
		for i in $$(seq 1 60); do \
			if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then \
				echo "[smoke] API is up."; \
				break; \
			fi; \
			sleep 1; \
			done; \
		echo "[smoke] Running scripts/TestAuth.sh..."; \
		BASE_URL=http://localhost:8080 ./scripts/TestAuth.sh'

db-seed-all: ## Seed both reference and demo data using the seed module
	$(MAKE) compose-up-db
	mvn -q -pl seed -am -DskipTests package
	java -Dseed.main=seeding/main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar --spring.profiles.active=default
	java -Dseed.main=seeding/demo-main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar --spring.profiles.active=default

seed-competition-cli: ## Seed competitions using the Spring Boot CLI against the dev DB
	$(MAKE) compose-up-db
	mvn -q -pl $(API_DIR) -am -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
	  -Dexec.mainClass=com.ligitabl.api.seed.CompetitionSeedCli \
	  -Dexec.args=".art/seeding/competition.yaml"
import-pl: ## Import Premier League matches into the dev DB using the workflow runner
	$(MAKE) compose-up-db
	$(MAKE) api-build
	FOOTBALL_DATA_API_KEY=$(API_FOOTBALL_DATA_KEY) java -jar $(JAR) \
	  --spring.profiles.active=workflow \
	  --workflow.run=true \
	  --workflow.competition=PL \
	  --workflow.exit-after=true

format: ## Format all Java sources (api, model) using Spotless (4-space indentation)
	mvn -q -pl api,model com.diffplug.spotless:spotless-maven-plugin:2.44.0:apply

format-check: ## Check formatting without changing files (fails if formatting needed)
	mvn -q -pl api,model com.diffplug.spotless:spotless-maven-plugin:2.44.0:check

format-all: format ## Alias: format api and model modules
