# Identity synchronization verification

The existing journal/outbox algorithm, ownership checks, disabled creation, row-lock ordering, status enforcement, bounded retry/backoff and no-destructive-compensation policy are retained. There was no evidence requiring a lease-worker replacement. Delivery remains eventually consistent and may repeat safe remote operations after a crash; exactly-once delivery is not claimed.

| Failure/concurrency case | Executed regression coverage |
|---|---|
| A: remote create succeeds, DB persistence fails | PostgreSQL trigger forces rollback; committed journal survives; owned identity remains disabled; original request recovers |
| B: original key and body replay | One profile/identity; admin and registration retries preserved; registration never resets accepted password |
| C: Keycloak outage | Durable outbox, attempts/backoff persisted, no premature enable; recovery drains pending state |
| D: multiple create requests | Same-key concurrent calls serialize; distinct keys sharing email produce one winner and one conflict |
| E: multiple workers | Two workers wait on the actual PostgreSQL row lock; only one completes remote delivery; repeated delivery is a no-op |
| F: old event versus suspension | Pending activation reads newest suspended state; in-flight activation is followed by suspension, with final remote state disabled |
| G: ambiguous timeout | Real HTTP timeout is not blindly retried; journal replay finds only the exact owned remote identity |
| H: unrelated same-email identity | Ownership mismatch cannot be adopted or deleted; identity client refuses unrelated compensation |
| I: repeated delivery | Completed outbox delivery does not repeat side effects; profile/role operations remain idempotent |

The final PostgreSQL run passed 20 tests. At the controlled-concurrency checkpoint, process timer maxima were 165.587 ms for row lookup/lock acquisition and 261.086 ms for delivery including transaction handling. These are observations from a constrained local run, not isolated service-level latency estimates or capacity claims. Sustained load, provisioning throughput and production lock contention remain unmeasured.

Metrics include pending/incomplete counts, pending retry sum, oldest age, attempts/failures, delivery duration, lock wait, committed synchronization latency and Keycloak failures. Metric tags contain no identity IDs, emails, tokens or passwords. ADMIN authorization protects Actuator metrics. Hibernate rejected-row detail logging is disabled; API errors remain sanitized.

Real Keycloak and Compose evidence is recorded separately in VERIFICATION.md. PostgreSQL tests mock only the identity port; they do not substitute for the real token/role/runtime checks. SMTP delivery, user/password recovery outside the original request contract and production recovery rehearsals are not certified.
