# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository.

## What this project is

**saga-kit** — Eventing-correctness library for SAP BTP/Kyma commerce meshes — idempotency, ordering, transactional outbox and DLQ as declarative middleware.

BTP/Kyma extension meshes re-implement retry/idempotency/ordering by convention. The 'return 200 on unrecoverable error, 400 only on retryable, swallow 409 as success' NATS invariant is tribal knowledge re-coded in every handler; one wrong status = a redelivery storm or a lost event.

**Solution:** A library encoding the correct semantics — idempotency keys, dedup windows, transactional **outbox**, ordered delivery groups, DLQ handling, and the 'ack-and-discard vs retry' decision — as declarative middleware, not copy-paste.

> Status: early scaffold. The core abstraction, a starter implementation and tests are real; most capabilities are documented intent, not yet built. Do not claim features exist that aren't in the code.

## Stack

Java 21 + Gradle (`java-library` plugin), JUnit 5.

## Project layout

- `src/main/java/**` — production code (core abstraction: `EventHandlerPipeline`).
- `src/test/java/**` — JUnit 5 tests.
- `build.gradle`, `settings.gradle` — build config.
- `docs/` — GitHub Pages site (`index.html`, `.nojekyll`). Served at https://alextsvetkov.github.io/saga-kit/.
- `.github/workflows/ci.yml` — CI (build + test on push/PR).

## Common commands

```bash
gradle build      # compile
gradle test       # run tests
```

## Conventions

- Prefer **constructor injection**; interface + `Default*` impl per service.
- No inline literals — use constants classes for log/config/exception strings.
- Keep the core abstraction (`EventHandlerPipeline`) honest so implementations stay swappable.
- **Conventional commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`).
- Generated code (if any) stays out of version control.
- Keep `README.md`, `docs/index.html` and this file in sync when the scope changes.

## Working agreements for agents

- This is part of a **suite of SAP Commerce backend tools**; keep terminology consistent with the sibling repos (e.g. `commerce-mcp`, `flow-context`).
- When adding real behaviour, update the Roadmap in `README.md` and add tests in the same PR.
- Don't introduce a live-backend dependency into the default build — keep the scaffold green on a clean checkout.
- If you change the public contract, reflect it in the docs site and the README capability table.
