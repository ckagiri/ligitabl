# Shared agent memory

Rules for working in ligitabl. Loaded every session through `CLAUDE.md`, so each
entry is the rule and where it came from, nothing more.

## Writing code and docs

**Comment only what the code can't say**: a non-obvious why, a constraint, a live
trap. State the rule, not the incident that taught it; that belongs in the commit or
the PR. Around four lines is the ceiling for an inline comment. Prefer a clear name
or error message to a comment. Terse one-liners such as `// Async - don't wait` are
the register to match.
Check the count while writing, not on review. Past four lines, it is usually one of
these, so cut it:

- restating the file header (say what is local and write "see the header")
- making one point twice
- arguing toward a conclusion (keep the conclusion and the one fact behind it)
- defending a choice nobody questioned (that goes in the commit)
- generic advice such as "keep this in sync"

A comment moves with its code and is cut to fit the new place. Near-identical
comments in sibling files use identical words. File headers and record `@param`
Javadoc may run longer.

**The public API of a shared utility may run longer too** (`Either`, test support such
as `TestCalendar`). Its Javadoc is read in IDE hover at every call site, so keep usage
examples and when-to-use guidance, especially where similar overloads differ in what
they do. Say the choice once in the class header rather than on every overload.
`@param` lines that only rename the argument are still noise.

**No incident history in code.** No dates, PR numbers or `.art/` paths: `.art` is
gitignored, so those links reach nobody else. A test pinning a past bug says what it
pins, not "Regression:" or "this used to".

**No measured figures in comments or docs.** Sizes, counts and durations drift and
nothing fails when they do. Name the property instead ("the largest table", "a
minute-scale delay"), or log the real value. Configured values (intervals, ports,
backoffs) stay exact.

**Cite things by name, not number**: a symbol for code, the heading for a section, what
a step does for a step. No line numbers, `§N` or "Step N"; link the file without a
`#L` fragment.

**Don't write "load-bearing".** Say the specific claim: what rests on it, or what
breaks if it changes.

**Template notes use parser-level comments, `<!--/* … */-->`.** A plain `<!-- -->`
survives Thymeleaf rendering and ships to every visitor. Leave `/*[[…]]*/` inline
expressions and `<!--/*/ … /*/-->` prototype blocks alone; they are code. See
`docs/frontend-dev.md`.

**Never edit a Liquibase changelog that has shipped**, comments included. Add a new
changeset instead.
