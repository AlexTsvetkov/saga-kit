# saga-kit

**Eventing-correctness library for SAP BTP/Kyma commerce meshes — idempotency, ordering, transactional outbox and DLQ as declarative middleware.**

**🌐 Live site: https://alextsvetkov.github.io/saga-kit/**

> ✅ **Status:** working core. A real, tested implementation of the core capability runs offline (no live SAP Commerce instance needed); unit tests pass in CI. Not yet a production product — see [Roadmap](#roadmap) for what would make it one.

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

## Usage

An `EventHandlerPipeline` wraps a plain handler with the NATS/Kyma 200/400/409
acknowledgement contract: fresh events run and ack (200); duplicate ids ack
without re-running the handler (409); transient failures return 400 (retry) until
the attempt budget is exhausted, then dead-letter (200, `sentToDlq=true`);
non-transient failures ack-and-discard (200, no DLQ). The full runnable tutorial
is at `src/main/java/com/sapcommercetools/saga/examples/Example.java`:

```java
// Scenario A: fresh (200) then duplicate (409) — handler runs at most once.
AtomicInteger runs = new AtomicInteger();
List<Event> dlq = new ArrayList<>();
EventHandlerPipeline pipeline = new EventHandlerPipeline(
        event -> runs.incrementAndGet(), dlq::add);

Event a = Event.of("evt-A", "OrderPlaced");
HandleResult first = pipeline.dispatch(a);
System.out.println(first.status() + " isOk=" + first.isOk() + " runs=" + runs.get());
HandleResult dup = pipeline.dispatch(a);              // same id
System.out.println(dup.status() + " isDuplicate=" + dup.isDuplicate() + " runs=" + runs.get());

// Scenario B: always-transient handler, maxAttempts=3 -> 400,400 then DLQ (200).
List<Event> dlqB = new ArrayList<>();
EventHandlerPipeline retrying = new EventHandlerPipeline(
        event -> { throw new TransientException("downstream 503"); }, 3, dlqB::add);
Event b = Event.of("evt-B", "InventorySync");
for (int i = 1; i <= 3; i++) {
    HandleResult r = retrying.dispatch(b);
    System.out.println("delivery " + i + ": " + r.status()
            + " retry=" + r.isRetry() + " dlq=" + r.sentToDlq());
}
System.out.println("DLQ size=" + dlqB.size());

// Scenario C: non-transient failure -> ack-and-discard (200, no DLQ).
List<Event> dlqC = new ArrayList<>();
EventHandlerPipeline discarding = new EventHandlerPipeline(
        event -> { throw new IllegalStateException("malformed payload"); }, dlqC::add);
HandleResult c = discarding.dispatch(Event.of("evt-C", "BadMessage"));
System.out.println(c.status() + " isOk=" + c.isOk() + " dlq=" + c.sentToDlq()
        + " dlqSize=" + dlqC.size());
```

```text
Output:
200 isOk=true runs=1
409 isDuplicate=true runs=1
delivery 1: 400 retry=true dlq=false
delivery 2: 400 retry=true dlq=false
delivery 3: 200 retry=false dlq=true
DLQ size=1
200 isOk=true dlq=false dlqSize=0
```

Gradle is not required. Compile and run the full tutorial with the plain JDK (Java 21):

```bash
find src/main/java -name '*.java' | xargs javac -d out
java -cp out com.sapcommercetools.saga.examples.Example
```

> With Gradle installed you can instead wire a `JavaExec` task
> (`mainClass = 'com.sapcommercetools.saga.examples.Example'`) and run
> `gradle run`; the `javac`/`java` path above always works with just the JDK.

## Roadmap

- [x] Implement the core capability with real logic + unit tests.
- [ ] Broaden coverage (more rules/edge cases) beyond the first working version.
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

This assessment is part of a broader, self-critical analysis of the whole tool suite (problem landscape, go-to-market, and an IP / conflict-of-interest review) maintained privately by the author.

---

*Part of a backend tooling suite for SAP Commerce Cloud. See [`commerce-mcp`](https://github.com/AlexTsvetkov/commerce-mcp) for the AI-native flagship.*
