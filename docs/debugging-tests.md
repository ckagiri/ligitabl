# Debugging Test Failures (Maven Surefire + Integration Tests)

This repo uses Maven Surefire for unit tests and (depending on the Make target) runs DB-backed integration tests via Testcontainers + Liquibase.

This guide is a practical checklist for diagnosing failures quickly using the same toolbox we use while iterating: `mvn`, `make`, `ls`, `tail`, `rg`, small `python` snippets for report parsing, and log capture.

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

## Scripted test runs (logging to repo)

Some scripts/Make targets write logs and exit codes into `scripts/target/` or `api/target/`.

Useful patterns:

```bash
ls -l scripts/target || true

tail -n 80 scripts/target/*.log 2>/dev/null || true
cat scripts/target/*.exit 2>/dev/null || true
```

## What to collect when asking for help

If you want someone else (or future-you) to reproduce/diagnose quickly, collect:

- The exact command you ran (`mvn ...` or `make ...`)
- The failing test class/method name(s)
- The relevant `*/target/surefire-reports/<TestName>.txt`
- For IT failures: whether Docker is running + any container logs shown in the report
