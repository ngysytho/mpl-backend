# Architecture decisions

Status: repository extraction and release coordination on 2026-11-09. See VERIFICATION.md for actual acceptance results.

## Package organization

The User module now uses `controller`, `service`, `repository`, `entity`, `dto/request`, `dto/response`, `mapper` and `specification`. Keycloak integration classes are together in `integration.keycloak`; `PageResult` is in `core.response`. Tests follow the relocated packages.

The earlier 2026-11-05 package-only change retained concrete classes, interfaces, separate User/JPA representations and transaction boundaries; see its historical [move manifest](PACKAGE-REFACTOR.md). The 2026-11-09 extraction changes component ownership and test dependencies while retaining those business boundaries and all existing changeset contents.

## 1. Liquibase owns application schema changes

Versioned, ordered, append-only changesets provide auditable PostgreSQL evolution and a controlled pre-rollout migration step. Hibernate validates the result. The standalone mpl-db-migrations runner and unchanged changelog preconditions restrict the target to `user_service_db`. API runtime never executes Liquibase; a database guard rejects migration overrides and non-validation Hibernate modes. No application migration touches Keycloak storage. Rollback SQL exists where practical, but destructive rollback in a populated environment requires a separate recovery decision.

## 2. Keycloak owns identities and credentials

Authentication, password hashing, MFA, sessions, token issuance and role storage belong to Keycloak. A separate database and database owner prevent application JPA or Liquibase from coupling to Keycloak internal tables. The backend stores no password, credential hash or duplicate authentication state.

## 3. keycloak-config-cli owns declarative IAM configuration

Realm/client/role/audience/password-policy changes are reviewed separately from data migrations. The server and CLI are pinned to an explicitly matching release. Environment substitution externalizes secrets and URLs. Remote state and `no-delete` resource behavior prevent a routine apply from silently pruning existing IAM objects. Revocations/removals remain explicit operations; merge behavior is not a revocation mechanism.

## 4. Admin REST handles runtime identity operations

A service-account HTTP client avoids binding the application to Keycloak internal persistence or a server-specific Java SDK. Client credentials, bounded timeouts, safe-operation retry and sanitized errors isolate the external dependency. Creation and email delivery are not blindly retried. Backend role allowlists prevent accidental ADMIN assignment; the configured built-in administrative roles are coarse and should be replaced by fine-grained policies where server-enforced operation isolation is required.

## 5. PostgreSQL owns the User aggregate

The application UUID is distinct from the Keycloak subject. Email, subject and provisioning keys have database uniqueness constraints. Application profile and status writes atomically enqueue the desired identity state. Shared display-name changes flow application → Keycloak; email is immutable after creation in this release. External edits of shared fields are operational drift, not an accepted bidirectional update mechanism.

## 6. Repository adapters isolate persistence

The domain is pure Java and defines its own query/result types. A separate JPA entity includes persistence-only optimistic versioning. Application services use repository and identity-management ports. Spring Data supplies query execution, Hibernate ORM mapping, Specifications bounded predicates, and Liquibase DDL. None substitutes for another's responsibility. ArchUnit checks dependency direction.

## 7. Journal plus outbox, not distributed transactions

A committed provisioning reservation is followed by a row-locked local transaction that may call Keycloak. If it rolls back after creating an identity, the identity remains disabled, with an ownership marker identifying the original request. Replay adopts only that identity. The profile/outbox/journal completion commit together; an after-commit delivery enables the identity. Updates and deactivation are similarly delivered through the outbox. Row locks across bounded external calls are an intentional initial tradeoff; provision throughput must be load-tested. A future lease-based worker can remove long-held transactions without changing the ownership policy.

Completed requests are retained for the lifetime of their profiles; keys/emails are not silently recycled. Incomplete creates require caller/operator replay. The worker retries only already-committed profile state. There is no automatic destructive compensation and no claimed exactly-once delivery.

## 8. Boot-managed JUnit 6 and external migration tests

Boot 4.0.9 manages Jupiter 6.0.3, compatible with Framework 7.0.10. The explicit JUnit 5 BOM override is removed. MockMvc uses SpringJUnitWebConfig and PostgreSQL integration uses SpringBootTest. A context initializer starts only disposable PostgreSQL and the independently built migration image before Hibernate validation. Existing behavioral tests and ArchUnit rules remain; Docker failures are not silently skipped.

## 9. Independent component releases

mpl-db-migrations owns unchanged changeset contents/logical resource paths and its standalone runner. mpl-iam owns the unchanged realm JSON, config-cli policy and lifecycle scripts. mpl-be owns integration Compose and deployment/release-manifest.yaml. IAM apply is explicit; startup uses a read-only contract job. Separate runtime/migration/IAM configuration credentials remain separate. No microservices, extra business database, module or infrastructure repository is introduced. See DEPLOYMENT.md for trade-offs, release stages and distinct API/database/IAM recovery procedures.
