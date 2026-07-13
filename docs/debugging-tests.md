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

   - API integration tests (DB-backed `*IT` and `*IntegrationTest`, direct Maven invocation):

     ```bash
     mvn -pl api -am -DskipITs=false -Dtest='**/*IT,**/*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
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

## Build failures that look like classpath issues (stale bytecode)

Sometimes failures present as “classpath” or “cannot access … class file not found” errors even though the
dependency is present. In this repo, we’ve seen this happen when **`model/target/classes` contains stale or
incompatible bytecode** (e.g. Lombok-generated builders not matching the current sources).

### Common symptoms

- `cannot access AbstractModel` / `class file for AbstractModel not found`
- `cannot access TeamRank` / `RoundSwap` / `SwapChange`
- `cannot find symbol: method getId()` on `SeasonPrediction`
- `cannot find symbol: method id(UUID)` on `SeasonPrediction.builder()`
- Weird generics mismatches like:
  - `java.util.List<TeamRank> cannot be converted to java.util.List<com.ligitabl.model.domain.TeamRank>`

These typically show up:

- During reactor builds (`mvn -pl api -am …`) but not in `api`-only builds, or
- During `testCompile` even when `compile` is green.

### Fast isolation: api-only vs reactor

Start by checking whether the failure is specific to the multi-module reactor:

```bash
# API only (uses the installed model jar from ~/.m2)
mvn -pl api -DskipTests -DskipITs clean compile

# Reactor build (uses modules from the workspace)
mvn -pl api -am -DskipTests -DskipITs compile
```

If `api`-only succeeds but the reactor build fails, it’s a strong hint that workspace module outputs under
`model/target/` are stale.

### Inspect the compiler classpath (last resort, but definitive)

Capture a full Maven debug compile and inspect what `javac` is actually seeing:

```bash
rm -f api/target/tmp-compile-debug.log
mvn -pl api -am -DskipTests -DskipITs -X compile > api/target/tmp-compile-debug.log 2>&1

# Find the javac classpath line
rg -n -- "-classpath" api/target/tmp-compile-debug.log | head

# Confirm model outputs are (or are not) on the classpath
rg -n -- "model/target/classes" api/target/tmp-compile-debug.log | head
```

### Compare bytecode: `model/target/classes` vs the local Maven jar

When the error mentions missing methods (`getId`, builder `.id(...)`), verify the compiled signatures:

```bash
# What does the workspace module output contain?
javap -classpath model/target/classes -public com.ligitabl.model.domain.AbstractModel | head -n 40
javap -classpath model/target/classes -public com.ligitabl.model.domain.SeasonPrediction\$SeasonPredictionBuilder | head -n 80

# Compare against what Maven would normally consume for api-only builds
javap -classpath ~/.m2/repository/com/ligitabl/ligitabl-model/0.1.0-SNAPSHOT/ligitabl-model-0.1.0-SNAPSHOT.jar \
  -public com.ligitabl.model.domain.SeasonPrediction\$SeasonPredictionBuilder | head -n 120
```

If the `model/target/classes` builder doesn’t extend `AbstractModel$AbstractModelBuilder` but the jar version
does, you’re compiling tests against stale bytecode.

### Minimal, safe fix: delete only the stale compiled model classes

Prefer this over `mvn clean` because the `model` module also contains generated jOOQ sources.

```bash
rm -rf model/target/classes/com/ligitabl/model/domain \
       model/target/classes/com/ligitabl/model/shared \
  && ./mvnw -pl api -am -DskipITs test
```

Notes:

- This forces Maven to recompile the model domain classes without deleting `model/target/generated-sources/jooq`.
- If you run a full `clean` on `model/` and jOOQ codegen is skipped (default), model compilation may fail if
  something imports `com.ligitabl.model.db.*`.

### Stale installed jars in `~/.m2` (duplicate-class / "incompatible types: X cannot be converted to X")

A related but distinct symptom: `mvn -pl api -am ...` fails across many unrelated test files with
errors like `incompatible types: com.ligitabl.model.repo.ContestRepo cannot be converted to
ContestRepo` — the *same* simple class name reported as incompatible with itself. This means two
copies of the class are on the classpath simultaneously: the reactor's freshly-built
`model/target/classes` **and** a previously-`mvn install`ed jar in
`~/.m2/repository/com/ligitabl/ligitabl-model/...` (or `ligitabl-api`). This can happen after
running any Make target that does `mvn install` (e.g. `make codegen`/`make model-codegen-local`
install `jooq-codegen`, and some workflows also install `model`/`api`).

Fix — remove the stale installed jars so the reactor build is the only source of those classes:

```bash
rm -rf ~/.m2/repository/com/ligitabl/ligitabl-model ~/.m2/repository/com/ligitabl/ligitabl-api
rm -rf api/target/classes api/target/test-classes model/target/classes model/target/test-classes
mvn -pl api -am -DskipITs test
```

**Shell gotcha:** if your shell aliases `rm` to `rm -I` (interactive-ish confirmation, common in
zsh setups), a scripted `rm -rf` can silently print a `recursively remove N dirs?` prompt and
no-op when run non-interactively (no TTY to answer `y`) — the directories are *not* deleted, and
the next command runs against the still-stale classes, so the fix appears not to have worked.
Check `type rm` first; if it's aliased, use `command rm -rf ...` (or `\rm -rf ...`) to bypass the
alias and guarantee the deletion actually happens.

### If `clean` is required: regenerate jOOQ sources first

If you *must* clean `model/target/` and you later hit missing `com.ligitabl.model.db.*` types, regenerate via:

```bash
# Makefile path (recommended)
make codegen

# Or pure Maven
mvn -pl model -Pwith-jooq generate-sources
```

Then re-run your tests.

### "BUILD SUCCESS" can lie: unresolved references get compiled into runtime-throwing stubs

A more insidious variant of the stale-bytecode problem: Maven reports `BUILD SUCCESS` — even
`[INFO] Nothing to compile - all classes are up to date.` — for a module whose source no longer
compiles (e.g. you renamed/removed a method and a test still calls the old one; you added a
constructor param and a test's `new Foo(...)` call is now short one argument). No compiler error,
no red output, tests appear to run.

**Root cause**: the class file on disk was compiled by something other than this Maven invocation
— most likely an IDE's background Java compiler (e.g. a JDT/Eclipse-based language server) that
recompiled the file after your edit and wrote a fresh `.class` to `target/classes` or
`target/test-classes` directly. That compiler doesn't hard-fail on an unresolved method/constructor
reference; it embeds the error as a `String` and throws it at runtime only if that specific line
executes — so `javap` on the file still shows valid bytecode, and Maven's mtime-based staleness
check sees a `.class` newer than the `.java` and skips recompilation entirely, trusting output it
never produced. If the offending line/branch happens not to run during your `mvn test` pass (a
different test method, or the assertion never reached because an earlier one already failed), the
suite goes green with a broken method silently sitting in the jar.

**Symptom, if you do hit the throwing line**: a runtime exception (not a compile failure) whose
message starts with `Unresolved compilation problem(s):` (sometimes truncated at `\n\t` line
breaks in surefire's report), e.g.:

```
Unresolved compilation problems:
        The method isOpenForJoining(Contest, Season, Competition) in the type ContestSupport is not applicable for the arguments (boolean, int, Round)
        The method isOpen() is undefined for the type PrivateContestRowDto
```

**Detect it directly** (don't trust `BUILD SUCCESS` after any signature/constructor change to a
class other tests reference) — scan the compiled `.class` files for the embedded stub marker:

```bash
for f in $(find api/target/test-classes api/target/classes -name "*.class"); do
  javap -c "$f" 2>/dev/null | grep -q "Unresolved compilation problem" && echo "BROKEN: $f"
done
```

Scope this to the specific classes/packages you touched (or that depend on what you touched) —
scanning an entire module's `target/` this way is slow (can run past a couple of minutes on
`api`'s full tree).

**Fix**: force an honest recompile, then re-scan.

```bash
# Module-scoped, keeps model/target/generated-sources/jooq intact (see the clean-vs-model-compile
# note above — do NOT run `mvn ... -am clean`, it wipes jOOQ generated sources too)
rm -rf model/target/classes api/target/classes api/target/test-classes
mvn -q -DskipTests -pl model -am compile
mvn -DskipTests -pl api -am test-compile   # drop -q here; watch for real [ERROR] lines
```

If `mvn ... test-compile` still reports success but you're suspicious, re-run the `javap` scan
above against the freshly produced classes as the definitive check — a real `javac`-driven Maven
compile never produces the `Unresolved compilation problem` marker; only trust `BUILD SUCCESS`
once that scan comes back clean.

## Mockito “unnecessary stubbing” (strict stubs)

This repo keeps Mockito strict by default. If you see “unnecessary stubbing” failures, fix by removing or
scoping stubs to only the tests that actually execute those code paths.

Typical approach:

- Don’t blanket-stub collaborators in `@BeforeEach`.
- Stub only inside the test(s) that need it.
- Prefer removing unused stubs over adding `lenient()`.

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
- If you run Maven with `-q`, you may not get a visible `BUILD SUCCESS`/`BUILD FAILURE` banner in the log.
  - Prefer the exit code (`$?` / the `.exit` file) and Surefire XML (`*/target/surefire-reports/TEST-*.xml`) as the source of truth.

## Makefile test targets (quick reference)

Short summaries of the test-focused targets in [Makefile](Makefile):

- `test`: full API test suite (includes unit + integration).
- `test-unit`: only guard-style unit tests (fastest slice).
- `test-api-core`: core API tests (skips `*IT`).
- `test-api-core-with-codegen`: start DB, migrate, run jOOQ codegen, then `test-api-core`.
- `test-api-it`: DB-backed `*IT` and `*IntegrationTest` tests only.
- `test-api-all`: full API test suite (same scope as `test`).
- `test-all`: full repo test suite (all modules).

### Model “domain-only” tests (with or without jOOQ codegen)

The `model` module contains both:

- pure domain/unit tests (e.g. `model/src/test/java/com/ligitabl/model/domain/*Test.java`)
- repo/infra tests that depend on jOOQ generated types (`com.ligitabl.model.db.*`)

By default, jOOQ codegen is skipped to allow fast builds. That means repo/infra tests that import generated classes will fail to compile unless codegen has been run.

To make “domain-only” tests easy to run in either mode, the Makefile provides:

- `test-model-domain`: runs only the model domain/unit test slice using the model’s `no-jooq` profile.
  - Why: avoids compiling/running repo/infra tests that require generated jOOQ classes.
  - Command:

    ```bash
    make test-model-domain
    ```

- `test-model-domain-with-codegen`: runs jOOQ codegen first (heavier), then executes only the domain tests.
  - Why: useful if you want generated code present, but still want a quick “domain-only” test run.
  - Command:

    ```bash
    make test-model-domain-with-codegen
    ```

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

## Field notes: fast, low-noise workflows

- **Quiet Maven runs + explicit module targeting**
  - When you want a short, actionable output stream, use `-q` and `-pl` together.

  ```bash
  mvn -q -pl api -am -DskipITs test
  ```

- **If the exit code says fail but logs look clean**
  - Scan all `TEST-*.xml` files across modules to find hidden failures:

  ```bash
  python3 - <<'PY'
  import glob
  import xml.etree.ElementTree as ET

  fails=[]
  for path in glob.glob('**/target/surefire-reports/TEST-*.xml', recursive=True):
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

- **Use `rg -n` on logs when the output is huge**
  - The fastest way to spot test failures in a long log:

  ```bash
  rg -n "\[ERROR\]|FAILURE!|There are test failures" api/target/test-api.log
  ```

  - If you can’t find expected strings in `api/target/test-api.log`, check whether:
    - the test run used a different log path (VS Code tasks sometimes log to `api/target/test-api*.log` variants), or
    - your editor search is excluding `target/` (common). In that case, use terminal `grep/rg` or enable “include ignored files”.

  - **Package move + refactor via a small Python snippet**
    - When renaming packages across many files, a simple `python3` pass can rewrite imports + package declarations quickly.
    - Example used to rename `com.ligitabl.api.usecases` to `com.ligitabl.api.rest` after moving folders:

    ```bash
    python3 - <<'PY'
    from pathlib import Path

    root = Path('.')
    old = 'com.ligitabl.api.usecases'
    new = 'com.ligitabl.api.rest'

    changed = []
    for path in root.rglob('*'):
      if not path.is_file():
        continue
      if path.suffix not in {'.java', '.kt', '.md', '.yml', '.yaml', '.xml', '.properties'}:
        continue
      try:
        text = path.read_text(encoding='utf-8')
      except Exception:
        continue
      if old in text:
        path.write_text(text.replace(old, new), encoding='utf-8')
        changed.append(path)

    print(f'Updated {len(changed)} files')
    PY
    ```

## Recent learnings (from debugging this repo)

- **JUnit discovery failures can come from missing jOOQ-generated classes.**
  - Symptoms include:
    - `TestEngine with ID 'junit-jupiter' failed to discover tests`
    - `NoClassDefFoundError: Team` (or other model types)
    - `package com.ligitabl.model.db.tables does not exist`
  - Root cause: model module compiled without generated jOOQ types (or stale classes on the classpath).
  - Fix (safe, ordered):
    1. Generate jOOQ types against the current test DB:
       ```bash
       make model-codegen-local
       ```
    2. Re-run core API tests:
       ```bash
       make test-api-core
       ```
    3. If errors persist, clean API classes and re-run:
       ```bash
       mvn -q -pl api clean
       make test-api-core
       ```
  - Shortcut target (does steps 1 + 2):
    ```bash
    make test-api-core-with-codegen
    ```

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

- **Mockito strict stubbing failures (`UnnecessaryStubbingException`) are usually caused by a refactor changing the call path.**
  - Symptom: the test fails even though assertions look fine, with an error like:
    - `org.mockito.exceptions.misusing.UnnecessaryStubbingException: Unnecessary stubbings detected`
  - Fix (preferred): remove the stale `when(...)` stub for the call that no longer happens in that scenario.
  - Alternatives:
    - make the specific stub lenient (`lenient().when(...)...`) if the stubbing is intentionally shared, or
    - reduce shared setup in `@BeforeEach` so each test only stubs what it uses.

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

This can also show up as **JUnit failing to discover tests** (e.g. `TestEngine with ID 'junit-jupiter' failed to discover tests`) when a previously-compiled test class (or something it references) can no longer be resolved at runtime.

Typical fixes:

- Do a clean rebuild of the affected module:

  ```bash
  mvn -pl api -am clean test
  ```

- If you want a faster “clean without `mvn clean`”, delete compiled outputs and re-run:

  ```bash
  rm -rf api/target/classes api/target/test-classes
  mvn -pl api -am -DskipITs test
  ```

- Makefile shortcut (recommended):

  ```bash
  make api-clean-classes
  make test-api-core
  ```

- If it’s a `model`/jOOQ related failure, regenerate first (see the codegen section above).

**This can silently produce a false "BUILD SUCCESS", not just a runtime error** — concrete case
from task 66 (Turnstile CAPTCHA): a line referenced `HttpServletResponse.SC_UNPROCESSABLE_ENTITY`,
which doesn't exist (the Jakarta Servlet API only defines classic HTTP/1.1 status constants, not
WebDAV's 422). `mvn -q -pl api compile` reported success with **zero output**, and a subsequent
`mvn -pl api test` run of the affected controller's tests also passed — because Maven's
incremental compiler decided nothing had changed and reused a stale `.class` file from before the
bad edit, so the broken source was never actually compiled in either run. The bug only surfaced as
a real `500 Internal Server Error` (`java.lang.Error: Unresolved compilation problem`) when hitting
the running app directly. **Lesson: a green `mvn compile`/`mvn test` is not proof the current
source compiles** — if you've just changed a file and the build looks suspiciously fast or quiet,
force a real recompile before trusting it:

```bash
rm -rf api/target/classes api/target/test-classes
mvn -pl api compile   # now watch for "Recompiling the module because of changed source code"
```

That log line — `Recompiling the module because of changed source code` — is the actual signal
that javac ran against your current source. Its absence (`Nothing to compile - all classes are up
to date`) after a real edit is the tell that you're looking at stale output.

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
- Separate fast vs slow suites: run `-DskipITs` by default locally, then run `*IT` and `*IntegrationTest` explicitly via `make test-api-it` when needed.

## Updating tests after a query behaviour change

When a query is refactored (e.g. INNER JOIN → LEFT JOIN), existing tests often break because the result set grows or the semantics change. The workflow below was used when the leaderboard query was changed to return unscored participants.

### 1. Identify which tests break — and why

Run the test suite (after confirming changes compile) and note which assertions fail:

```bash
make test-api-it
```

Then ask: **did the query return _more_ rows than before, fewer, or different columns?** Each maps to a different fix pattern:

| Change | Typical symptom | Fix |
|---|---|---|
| INNER JOIN → LEFT JOIN | `hasSize(N)` fails because unmatched rows now appear | Update expected size; add assertions for the new rows |
| New column / flag | Compile errors or wrong defaults | Add the field to the domain record and update all call sites |
| Different ordering | Wrong `get(0)` result | Understand new sort key; update assertions accordingly |

### 2. Distinguish pre-existing failures from your changes

Use `git stash` to temporarily restore the previous state and confirm which failures existed before your change:

```bash
git stash
make test-api-it 2>&1 | grep "Tests run:"
git stash pop
```

If the same tests fail without your changes, they are pre-existing issues — don't try to fix them as part of the current task.

### 3. Test naming and Make targets

**`make test-api-it` filters by `*IT` and `*IntegrationTest` naming patterns.**

A class named `FooIntegrationTest` is included; one named `FooTest` is not. If a test you expect to see is missing from the run output, check its class name:

```bash
# confirm whether a test class matches the filter
rg -rn "class.*IntegrationTest\|class.*IT " api/src/test/java
```

If a DB-backed test is named `*Test` (not `*IT` / `*IntegrationTest`), either rename it or run it with the full test suite:

```bash
make test-api-all
```

### 4. Patterns for left-join refactors (scored vs unscored rows)

When a query changes from "only return rows with results" to "return all participants with results optionally joined", a `scored` flag is typically added to the domain type. Tests need updates in two ways:

**Size assertions** — the result set is now larger:

```java
// Before: only scored users returned
assertThat(results).hasSize(1);

// After: all participants (scored + unscored)
assertThat(results).hasSize(3);
```

**Ordering assertions** — scored users sort above unscored users (via `isScoredField.desc()` as the first ORDER BY key), so existing index-based assertions (`get(0)`) usually still hold for scored-only scenarios, but add explicit checks:

```java
assertThat(results.get(0).scored()).isTrue();
assertThat(results.get(1).scored()).isFalse();
```

**New tests to add** after a left-join refactor:
- No results exist yet → participants appear with `scored=false`
- Scored users always rank above unscored users
- Eligibility filter (e.g. `atRoundNumber <= phaseToRound`) excludes late joiners from phases they missed

## What to collect when asking for help

If you want someone else (or future-you) to reproduce/diagnose quickly, collect:

- The exact command you ran (`mvn ...` or `make ...`)
- The failing test class/method name(s)
- The relevant `*/target/surefire-reports/<TestName>.txt`
- For IT failures: whether Docker is running + any container logs shown in the report
