# Bash/Java replacement verification — 2026-11-09

The operational Python sources and tests have been replaced by Bash, jq and Java/JUnit. Current replacement verification is in progress; the release manifest is marked PENDING_REVALIDATION until the new IAM artifact and Compose acceptance pass. The previous record below is historical evidence for the earlier implementation.

---

# Historical extraction verification — 2026-11-09

**PASS_LOCAL for the extracted 1.0.0 component combination. Production readiness is not claimed.**

Machine-readable evidence and tested image IDs: [deployment/verification-2026-11-09.json](../deployment/verification-2026-11-09.json). Release declaration: [release-manifest.yaml](../deployment/release-manifest.yaml). Exact commands, operational constraints and rollback procedures: [DEPLOYMENT.md](DEPLOYMENT.md). File inventory: [EXTRACTION-CHANGES.md](EXTRACTION-CHANGES.md).

| Check | Actual result |
|---|---|
| Pre-change fast Java baseline | **PASS** — 45 tests, zero failures/errors/skips |
| Pre-change PostgreSQL Testcontainers baseline | **FAIL** — master changelog had invalid `inclide` node referencing a missing file; RealKeycloakIT skipped by opt-in guard |
| Final backend `MAVEN_OPTS='-Xms64m -Xmx384m' ./mvnw -B -ntp clean verify` | **PASS** — 51 unit/security/mock HTTP/architecture tests and 20 PostgreSQL integration tests; one real-Keycloak test skipped here and executed separately below |
| RealKeycloakIT in disposable Compose acceptance | **PASS** — 1 test, zero failures/errors/skips; owned identity creation, ownership markers, roles, repeated profile synchronization, suspend/reactivate and owned deletion |
| Migration repository `./mvnw -B -ntp clean verify` | **PASS** — 3 unit tests and 1 PostgreSQL integration test: clean migration, SQL preview, rerun, history mismatch rejection, required-schema verification, approved rollback of 005 and reapply |
| Existing local migration history, read-only | **PASS** — five exact IDs/authors/paths/checksums; existing DB never migrated or rolled back |
| Changeset / realm file comparison | **PASS** — six actual changeset files and realm JSON byte-identical to pre-edit snapshot |
| IAM offline contract and apply gate | **PASS** — 3 Python tests; realm/clients/roles/audience/scopes/marker policy and explicit apply guard |
| Operational Python tests | **PASS** — 7 tests; release contract, version mismatch, wrong DB/IAM/schema, immutable rollback allowlist, localhost cookie handling and external HTTPS enforcement |
| Frontend refresh regression | **PASS** — 1 Node test; concurrent requests share refresh |
| Docker images | **PASS** — mpl-be:1.0.0, mpl-db-migrations:1.0.0, mpl-iam:1.0.0 built; tested image IDs recorded |
| Compose base and sibling-build override | **PASS** — config validation; separate API/migrator credentials; healthy DB / completed migration and IAM contract dependencies |
| Full disposable Compose acceptance | **PASS** — Keycloak 26.5.5, migration validate/SQL/update, IAM apply/reapply, live read-only contract, API readiness, registration replay, authorization code + PKCE, actual issuer/audience/USER/ADMIN claims, protected API smoke and ADMIN-only metrics |
| Synchronization / provisioning state after E2E | **PASS** — pending outbox 0, incomplete provisioning 0; actual metrics captured in JSON evidence |
| API artifact ownership | **PASS** — API JAR contains neither Liquibase library nor changelogs; no backend realm source remains |
| Native launcher status / syntax | **PASS** — syntax checks and non-mutating status; full native startup against existing data **NOT_RUN** |
| Release manifest validation | **PASS** — explicit versions and schema/IAM assumptions; image identity enforcement implemented; production rollout driver **NOT_RUN** |
| Production migration/rollback/deployment, backup restore/PITR | **NOT_RUN** — no production access or changes; operator backup/recovery references are not a restore rehearsal |
| Previous API rollback compatibility, remote registry promotion, SMTP delivery, sustained load | **NOT_RUN** — no claims made; API rollback allowlist remains empty |

## Migration compatibility

The read-only local DATABASECHANGELOG query found five applied changesets, not the historical four. Their effective paths are `db/changelog/changes/...`, author `mpl-team`, with version-9 checksums. The independent artifact retains exactly those resource-relative paths without introducing logicalFilePath. Tests compare parsed identities/checksums to `mpl-db-migrations/tests/baseline-databasechangelog.csv`; clean and repeat migration were exercised against disposable PostgreSQL 17.6.

Only the malformed master entry was removed. No applied changeset was edited; no clearCheckSums, changelogSync, existing database reset, Keycloak internal table modification or credential rotation was performed. Other environments with different historical paths/checksum versions will be refused for explicit compatibility review. Their history is unverified.

## Identity findings

See [IDENTITY-VERIFICATION.md](IDENTITY-VERIFICATION.md) for cases A–I and test mapping. All tested recovery/concurrency semantics held. The in-flight activation/suspension test confirms final suspended state; repeated completed delivery does not repeat remote side effects. The controlled-concurrency checkpoint recorded timer maxima of 165.587 ms for row lookup/lock acquisition and 261.086 ms for delivery. These are local observations, not production performance guarantees. Locks still span bounded external calls; eventual consistency remains.

## Resolved acceptance failures

1. The baseline malformed `inclide` master prevented Spring startup in PostgreSQL integration. Extraction removes only that invalid directive and retains all real changesets.
2. Early full-stack attempts failed Keycloak health startup while compilation and Docker work competed for local resources. Explicit heap/container caps and a larger first-start health budget were added; final acceptance ran separately and passed. No destructive workaround or existing volume deletion was used.
3. Python urllib withheld Secure cookies on HTTP localhost, causing Keycloak `cookie_not_found` during PKCE. The disposable test driver now emulates the browser's loopback behavior, with tests ensuring external hosts still require HTTPS. Server/client authentication configuration was not changed.

The final successful isolated project was `mpl-acceptance-8150fd54723c`. Its logs and result remain in ignored `.local/`; generated test secrets were removed. Its containers/networks and only its uniquely labelled test volumes were cleaned up. Earlier failed test projects were also cleaned. Docker Desktop was started for this verification; existing native databases, user identities, `.env`, and original `.local` secrets were not replaced.

Restricted command logs are retained under `.local/verification-2026-11-09/`. Existing source had no Git metadata; a pre-edit source hash inventory and original migration/IAM files were saved in `/tmp/mpl-extraction-baseline`. New sibling Git repositories have no remotes/commits; publishing is outside this work.

---

# Verification record

**Historical record: native local startup on 2026-11-05. Superseded by the extraction verification above.**

Date: 2026-11-04 (Asia/Ho_Chi_Minh).

The user explicitly requested code only and no execution. Accordingly:

| Check | Status |
|---|---|
| Workspace inspection | Performed; project created separately under mpl-be |
| Version/source compatibility review | Performed using upstream metadata/source |
| Source/configuration authoring and reading | Performed |
| Maven compilation/package | **Not run** |
| Unit/security/mock HTTP tests | **Not run** |
| PostgreSQL Testcontainers integration tests | **Not run** |
| Real Keycloak opt-in integration test | **Not run** |
| Liquibase validate/update/rollback | **Not run** |
| Docker image build | **Not run** |
| Docker Compose config validation | **Not run** |
| Application startup/end-to-end request | **Not run** |

Initial environment inspection found Maven installed, no Java selected by the shell's `java` command, and Docker's daemon unavailable at that time. Tooling preparation was initiated before the code-only instruction; it is not evidence of a successful build or running backend. Configure Java 21 and Docker before using the acceptance commands in README.

No test pass count, migration success, health status or production-readiness certification is claimed. Dependency versions and architectural choices are recorded; runtime compatibility and behavior still need the acceptance suite in the target environment. Real Keycloak tests need a disposable, configured realm and explicit opt-in. SMTP delivery requires configured transport and a manual or separately automated delivery check.

## 2026-11-05 package-only reorganization

Moved source/test files to the requested layer directories and updated package/import references and architecture documentation. No Maven compilation, tests, application startup, migration or Docker validation was executed. Static source/import/path comparisons and configuration hash comparisons are documented in [PACKAGE-REFACTOR.md](PACKAGE-REFACTOR.md).

## 2026-11-05 native local startup — execution authorized

- Existing Postgres.app PostgreSQL 18.1 started; pre-existing `ie103` database preserved.
- Created isolated `user_service_db` and `keycloak_db`, non-superuser `app_runtime`, `app_migrator` and `keycloak` roles. All three local role connections succeeded. Runtime has no schema CREATE permission and no access to Liquibase tracking tables.
- `.env` configured for localhost; generated secrets retained privately. Added native local process launcher.
- Docker Desktop stopped; no Docker containers started.
- Fixed the ArchUnit selector to use the supported `haveFullyQualifiedName` method.
- `./mvnw -B -ntp package`: SUCCESS, 35 tests, zero failures/errors/skips.
- API started, Hibernate schema validation succeeded; Liquibase applied four changesets, optional seed excluded.
- Native Keycloak 26.5.5 started using the existing Java 21 installation with a 512 MiB heap and local cache.
- Real integration checks exposed missing config-cli profile activation, an invalid unmanaged-attribute enum and missing realm-role read permission. Fixed realm attribute `userProfileEnabled`, used null unmanaged policy and granted `view-realm` to the runtime service account with matching scope mapping. No realm-admin grant.
- keycloak-config-cli 6.5.1 / 26.5.5 successfully applied the realm and was reapplied.
- `RealKeycloakIT`: SUCCESS, 1 test, zero failures/errors/skips; tested real service-account provisioning, profile/status/role operations and owned-identity deletion.
- Real JWT API smoke checks passed: ADMIN create, idempotent replay, filtered/paginated list, profile-to-Keycloak synchronization, suspend, reactivate and DELETE deactivation. Temporary clients/identities/profiles created by the successful and failed smoke runs were cleaned up using exact known identifiers.
- Readiness HTTP 200 / UP; Swagger UI HTTP 200; unauthenticated protected API HTTP 401.
- Testcontainers PostgreSQL integration suite NOT RUN, to keep Docker off. The local smoke run does not substitute for that entire suite.
- SMTP is not configured; email delivery has not been verified.
