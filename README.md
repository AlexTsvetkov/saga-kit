# saga-kit

**Eventing-correctness library for SAP BTP/Kyma commerce meshes — idempotency, ordering, transactional outbox and DLQ as declarative middleware.**

**🌐 Live site: https://alextsvetkov.github.io/saga-kit/**

> ⚠️ **Status:** early scaffold. The core abstraction, a starter implementation and tests are real; this is a foundation to build on, not a finished product. See [Roadmap](#roadmap).

**Stack:** Java 21 + Gradle.

---

## The problem

BTP/Kyma extension meshes re-implement retry/idempotency/ordering by convention. The 'return 200 on unrecoverable error, 400 only on retryable, swallow 409 as success' NATS invariant is tribal knowledge re-coded in every handler; one wrong status = a redelivery storm or a lost event.

## The solution

A library encoding the correct semantics — idempotency keys, dedup windows, transactional **outbox**, ordered delivery groups, DLQ handling, and the 'ack-and-discard vs retry' decision — as declarative middleware, not copy-paste.

See the [project site](https://alextsvetkov.github.io/saga-kit/) for the full benefits narrative.

## Design principles

1. **Correct by default** — New handlers get idempotency, retry mapping and DLQ handling for free.
2. **Encode the invariant** — The 200/400/409 retry semantics are a library primitive, not a comment.
3. **Structural ordering** — Ordered delivery and outbox make sequencing a property of the system, not a per-handler hack.
4. **Broker-pluggable** — NATS/Kyma Eventing first; the abstraction admits other brokers.

## Core abstraction

`EventHandlerPipeline` — Wraps an event handler with idempotency, the 200/400/409 retry-semantics mapping, tracing and DLQ handling so handlers only declare intent.

## Features

| Capability | Description |
|------------|-------------|
| ``@IdempotentHandler`` | Dedup by message id within a window. |
| `Retry mapping` | Declarative retryable vs ack-and-discard. |
| `Outbox` | Transactional publish with a relay. |
| `DLQ policy` | Standard dead-letter handling + replay. |

## Quick start

```bash
gradle build
gradle test
```

## Roadmap

- [ ] Flesh out the core beyond the starter implementation.
- [ ] Wire against a live SAP Commerce / BTP environment.
- [ ] Publish artifacts and usage docs.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Conventional commits; generated code stays out of version control.

## License

[MIT](./LICENSE) © 2026 Aliaksandr Tsviatkou

## Honest assessment

> From the v2 self-critical analysis. Scores use **Gap · Value · Moat · Time-to-revenue · Risk** (for Risk, **higher = safer**). Prior art is named deliberately — "no competitor" is almost never true.

**Scores:** Gap 2 · Value 3 · Moat 2 · TTR 3 · Risk 4

- **Prior art / competition.** Transactional outbox is a documented pattern; Axon, Eventuate, Spring messaging and NATS JetStream already cover most of it. The SAP-specific 200/400/409 mapping is ~50 lines.
- **True differentiator.** Convenience + encoding one tribal invariant. Not a moat.
- **Kill criterion.** If it doesn't get GitHub traction as free middleware, there's no business beneath it.
- **Verdict.** **OSS-only.** A credibility/funnel bet, not a company.

See the full landscape, go-to-market and the **IP / conflict-of-interest** discussion in [sap-commerce-general-ideas-for-startup.md](https://github.com/AlexTsvetkov/sap-commerce-ideas-for-projects/blob/main/ideas-for-startup/sap-commerce-general-ideas-for-startup.md).

---

*Part of a backend tooling suite for SAP Commerce Cloud. See [`commerce-mcp`](https://github.com/AlexTsvetkov/commerce-mcp) for the AI-native flagship.*
