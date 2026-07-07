# Production Deployment: Problems, Fixes, and How It All Works

This document covers everything worked through to get the production deployment pipeline running, including how the CI/CD workflow works, the local SSH tunnel setup for database operations, and every problem we hit along the way with its root cause and fix.

---

## Table of Contents

1. [How deploy.yml works](#how-deployyml-works)
2. [Problem: Seed module ignoring DB_HOST](#problem-seed-module-ignoring-db_host)
3. [Problem: 100% CPU on the $7 droplet](#problem-100-cpu-on-the-7-droplet)
4. [Problem: Password authentication failures in CI](#problem-password-authentication-failures-in-ci)
5. [Problem: Missing schema at seed time](#problem-missing-schema-at-seed-time)
6. [Problem: Server becoming completely unresponsive during seeding](#problem-server-becoming-completely-unresponsive-during-seeding)
7. [Problem: Import failing silently — missing JWT_SECRET](#problem-import-failing-silently--missing-jwt_secret)
8. [Local SSH tunnel setup](#local-ssh-tunnel-setup)
9. [Problem: Makefile error "commands commence before first target"](#problem-makefile-error-commands-commence-before-first-target)
10. [Problem: SSH tunnel — Connection reset (no port binding)](#problem-ssh-tunnel--connection-reset-no-port-binding)
11. [Problem: SSL negotiation failure through the tunnel](#problem-ssl-negotiation-failure-through-the-tunnel)
12. [Problem: AuthController fails to start in batch mode](#problem-authcontroller-fails-to-start-in-batch-mode)
13. [Cleanup: Remove debug lines from deploy.yml](#cleanup-remove-debug-lines-from-deployyml)
14. [Problem: No space left on device during image pull](#problem-no-space-left-on-device-during-image-pull)
15. [Files changed summary](#files-changed-summary)

---

## How deploy.yml works

The workflow lives at `.github/workflows/deploy.yml` and has four jobs that run in sequence on push to `main` or `release`.

### Job 1: `test` — runs on every push and PR

Starts a Postgres service container (`ligitabl_ci` database), then:

1. Installs the root POM and `jooq-codegen` module to the local Maven repo. jOOQ codegen is a _plugin dependency_ of the `model` module, not a regular dependency, so Maven resolves it from the local repo rather than the reactor. It must be installed first or Maven cannot read its artifact descriptor.
2. Runs Liquibase migrations against the service container to build the schema. jOOQ codegen needs an actual schema to generate type-safe classes from, so this must happen before the next step.
3. Runs `mvn test` with `-Pwith-jooq` to trigger codegen in the `generate-sources` phase. API integration tests launch their own Testcontainers DB independently.
4. Uploads the generated jOOQ sources as an artifact (needed by the build job, since Docker build has no DB access).

### Job 2: `build-and-push` — runs on push to main/release

Downloads the jOOQ artifact from job 1, then builds and pushes two Docker tags to Docker Hub:

- `username/ligitabl:api-latest` — always the most recent main build
- `username/ligitabl:api-<sha>` — immutable tag for this exact commit

The Dockerfile copies the pre-generated jOOQ sources from `model/target/generated-sources/jooq` (placed there by the artifact download step) so the build does not need a live database.

### Job 3: `deploy` — runs on push to main/release

1. Constructs `.env.prod` from GitHub Secrets and SCPs it plus `docker-compose.prod.yml` to the server at `~/.deploy/ligitabl/`.
2. SSHes to the server and:
   - Logs into Docker Hub
   - Pulls the new image
   - Runs `docker compose down` then `docker compose up -d`
   - Liquibase migrations run automatically on app startup (`SPRING_PROFILES_ACTIVE=liquibase`)
   - Checks the DB for existing season data and warns if the first-time setup hasn't been run yet

### Job 4: `setup` — triggered manually via workflow_dispatch

Run once after the first successful deploy to seed the reference data and import Premier League fixtures. Trigger via: Actions → Run workflow → check "Run first-time setup".

The job:

1. Builds the seed JAR in CI (needs jOOQ codegen, which needs a schema — hence the postgres service in this job too).
2. SCPs the seed JAR to the server.
3. SSHes to the server and:
   - Reads DB credentials from `.env.prod` on the server using `grep`/`cut` (avoids passing them through SSH env forwarding, which corrupts passwords with special characters — see [Problem: Password authentication failures in CI](#problem-password-authentication-failures-in-ci)).
   - Verifies the credentials work by running a `psql SELECT 1` through the Docker network.
   - Builds `.env.seed` by renaming keys from `.env.prod` using `sed` — byte-for-byte copy, no shell expansion.
   - Runs the seed JAR in a Docker container on the `ligitabl-network` with memory limits.
   - Runs the API container in batch mode to import Premier League matches from football-data.org.

The `APP_ARGS` environment variable is read by the Dockerfile's entrypoint (`sh -c "exec java $JAVA_OPTS -jar app.jar $APP_ARGS"`), which is how `--spring.main.web-application-type=none --workflow.run=true` etc. get passed to the import run.

---

## Problem: Seed module ignoring DB_HOST

**Symptom:** The seed container failed with `Connection refused` to `localhost:5432` even though `DB_HOST=db` was passed.

**Root cause:** The seed's `application.yml` had the datasource URL hardcoded as a literal default:

```yaml
url: ${DB_URL:jdbc:postgresql://localhost:55432/${DB_NAME:ligitabl}}
```

There was no `${DB_HOST}` placeholder — the `localhost` was baked into the default string. Setting `DB_HOST=db` in the environment had no effect.

The API module's `application.yml` used the correct pattern:

```yaml
url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:55432}/${DB_NAME:ligitabl}}
```

**Fix:** Updated `seed/src/main/resources/application.yml` to match the API pattern:

```yaml
url: ${DB_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:55432}/${DB_NAME:ligitabl}}
```

Now `DB_HOST=db` is honoured when running in Docker, and `localhost` remains the default for local development.

---

## Problem: 100% CPU on the $7 droplet

**Symptom:** The server ran at near 100% CPU continuously, making SSH slow and the app sluggish.

**Root cause:** The JVM was configured with G1GC (the default for Java 9+). G1GC uses multiple background threads for concurrent garbage collection. On a single-vCPU machine, these threads compete with the application threads for the one CPU, causing constant context-switching and CPU starvation. G1GC also does not respect `ActiveProcessorCount` unless told to.

**Fix:** In `docker-compose.prod.yml`, switched to SerialGC and capped the process count:

```yaml
JAVA_OPTS: >-
  -Xmx384m
  -Xms128m
  -XX:+UseSerialGC
  -XX:ActiveProcessorCount=1
  -Djava.security.egd=file:/dev/./urandom
```

SerialGC uses a single thread for garbage collection with no background threads, which is optimal for single-vCPU environments. The `-Djava.security.egd` flag speeds up JVM startup by using `/dev/urandom` instead of the blocking `/dev/random`.

---

## Problem: Password authentication failures in CI

This went through several iterations.

### Iteration 1: `localhost:5432 connection refused`

The seed container was trying to connect to `localhost:5432`, which inside a Docker container refers to the container's own loopback, not the host or any other container. Fix: pass `-e DB_HOST=db` so it connects to the `db` service on the Docker network.

### Iteration 2: `password authentication failed for user "***"` (masked username — wrong user)

The `appleboy/ssh-action`'s `envs:` forwarding was corrupting the `DB_PASSWORD` value. Special characters (`$`, `!`, `@`, etc.) in passwords get interpreted by the shell during the SSH handshake, altering the value before it reaches the remote script.

**Fix:** Instead of forwarding the password as an SSH env var, read it directly on the server from `.env.prod` (which was already SCPed there by the deploy job):

```bash
DB_PASSWORD=$(grep '^POSTGRES_PASSWORD=' .env.prod | cut -d= -f2-)
```

`cut -d= -f2-` is important — it captures everything after the first `=`, including any `=` characters that might appear in base64-encoded secrets.

### Iteration 3: Still failing even after reading from file

The `docker run -e DB_PASSWORD="$DB_PASSWORD"` still expanded the variable through the shell, re-introducing corruption risk for passwords with shell-special characters.

**Fix:** Build a temporary env file using `sed` to rename keys, then use `docker run --env-file`. The `--env-file` flag reads values byte-for-byte with no shell interpretation:

```bash
printf 'DB_HOST=db\nDB_PORT=5432\nSEED_RUN_LIQUIBASE=true\n' > .env.seed
grep '^POSTGRES_DB='       .env.prod | sed 's/^POSTGRES_DB=/DB_NAME=/'           >> .env.seed
grep '^POSTGRES_USER='     .env.prod | sed 's/^POSTGRES_USER=/DB_USER=/'         >> .env.seed
grep '^POSTGRES_PASSWORD=' .env.prod | sed 's/^POSTGRES_PASSWORD=/DB_PASSWORD=/' >> .env.seed
```

### Iteration 4: psql verification passing but seed still failing

The verification step used `docker exec ligitabl-db-prod psql -h 127.0.0.1 ...`. This connects from _within_ the DB container back to its own loopback, which may use a different `pg_hba.conf` rule (trust auth on loopback). The seed container connects via the Docker network, which hits a different rule requiring actual password authentication.

**Fix:** Changed the verification to match the seed's actual network path:

```bash
docker run --rm --network ligitabl-network \
  -e PGPASSWORD="$DB_PASSWORD" \
  postgres:16 \
  psql -h db -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1;"
```

---

## Problem: Missing schema at seed time

**Symptom:** `relation "public.t_user" does not exist` — the seed started but could not find any tables.

**Root cause:** Liquibase migrations had never been run against the production database. The app runs them on startup with `SPRING_PROFILES_ACTIVE=liquibase`, but the setup job ran the seed _before_ or independently of the app startup.

**Fix:** The seed module's `application.yml` already has a Liquibase integration controlled by:

```yaml
spring:
  liquibase:
    enabled: ${SEED_RUN_LIQUIBASE:false}
```

Added `SEED_RUN_LIQUIBASE=true` to the `.env.seed` file so the seed runs migrations before seeding data:

```bash
printf 'DB_HOST=db\nDB_PORT=5432\nSEED_RUN_LIQUIBASE=true\n' > .env.seed
```

---

## Problem: Server becoming completely unresponsive during seeding

**Symptom:** SSH connections timed out completely during the seeding step. The server was inaccessible for several minutes.

**Root cause:** The seed container had no memory limit. The 1 GB droplet was already running the API (`-Xmx384m`), Postgres, and nginx-proxy. Adding an unbounded JVM process caused total memory to exceed available RAM. Linux then started heavy swap I/O, which starved the SSH daemon of CPU and I/O and made the machine appear dead.

**Fix:** Added Docker memory limits and tuned the seed JVM:

```bash
docker run --rm \
  --network ligitabl-network \
  --memory=256m --memory-swap=256m \
  ...
  java -Xmx192m -Xms64m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 \
    -Dseed.main=seeding/main.yaml -jar /app/seed.jar
```

`--memory-swap=256m` (equal to `--memory`) disables swap for the container, causing it to be OOM-killed rather than drag the whole server into swap death.

---

## Problem: Import failing silently — missing JWT_SECRET

**Symptom:** The import step (step 2/2) produced no output and exited without running.

**Root cause:** The API's Spring Security configuration auto-wires `JwtTokenGenerator` on startup even when running with `--spring.main.web-application-type=none`. The `JwtTokenGenerator` constructor calls `Keys.hmacShaKeyFor(secret.getBytes())`, which requires at least 256 bits (32 bytes). The import container was not given a `JWT_SECRET`, so Spring used an empty/default value that failed the JJWT minimum-length validation, aborting the context.

**Fix:** Added `JWT_SECRET` to the import env file (read from `.env.prod` on the server, same `grep` pattern):

```bash
grep '^JWT_SECRET=' .env.prod >> .env.import
```

---

## Local SSH tunnel setup

**Motivation:** The $7 droplet (1 vCPU, 1 GB RAM) could not run the seed or import-pl jobs without causing swap saturation and SSH blackouts. Running these operations from a local development machine through an SSH tunnel uses the local machine's resources and avoids the constraints entirely.

### Makefile targets added

Three new targets in the `PRODUCTION REMOTE OPERATIONS` section of the Makefile:

**`make prod-tunnel ENV=prod PROD_CONFIRMED=yes`** (Terminal 1, keep open):

SSHes to the server, uses `docker inspect` to find the postgres container's IP on the Docker bridge network, then establishes a port-forward from `localhost:5434` to that container IP:5432. Using the container's bridge IP directly means no host-level port binding is required on the server.

```makefile
DB_IP=$(ssh ... "docker inspect ligitabl-db-prod \
  --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'")
ssh -L 5434:$DB_IP:5432 deployer@server -N
```

**`make prod-seed ENV=prod PROD_CONFIRMED=yes`** (Terminal 2):

Builds the seed JAR locally and runs it with the tunnel as the DB host. Sets `DB_URL` explicitly with `sslmode=disable` (see [SSL problem](#problem-ssl-negotiation-failure-through-the-tunnel)):

```makefile
DB_URL="jdbc:postgresql://localhost:5434/$(DB_NAME)?sslmode=disable" \
  java -Dseed.main=seeding/main.yaml -jar $(SEED_JAR)
```

**`make prod-import-pl ENV=prod PROD_CONFIRMED=yes`** (Terminal 2):

Builds the API JAR locally and runs it in workflow mode with the tunnel as the DB host. Sets `SPRING_DATASOURCE_URL` explicitly with `sslmode=disable`.

### Required `.env.prod` entries (local, never committed)

```bash
PROD_HOST=<droplet-ip>
PROD_SSH_KEY=~/ssh-keys/digital-ocean/id_rsa
PROD_SSH_USER=deployer
PROD_TUNNEL_PORT=5434

DB_NAME=ligitabl
DB_USER=ligitabl
DB_PASSWORD=<production password — from GitHub Secrets>

JWT_SECRET=<production JWT secret — from GitHub Secrets>
API_FOOTBALL_DATA_KEY=<football-data.org token>
```

---

## Problem: Makefile error "commands commence before first target"

**Symptom:** `make prod-tunnel ENV=prod PROD_CONFIRMED=yes` immediately failed with:

```
Makefile:49: *** commands commence before first target. Stop.
```

**Root cause:** The `$(error ...)` directive at line 49 (which fires when `.env.prod` does not exist) had a leading TAB character. At file scope (not inside a recipe), Make interprets a leading TAB as the start of a recipe. Since no target had been defined yet, Make reported this as "commands commence before first target".

GNU Make requires that file-scope directives (`$(error)`, `$(warning)`, `include`, etc.) must NOT have a leading TAB. A leading TAB is only valid inside a recipe block.

**Before (broken):**

```makefile
ifeq (,$(wildcard $(ENV_FILE)))
	$(error ❌ $(ENV_FILE) not found!\
	\nCreate it with: cp env.$(ENV).template $(ENV_FILE)\
	\nThen edit with your settings.)
endif
```

**After (fixed):**

```makefile
ifeq (,$(wildcard $(ENV_FILE)))
$(error ❌ $(ENV_FILE) not found! Create it with: cp env.$(ENV).template $(ENV_FILE) then edit with your settings.)
endif
```

---

## Problem: SSH tunnel — Connection reset (no port binding)

**Symptom:** `java.net.SocketException: Connection reset` immediately on connection. The stack trace pointed to `ConnectionFactoryImpl.enableSSL` or `doAuthentication` (after the SSL fix was applied).

**Root cause:** The original tunnel command was:

```bash
ssh -L 5434:127.0.0.1:5432 deployer@server -N
```

This forwards to `127.0.0.1:5432` _on the server's host_. The `docker-compose.prod.yml` at that point had no `ports:` binding for the DB service — the postgres container was only reachable inside the `ligitabl-network` Docker network, not on the host's loopback. The SSH server tried to connect to `127.0.0.1:5432`, got "Connection refused", and the SSH client surfaced this to the JDBC driver as a "Connection reset".

**Fix:** Instead of relying on a host port binding, use the container's internal Docker bridge IP directly. The SSH server on the host can route to Docker bridge IPs without any port binding:

```makefile
DB_IP=$(ssh ... "docker inspect ligitabl-db-prod \
  --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'")
ssh -L 5434:$DB_IP:5432 deployer@server -N
```

This approach is actually better than the host port binding approach: it requires no docker-compose changes and does not expose the DB on the host at all.

Note: `docker-compose.prod.yml` also has `ports: ["127.0.0.1:5432:5432"]` committed now. This is useful for GUI database tools that connect via a simpler `ssh -L` without needing the dynamic container IP lookup, but it is not required for the Makefile prod-tunnel target.

---

## Problem: SSL negotiation failure through the tunnel

**Symptom:** After fixing the tunnel to use the container's bridge IP, connection still failed with `Connection reset` at `ConnectionFactoryImpl.enableSSL`.

**Root cause:** The PostgreSQL JDBC driver defaults to attempting SSL on every connection (`sslmode=prefer`). It sends an SSL request packet to the server. The Docker postgres container has no SSL configured, so it rejects the SSL request by closing the connection — appearing as a reset.

This does not happen when the seed/import run in Docker containers on the server (container-to-container on a Docker bridge network does not trigger the same SSL negotiation path), which is why this only appeared in the tunnel scenario.

**Fix:** Explicitly set `sslmode=disable` in the JDBC URL for the tunnel-based Makefile targets. Since the traffic is already encrypted end-to-end by the SSH tunnel, disabling JDBC-level SSL is safe.

For `prod-seed` (seed module uses `DB_URL`):

```makefile
DB_URL="jdbc:postgresql://localhost:$(PROD_TUNNEL_PORT)/$(DB_NAME)?sslmode=disable"
```

For `prod-import-pl` (API module uses `SPRING_DATASOURCE_URL`):

```makefile
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$(PROD_TUNNEL_PORT)/$(DB_NAME)?sslmode=disable"
```

---

## Problem: AuthController fails to start in batch mode

**Symptom:** `prod-import-pl` failed with:

```
Error creating bean with name 'authController': Unsatisfied dependency expressed
through constructor parameter 6: No qualifying bean of type
'org.springframework.security.web.authentication.RememberMeServices' available
```

**Root cause:** `AuthController` is a `@Controller` with `RememberMeServices` as a constructor dependency (used for the "remember me" cookie on login). `RememberMeServices` is a servlet-layer security bean — it only exists when Spring's web security auto-configuration runs. When the import workflow sets `--spring.main.web-application-type=none`, Spring does not start the servlet context and does not create any servlet-security beans including `RememberMeServices`. Spring still tries to instantiate `AuthController` and fails.

**Fix:** Added `@ConditionalOnWebApplication` to `AuthController`:

```java
@ConditionalOnWebApplication
@Controller
@RequestMapping("/auth")
public class AuthController {
```

This tells Spring to only register `AuthController` as a bean when the application is running as a servlet web application. In non-web (`none`) or reactive modes, the bean is skipped entirely. This is the correct semantic — the auth controller has no business existing in a batch context.

---

## Cleanup: Remove debug lines from deploy.yml

During debugging of the password auth failures, two debug lines were added to the setup job's SSH script that printed sensitive values to the GitHub Actions log:

```bash
# REMOVED:
echo "Shell sees: DB_NAME='$DB_NAME' DB_USER='$DB_USER' DB_PASSWORD='$DB_PASSWORD'"

# REMOVED:
echo "DEBUG .env.seed:"
cat .env.seed
```

Both were removed once the credential flow was confirmed working.

---

## Problem: No space left on device during image pull

**Symptom:** The `deploy` job failed mid-pull with:

```
write /var/lib/docker/tmp/GetImageBlob2653441039: no space left on device
Error: Process completed with exit code 1
```

**Root cause:** Every deploy builds and pushes an immutable tag, `api-<github.sha>`, alongside `api-latest`. Each deploy on the server pulls a brand new sha-tagged image, but the previous deploy's sha-tagged image is never removed — it's still tagged (referenced by name), just no longer used by a running container. The cleanup step ran `docker image prune -f`, which only removes **dangling** images (untagged layers left over from a build), not old tagged images. So every deploy left one more full image on disk, and on the small droplet this eventually exhausted the disk before the *next* pull could even land its blobs.

**Fix:** In `.github/workflows/deploy.yml`'s `deploy` job:

1. Added a prune step **before** `docker compose pull`, so stale images are cleared and there's headroom for the incoming layers:
   ```bash
   docker image prune -af
   ```
2. Changed the post-deploy cleanup from `docker image prune -f` to `docker image prune -af` as well, so it actually removes old tagged images now that the containers referencing them have been recreated, not just dangling ones.

The `-a` flag is the key change — it prunes all images not used by an existing container, regardless of whether they're tagged, which is what actually reclaims space from the sha-tag accumulation.

---

## Files changed summary

| File                                                              | What changed                                                                                                                                                |
| ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `seed/src/main/resources/application.yml`                         | Changed datasource URL to use `${DB_HOST:localhost}` placeholder instead of hardcoded `localhost`                                                           |
| `docker-compose.prod.yml`                                         | Switched to `SerialGC`, reduced heap sizes, added `127.0.0.1:5432:5432` port binding for SSH tunnelling                                                     |
| `.github/workflows/deploy.yml`                                    | Added `sed`-based env file building, network-path credential verification, memory limits on seed container, `JWT_SECRET` in import env, removed debug lines |
| `Makefile`                                                        | Fixed `$(error)` TAB at file scope; added `prod-tunnel`, `prod-seed`, `prod-import-pl` targets; added `sslmode=disable` to tunnel JDBC URLs                 |
| `api/src/main/java/com/ligitabl/api/web/auth/AuthController.java` | Added `@ConditionalOnWebApplication` so the controller is skipped when running in batch/workflow mode                                                       |
| `.env.prod` (local, gitignored)                                   | Added `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `API_FOOTBALL_DATA_KEY` for local tunnel operations                                                |
| `.github/workflows/deploy.yml`                                    | Added `docker image prune -af` before pulling, changed post-deploy prune from `-f` to `-af` to actually remove old sha-tagged images, not just dangling ones |
