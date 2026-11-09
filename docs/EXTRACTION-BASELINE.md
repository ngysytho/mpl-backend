# Extraction baseline — 2026-11-09

Before edits: 45 Java tests passed, zero failures/errors/skips. The previous 35-test record is historical. Spring Boot 4.0.9 / Framework 7.0.10; forced Jupiter 5.13.4 / Platform 1.13.4; Boot manages Jupiter 6.0.3. Liquibase 5.0.4; PostgreSQL JDBC 42.7.14; Testcontainers 2.0.5.

No Git metadata was supplied. A SHA-256 source inventory and original migration/IAM files were saved in /tmp/mpl-extraction-baseline before changes. Existing .env and .local secret files were not copied or modified.

The local database was queried read-only for DATABASECHANGELOG metadata only: five applied changesets, not four. Exact identities/checksums are retained in ../mpl-db-migrations/tests/baseline-databasechangelog.csv. No user data was queried or changed. The source master has an invalid `inclide` entry referencing a nonexistent seed/002-development-seed-provisioning.yaml; extraction removes this invalid directive, preserving every actual changeset byte and the valid include order.

Existing startup dependencies: local and test profiles execute Liquibase; production disables it but base permits an environment override. API local launcher also automatically reapplies IAM configuration. Runtime API handles Admin REST, journal/outbox, ownership markers and retries; these remain backend responsibilities. Move changelogs, migration execution, realm JSON, no-delete settings and IAM lifecycle scripts to siblings. Preserve profile/identity UUIDs, REST contracts, authorization, PKCE, default roles, account status, transactional journal/outbox and bounded HTTP retries.

Docker initially unavailable; Docker Desktop was started for disposable acceptance tests. Baseline `./mvnw verify` then FAILED: UserPersistenceIT could not initialize because Liquibase rejected the invalid `inclide` node; RealKeycloakIT was skipped by its opt-in guard. The 45 fast tests still passed. The frontend Node test also passed. No existing database or realm may be used for destructive tests. Final results are recorded in VERIFICATION.md.
