# Debugging Test Failures (Maven Surefire + Integration Tests)

This repo uses Maven Surefire for unit tests and (depending on the Make target) runs DB-backed integration tests via Testcontainers + Liquibase.

See [backend-dev.md](./backend-dev.md) for the repo’s intended workflows (commands, Make targets, and test taxonomy).

This guide is a practical checklist for diagnosing failures quickly using the same toolbox we use while iterating: `mvn`, `make`, `ls`, `tail`, `rg`, small `python` snippets for report parsing, and log capture.

Note: VS Code tasks are provided for common test flows (including “logged to repo” variants). Prefer those when you want repeatable logs under `api/target/` or `scripts/target/`.

## Quick triage checklist

1. **Run the smallest test suite that reproduces the failure**
   - API unit/core tests (skips `*IT`):

     ```bash
     mvn -pl api -am -DskipITs test
     ```

   - Or use Make wrappers described in [backend-dev.md](./backend-dev.md) (recommended when available):

     ```bash
     make test-api-core
     make test-api-it
     ```

   - API integration tests (DB-backed `*IT`, direct Maven invocation):

     ```bash
     mvn -pl api -am -DskipITs=false -Dtest='**/*IT' -Dsurefire.failIfNoSpecifiedTests=false test
     ```

2. **Identify which tests failed** (don’t scroll the whole Maven output)
   - Surefire writes machine-readable XML and human-friendly text reports under:
     - `api/target/surefire-reports/`
     - `model/target/surefire-reports/`
     - `seed/target/surefire-reports/`

3. **Re-run one failing test in isolation** (fast feedback)

## Where Surefire puts the useful artifacts

When Surefire runs, the best “what happened?” information is usually already in the repo:

- `*/target/surefire-reports/*.txt`
  - Plain-text per-test output, stack traces, and assertion diffs.
- `*/target/surefire-reports/TEST-*.xml`
  - JUnit XML with failures/errors counts and failure messages.
- `*/target/surefire-reports/*.dump` / `*.dumpstream` (sometimes)
  - Produced when the forked JVM crashes/hangs.

Common quick commands:

```bash
ls -la api/target/surefire-reports | head
rg -n "FAILURE|ERROR|Caused by" api/target/surefire-reports
rg -n "Tests run:|Failures:|Errors:|Skipped:" api/target/surefire-reports
```

## Re-running a single test (or method)

### Maven `-Dtest=`

Run one test class:

```bash
mvn -pl api -am -DskipITs -Dtest=AuthControllerIntegrationTest test
```

Run one test method:

```bash
mvn -pl api -am -DskipITs -Dtest=AuthControllerIntegrationTest#register_conflict test
```

Notes:

- Use `-DskipITs` when you don’t need DB-backed `*IT` tests.
- If you don’t see output you expect, add `-DtrimStackTrace=false` and drop `-q`.

### Maven `-DfailIfNoTests=false`

Helpful when you are dialing in `-Dtest=...` patterns and don’t want Maven to fail on “no tests matched”:

```bash
mvn -pl api -am -DskipITs -DfailIfNoTests=false -Dtest=SomePattern test
```

## Getting more useful output from Maven/Surefire

When a failure is opaque, re-run with more diagnostics:

- Maven stack traces:

  ```bash
  mvn -pl api -am -DskipITs test -e
  ```

- Maven debug logging (very verbose; last resort):

  ```bash
  mvn -pl api -am -DskipITs test -X
  ```

- Keep full stack traces:

  ```bash
  mvn -pl api -am -DskipITs -DtrimStackTrace=false test
  ```

## Capturing logs reliably (tee + exit code)

Sometimes you want a durable log you can search, share, or diff.

A robust pattern (keeps the real Maven exit code even when piped through `tee`):

```bash
mkdir -p api/target
rm -f api/target/test-api.log api/target/test-api.exit

mvn -q -pl api -am -DskipITs test 2>&1 | tee api/target/test-api.log
code=${PIPESTATUS[0]}
echo $code > api/target/test-api.exit

tail -n 80 api/target/test-api.log
exit $code
```

Tips:

- `tail -n 80 ...` gives you “the end of the story” fast.
- The `.exit` file is a simple breadcrumb for later automation.

## Parsing Surefire XML with a quick Python snippet

If Maven output is too noisy, query the XML directly to list failing suites.

Example (API module):

```bash
python3 - <<'PY'
import glob
import xml.etree.ElementTree as ET

fails=[]
for path in glob.glob('api/target/surefire-reports/TEST-*.xml'):
    root=ET.parse(path).getroot()
    f=int(root.attrib.get('failures','0'))
    e=int(root.attrib.get('errors','0'))
    if f or e:
        fails.append((path,f,e))

print('nonzero:', len(fails))
for p,f,e in sorted(fails):
    print(p, 'failures', f, 'errors', e)
PY
```

From there, open the corresponding `.txt` next to the failing `TEST-*.xml` to see the stack trace and assertion details.

## Using `rg` effectively on test failures

Once you have the failing test name or error string:

- Find where it’s asserted:

  ```bash
  rg -n "register_conflict" api/src/test/java
  ```

- Find the exception type or error code in production code:

  ```bash
  rg -n "UseCaseException|AuthErrorHandler|RequestValidator" api/src/main/java
  ```

- Search logs/reports quickly:

  ```bash
  rg -n "(AssertionError|Caused by|ERROR|FAILURE)" api/target/surefire-reports
  ```

## Recent learnings (from debugging this repo)

- **Spring bean name collisions are easy to miss.**
  - When a web controller and an API controller share the same simple class name, Spring’s default bean name will collide.
  - Fix by explicitly naming one (e.g., `@Controller("webCreatePredictionController")`) or renaming the class/package.

- **`@ControllerAdvice` in WebMvc tests can break context load.**
  - If a controller advice depends on repository beans, `@WebMvcTest` won’t provide them by default.
  - Use `@ConditionalOnBean(...)` to only register advice when those repos exist, or exclude the advice in MVC slice tests.

- **Testcontainers + Spring context caching can lead to “connection refused.”**
  - If a test class uses its own `@Container` DB and the context is cached/reused, the cached context may point at a stopped container.
  - Fix options:
    - Mark the test class with `@DirtiesContext` (e.g., `AFTER_CLASS`) so the context isn’t reused.
    - Use a shared container (static + manual start) for the whole test suite.

- **Look for DB connection errors in the _end_ of the log.**
  - Hikari errors like “Pool is empty” or “Connection refused” often appear at the tail, not near the top.
  - `tail -n 80 api/target/test-api.log` is usually enough to see the root cause.

## Integration tests (Testcontainers) diagnostics

DB-backed `*IT` tests may fail for reasons unrelated to application code (Docker, network, ports, migrations).

Fast checks:

- Ensure Docker is running:

  ```bash
  docker ps
  ```

- If a test starts a container but fails, the Surefire report often includes container logs.
  Still, it can be useful to inspect directly:

  ```bash
  docker ps -a | rg "ligitabl|postgres|test"
  docker logs <container>
  ```

- If a test expects Postgres on a specific port (some scripts/targets do), verify mappings.

Common failure patterns:

- **Liquibase/migration failures**: look for the first migration exception; later errors are usually cascading.
- **Port collisions**: a local Compose DB may already be using a port the test wants.
- **Slow container startup**: rerun once; if it’s consistently slow, check Docker resources.

Additional patterns we’ve hit in this repo:

- **Postgres “SSL connection setup” / handshake failures**: ensure the test JDBC URL forces SSL off (e.g., `sslmode=disable`).
- **Container start timing vs Spring/Liquibase init**: make sure the container is started before any dynamic property registration or Liquibase tries to open a connection.

## jOOQ codegen + missing generated sources after `clean`

If you see compile errors like `package com.ligitabl.model.db... does not exist` (or `cannot find symbol` for jOOQ tables/records), you likely need to re-run jOOQ code generation.

Why it happens:

- Generated jOOQ sources are derived from the migrated DB schema.
- `mvn clean` (or a module clean) can remove build outputs and generated sources.

Fix (recommended):

```bash
make model-codegen-local
```

If the DB is already running and migrated:

```bash
make codegen-fast
```

Manual (if you’re debugging the workflow itself):

```bash
make compose-up-db
make migrate
make codegen
```

## “Unresolved compilation problem” / stale class artifacts

If you see exceptions that look like a runtime error but are actually compiler stubs (e.g., `java.lang.Error: Unresolved compilation problem`), treat it as a build artifact issue first.

Typical fixes:

- Do a clean rebuild of the affected module:

  ```bash
  mvn -pl api -am clean test
  ```

- If it’s a `model`/jOOQ related failure, regenerate first (see the codegen section above).

Where to look:

- `*/target/surefire-reports/*.dumpstream` can contain the most direct signal if the test JVM died mid-run.

## Scripted test runs (logging to repo)

Some scripts/Make targets write logs and exit codes into `scripts/target/` or `api/target/`.

Useful patterns:

```bash
ls -l scripts/target || true

tail -n 80 scripts/target/*.log 2>/dev/null || true
cat scripts/target/*.exit 2>/dev/null || true
```

## Suggestions to make integration tests run better

- Reduce Spring context churn: keep ITs on a small number of `@SpringBootTest` configurations to maximize context cache hits.
- Keep DB startup deterministic: start containers early and make readiness explicit; avoid “implicit” connection attempts during property registration.
- Avoid real outbound network calls: stub external HTTP dependencies in ITs to eliminate flakiness and speed runs.
- Prefer single-container-per-module patterns: static/singleton Postgres containers can significantly reduce overhead.
- Separate fast vs slow suites: run `-DskipITs` by default locally, then run `*IT` explicitly via `make test-api-it` when needed.

## What to collect when asking for help

If you want someone else (or future-you) to reproduce/diagnose quickly, collect:

- The exact command you ran (`mvn ...` or `make ...`)
- The failing test class/method name(s)
- The relevant `*/target/surefire-reports/<TestName>.txt`
- For IT failures: whether Docker is running + any container logs shown in the report
