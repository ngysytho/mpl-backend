# Release coordination and recovery

## Repository boundaries

```text
projects/
├── mpl-be/                  # One modular monolith, User module, runtime IAM integration
│   ├── Dockerfile
│   ├── docker-compose.yml   # Main persistent local integration stack
│   ├── compose.build.yaml   # Optional sibling build contexts
│   ├── deployment/release-manifest.yaml
│   ├── scripts/{release,smoke,acceptance,local}.sh
│   ├── docker/              # Existing two-database bootstrap/grants
│   └── src/{main,test}/
├── mpl-db-migrations/       # Standalone Java/Liquibase runner, no Spring Boot
│   ├── pom.xml, Dockerfile, mvnw
│   ├── src/main/resources/db/changelog/
│   ├── src/main/java/com/mpl/migrations/MigrationMain.java
│   ├── src/test/java/com/mpl/migrations/
│   ├── tests/baseline-databasechangelog.csv
│   └── scripts/migrate.sh
└── mpl-iam/                 # Realm/configuration ownership, not a second backend
    ├── Dockerfile
    ├── realm/user-service-realm.json
    ├── scripts/{iam,contract,native}.sh
    ├── scripts/{start.sh,policy.env}
    └── tests/contract-test.sh
```

No mpl-infra, microservices, business modules, new application database/datasource, database architecture redesign or knowledge-base framework was introduced. `user_service_db` and `keycloak_db` retain their ownership and names. Existing JPA mappings, business transactions, UUIDs, API payloads and authentication flows remain intact. Original source had no Git metadata; the two siblings are independent local Git repositories with no remote configured. See EXTRACTION-CHANGES.md for the exact file inventory.

## Build and verify a release

Commands below run from **mpl-be**, with the sibling repositories next to it. Do not replace an existing `.env`. For a new local environment, copy the example once and configure secrets independently. Never put secrets in image build arguments.

```sh
(cd ../mpl-db-migrations && ./mvnw -B -ntp clean verify)
(cd ../mpl-iam && bash tests/contract-test.sh)
(cd ../mpl-iam && bash scripts/iam.sh validate)
docker build -t mpl-db-migrations:1.0.0 ../mpl-db-migrations
docker build -t mpl-iam:1.0.0 ../mpl-iam
./mvnw -B -ntp clean verify
node --test src/test/js/swagger-login.test.cjs
bash scripts/tests/operations-test.sh
bash scripts/release.sh validate
docker build -t mpl-be:1.0.0 .
# Equivalent optional sibling builds:
docker compose -f docker-compose.yml -f compose.build.yaml --profile iam build
# Disposable full-stack acceptance; never uses the existing dotenv file or local databases:
bash scripts/acceptance.sh
```

Fast unit suite: `./mvnw -B -ntp test`. Database integration only, after compilation/image build: `./mvnw -B -ntp -Dit.test=UserPersistenceIT verify`. Migration image may be overridden with `-Dmigration.image=REGISTRY/mpl-db-migrations:TAG`; the release validator must agree before promotion. Testcontainers requires Docker and fails when it is absent. Real Keycloak lifecycle testing requires a disposable server and the acceptance harness sets both opt-in flags. Spring Boot manages Jupiter 6.0.3; MockMvc uses SpringJUnitWebConfig and persistence tests use SpringBootTest. Only the custom initializer owns external container/migration setup; Spring owns the application context lifecycle.

The manifest uses the JSON subset of YAML to allow validation with jq without a separate YAML parser. It records explicit component versions, Keycloak/config-cli compatibility, five required schema changes, realm/audience/client assumptions, tested local image IDs and verification evidence. Local tags are not registry immutability guarantees. Promote the **same tested images** to a registry, pin their digest, scan them and retain build/test provenance; do not rebuild a release tag and call it the same artifact. The deployment runner checks all five local image IDs and the running database/Keycloak container image IDs against tested evidence. No registry push or production deployment is performed here.

## Repeatable integration startup

```sh
docker compose config --quiet
docker compose up -d --wait app-db keycloak
# Read-only/preflight and SQL preview:
docker compose run --rm migration verify-history
docker compose run --rm migration validate
docker compose run --rm migration updateSQL > migration-review.sql
# After SQL review and backup/recovery readiness:
docker compose run --rm migration update
docker compose run --rm migration verify
# Only for an explicitly reviewed IAM change or first realm creation:
MPL_IAM_APPLY_APPROVED=true docker compose run --rm keycloak-config
docker compose run --rm iam-contract
docker compose up -d --wait api
```

The API depends on healthy PostgreSQL, successful independent migration and successful read-only IAM contract jobs. The IAM configuration job is under an explicit `iam` profile and never runs implicitly during API/Keycloak startup. A fresh realm fails the read-only contract until configured explicitly. Keycloak waits for its own database. Internal DNS (`app-db`, `keycloak-db`, `keycloak`) is used for service connections; the public issuer remains the browser-visible URL.

The database volumes are persistent. Bootstrap scripts run only on fresh volumes; no password rotation, volume reset or old-user cleanup is performed by startup. Container memory/heap limits bound local usage. Local Compose uses HTTP and local profile; it is not a production topology. Do not publish the Admin API, database or metrics to an untrusted network. Existing local ports can be overridden with APP_DB_PORT, KEYCLOAK_PORT and APP_PORT; keep KEYCLOAK_PUBLIC_URL and SWAGGER_PUBLIC_URL consistent.

## Protected smoke and monitoring

Obtain an **access token** for an existing application profile through the unchanged authorization-code/PKCE flow. Export it privately as SMOKE_ACCESS_TOKEN; never put it in command-line arguments or logs.

```sh
API_URL=http://localhost:8080 bash scripts/smoke.sh
```

The script checks readiness, anonymous 401, and authenticated GET `/api/v1/users/me` without modifying profile state. The disposable acceptance additionally tests registration replay, real PKCE, issuer/audience/role compatibility, USER rejection on administrator routes, real Keycloak lifecycle and reconciled journals. It uses unique project labels, new credentials/ports and deletes only its own test resources after collecting restricted logs. It never enables password grant on the frontend client.

Actuator `/actuator/metrics` and `/actuator/metrics/{name}` require an ADMIN bearer token. Existing business authorization is unchanged. Restrict network access and use an approved collector; no user IDs or secret payloads are metric tags. Inspect:

- `user.identity.sync.pending` and `user.identity.provisioning.incomplete` (gauges).
- `user.identity.sync.retry.attempts` (sum of pending row retry counts).
- `user.identity.sync.oldest.seconds` (oldest pending event age).
- `user.identity.sync.attempts`, `user.identity.sync.failures` (counters).
- `user.identity.sync.delivery.duration` (timer including transaction/lock wait).
- `user.identity.sync.lock.wait` (row lookup/lock acquisition timer, not a pure database lock-only measurement).
- `user.identity.sync.latency` (event age recorded after successful commit).
- `user.identity.keycloak.failures` (HTTP/IO/interruption counters, including bounded retry failures).

Pending gauges perform aggregate SQL when scraped; choose a sensible scrape interval and alert on sustained backlog/age. Incomplete provisioning still requires original caller/operator replay. No exactly-once guarantee, automatic identity deletion, lease worker or journal purge is added. Controlled two-worker tests observe PostgreSQL lock waits and final-state correctness; they are not throughput/load benchmarks or production latency certification.

## Release execution

1. Build immutable artifacts and retain their digests/provenance.
2. Run unit, PostgreSQL, IAM contract and disposable full-stack acceptance.
3. Review the manifest's exact API/schema/IAM combination; do not add an old API to rollback allowlists without evidence.
4. On the target with infrastructure already healthy, validate history/changelog and preview SQL. Preserve the restricted SQL review artifact.
5. Verify independent application/Keycloak backups and restore procedures; record backup and recovery references.
6. Run migration, verify no required changes remain, then explicitly apply IAM only if needed.
7. Verify live IAM contract, deploy the compatible API, wait for readiness, run a protected read-only smoke and inspect synchronization/provisioning metrics.
8. Store per-stage outcomes and the manifest hash with the release record. A failed stage stops subsequent deployment; it never triggers DB/IAM rollback.

```sh
bash scripts/release.sh preview --env-file /secure/platform.env --project MPL_PROJECT
# The following is an operator deployment command, not executed against production in this task:
bash scripts/release.sh deploy --env-file /secure/platform.env --project MPL_PROJECT \
  --execute --sql-reviewed --backup-reference VERIFIED_BACKUP --recovery-reference VERIFIED_RESTORE_PLAN
# Add --apply-iam only for reviewed IAM changes. SMOKE_ACCESS_TOKEN must already be exported.
```

Preview runs only jobs with `--no-deps`; it does not start infrastructure or mutate schema/IAM. Deployment requires matching tested images, PASS_LOCAL acceptance evidence, explicit SQL review, backup/recovery references and a smoke token. References are operator attestations, not an automated restore rehearsal. Results and SQL/logs are saved privately in `.local/releases/`; do not publish secret-bearing logs or raw Compose config.

## Three distinct recovery paths

**API rollback:** Select a previously verified image compatible with the currently applied schema and IAM. The manifest's `verifiedApiRollbackImages` is empty because no previous API/schema/IAM combination was tested. `release.sh rollback-api --execute --api-image ...` therefore refuses unverified images. Allowlisted rollback images must use immutable digest references. After adding real evidence through review, it checks schema and IAM, replaces only the API (`--no-deps`), waits for health, and runs the same smoke. Migration or IAM jobs are not reversed.

**Database rollback:** Run `docker compose run --rm migration rollbackSQL COUNT`, review all SQL and its SHA-256, establish a verified backup/recovery point and stop incompatible writers. The independent runner requires `MPL_ROLLBACK_APPROVAL=rollback:COUNT:user_service_db`, `MPL_BACKUP_REFERENCE` and `MPL_ROLLBACK_SQL_SHA256` to execute `rollback COUNT`. With Compose, pass them explicitly using `run -e VARIABLE ...`. A changed SQL plan is rejected. Do not roll back 005 while this API is running: registration requires that schema. Rollback cannot restore dropped/deleted data. Prefer a reviewed forward correction or database recovery where appropriate.

**IAM rollback/recovery:** Review a corrective IAM configuration or supported Keycloak administrative restore separately. Preserve existing secrets and encrypted remote state; check actual permission changes. `no-delete` prevents pruning, not every harmful update. Never edit Keycloak tables or use application Liquibase on keycloak_db. API startup does not import, overwrite or recreate the populated realm.

Use expand-contract: add compatible structures, roll out writers/readers, verify both old and new APIs, then retire old structures in a separately reviewed release. Destructive contract changes need explicit backup and recovery planning and remove affected API rollback candidates.

## Trade-offs and limits

| Change | Benefit | Cost / remaining limitation |
|---|---|---|
| Independent migrations | Separate credentials, schema jobs/rollback without API restart | Extra repository/image and API/schema coordination; incompatible rollback remains dangerous |
| Independent IAM | Separate configuration ownership, review and validation | Separate releases/recovery; erroneous configuration can break login |
| Main Compose in backend | Reproducible integration with explicit readiness/order | Multiple images, resource use, local-network configuration; no production topology claim |
| JUnit 6 and real integration tests | Standard Spring lifecycle and real PostgreSQL/IAM regressions | Slower tests, Docker/image prerequisites, environment maintenance |
| Sync verification and metrics | Recovery/concurrency evidence, safer diagnosis | Aggregate scrape overhead; row locks still span bounded HTTP calls; eventual consistency remains |
| Release manifest and gates | Traceable version composition and deliberate recovery | Extra stages, provenance and backup obligations; old-version rollback needs separate evidence |

SMTP delivery, production backup restore/PITR, capacity/load testing, remote registry promotion, production TLS/topology and prior-API compatibility are not certified by this local verification. Existing local data/secrets are preserved. See VERIFICATION.md for executed results and any remaining blockers.

## Dependency references

The installed Boot 4.0.9 BOM is the authoritative version source for this build. Upstream references used during review: [Spring Boot testing](https://docs.spring.io/spring-boot/reference/testing/), [Liquibase search-path and stable identity](https://docs.liquibase.com/community/reference-guide-5-0/parameters/search-path), [config-cli 6.5.1 release](https://github.com/adorsys/keycloak-config-cli/blob/v6.5.1/CHANGELOG.md), and [its Docker entry point](https://github.com/adorsys/keycloak-config-cli/blob/v6.5.1/Dockerfile). Actual compatibility evidence comes from the local tests, not only these descriptions.

The Java/JUnit PKCE acceptance driver permits Keycloak Secure cookies only on its disposable loopback fixture. Cookies remain restricted to the login host/path, and form submissions must retain the same scheme, host and port. Redirects are inspected without following them. Transport unit tests cover these boundaries. This is a protocol smoke test, not browser rendering coverage.
