# Makefile for ligitabl (Spring Boot + Maven + Docker)

APP_NAME := ligitabl
API_DIR := api
VERSION ?= 0.1.0-SNAPSHOT
JAR := $(API_DIR)/target/$(APP_NAME)-$(VERSION).jar
IMAGE ?= $(APP_NAME):dev
PORT ?= 8080
DOCKER_COMPOSE ?= docker compose

# Load variables from .env if present
ifneq (,$(wildcard .env))
	include .env
	export
endif

.PHONY: help build test clean run run-no-db docker-build docker-run docker-stop compose-up compose-down codegen

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | sed 's/:.*## /\t- /' | sort

build: ## Build the project (skip tests)
	mvn -q -DskipTests -f $(API_DIR)/pom.xml clean package

test: ## Run tests
	mvn -f $(API_DIR)/pom.xml test

clean: ## Clean build artifacts
	mvn -f $(API_DIR)/pom.xml clean

run: $(JAR) ## Run the built JAR (DB required unless liquibase disabled)
	java -jar $(JAR)

run-no-db: $(JAR) ## Run without requiring DB (skips DataSource auto-config)
	java -jar $(JAR) --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

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

compose-down: ## Stop and remove compose services/volumes
	$(DOCKER_COMPOSE) down -v

codegen: ## Run jOOQ code generation (requires JOOQ_DB_URL, JOOQ_DB_USER, JOOQ_DB_PASSWORD)
	@if [ -z "$$JOOQ_DB_URL" ] || [ -z "$$JOOQ_DB_USER" ] || [ -z "$$JOOQ_DB_PASSWORD" ]; then \
		echo "Missing JOOQ DB env vars. Set JOOQ_DB_URL, JOOQ_DB_USER, JOOQ_DB_PASSWORD"; \
		exit 1; \
	fi
	mvn -q -DskipTests -f $(API_DIR)/pom.xml compile
	mvn -q -Pcodegen -DskipTests -f $(API_DIR)/pom.xml generate-sources
