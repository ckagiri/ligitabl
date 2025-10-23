# Makefile for ligitabl (Spring Boot + Maven + Docker)

APP_NAME := ligitabl
VERSION ?= 0.1.0-SNAPSHOT
JAR := target/$(APP_NAME)-$(VERSION).jar
IMAGE ?= $(APP_NAME):dev
PORT ?= 8080
DOCKER_COMPOSE ?= docker compose

.PHONY: help build test clean run run-no-db docker-build docker-run docker-stop compose-up compose-down codegen

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | sed 's/:.*## /\t- /' | sort

build: ## Build the project (skip tests)
	mvn -q -DskipTests clean package

test: ## Run tests
	mvn test

clean: ## Clean build artifacts
	mvn clean

run: $(JAR) ## Run the built JAR (DB required unless liquibase disabled)
	java -jar $(JAR)

run-no-db: $(JAR) ## Run without requiring DB (skips DataSource auto-config)
	java -jar $(JAR) --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

$(JAR):
	mvn -q -DskipTests package

docker-build: ## Build Docker image
	docker build -t $(IMAGE) .

docker-run: ## Run container on port $(PORT)
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) -e JAVA_OPTS="$(JAVA_OPTS)" $(IMAGE)

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
	mvn -q -Pcodegen -DskipTests generate-sources
