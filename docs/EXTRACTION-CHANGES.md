# Extraction file inventory — 2026-11-09

The original mpl-be directory had no Git metadata. This inventory compares SHA-256 hashes against the pre-edit snapshot in `/tmp/mpl-extraction-baseline/files.json`; generated build outputs, caches and secret/state files are excluded. Sibling repositories were created locally with no remotes or commits. No existing `.env` or user data was replaced.

## Bash/Java replacement

Removed the operational Python sources: backend local/release/smoke/acceptance plus two Python test files; migration migrate.py; IAM iam/contract/native plus its Python test file. Their replacements are Bash scripts and jq validators; no Python interpreter is installed in the IAM image. Ignored personal scratch files are not deployment inputs.

Added backend scripts/lib.sh, scripts/release.jq, scripts/FreePorts.java and ComposeSmokeIT.java. Added scripts/lib.sh to each sibling and scripts/validate.jq to IAM. Added deployment/verification-no-python-2026-11-09.json for the new artifact evidence. The old verification JSON remains historical.

## Ownership moves

- `mpl-be/src/main/resources/db/changelog/**` → `mpl-db-migrations/src/main/resources/db/changelog/**`: six actual changeset files byte-identical. Master only removes the invalid nonexistent `inclide` entry; valid include order, preconditions and effective paths remain unchanged.
- `mpl-be/keycloak/realm/user-service-realm.json` → `mpl-iam/realm/user-service-realm.json`: byte-identical.
- `mpl-be/keycloak/start.sh` → `mpl-iam/scripts/start.sh`: explicit dotenv location added for sibling/native workflows.
- `mpl-be/keycloak/README.md` → `mpl-iam/README.md`: ownership, validation, application and recovery instructions updated.
- IAM-specific native lifecycle/config-cli policy moves from the former backend Python launcher into `mpl-iam/scripts/{native,iam}.sh` and `policy.env`. Backend launcher delegates to sibling tools.
- `LiquibaseConfig.java` becomes `DatabaseConfig.java`: target guard retained, runtime migration enablement and Hibernate DDL modes rejected.

## Added in mpl-be

- `scripts/local.sh`

- `compose.build.yaml`
- `deployment/release-manifest.yaml`
- `deployment/verification-2026-11-09.json`
- `docs/DEPLOYMENT.md`
- `docs/EXTRACTION-BASELINE.md`
- `docs/EXTRACTION-CHANGES.md`
- `docs/IDENTITY-VERIFICATION.md`
- `scripts/acceptance.sh`
- `scripts/release.sh`
- `scripts/smoke.sh`
- `src/test/java/com/example/userservice/integration/ComposeLoginTransportTest.java`
- `scripts/tests/operations-test.sh`
- `src/main/java/com/example/userservice/config/DatabaseConfig.java`
- `src/test/java/com/example/userservice/config/DatabaseConfigTest.java`
- `src/test/java/com/example/userservice/integration/TestDatabaseInitializer.java`
- `src/test/java/com/example/userservice/modules/user/repository/IdentitySyncMetricsTest.java`

## Modified in mpl-be

- `.dockerignore`
- `.env.example`
- `.gitignore`
- `Dockerfile`
- `README.md`
- `docker-compose.yml`
- `docs/ARCHITECTURE.md`
- `docs/LOCAL-SETUP.md`
- `docs/OPERATIONS.md`
- `docs/VERIFICATION.md`
- `pom.xml`
- `src/main/java/com/example/userservice/config/SecurityConfig.java`
- `src/main/java/com/example/userservice/integration/keycloak/KeycloakAdminClient.java`
- `src/main/java/com/example/userservice/modules/user/repository/IdentitySyncAdapter.java`
- `src/main/java/com/example/userservice/modules/user/repository/IdentitySyncTransaction.java`
- `src/main/java/com/example/userservice/modules/user/repository/UserSyncMetrics.java`
- `src/main/resources/application-local.yml`
- `src/main/resources/application.yml`
- `src/test/java/com/example/userservice/integration/RealKeycloakIT.java`
- `src/test/java/com/example/userservice/integration/UserPersistenceIT.java`
- `src/test/java/com/example/userservice/integration/keycloak/KeycloakAdminClientTest.java`
- `src/test/java/com/example/userservice/security/UserSecurityTest.java`
- `src/test/resources/application-test.yml`

## Removed from mpl-be

- `keycloak/README.md`
- `keycloak/realm/user-service-realm.json`
- `keycloak/start.sh`
- `src/main/java/com/example/userservice/config/LiquibaseConfig.java`
- `src/main/resources/db/changelog/changes/001-create-users-table.yaml`
- `src/main/resources/db/changelog/changes/002-create-user-indexes.yaml`
- `src/main/resources/db/changelog/changes/003-add-user-constraints.yaml`
- `src/main/resources/db/changelog/changes/004-create-provisioning-and-sync-journal.yaml`
- `src/main/resources/db/changelog/changes/005-add-self-registration-support.yaml`
- `src/main/resources/db/changelog/db.changelog-master.yaml`
- `src/main/resources/db/changelog/seed/001-development-seed.yaml`

## Added in mpl-db-migrations

- `.dockerignore`
- `.env.example`
- `.gitignore`
- `.mvn/wrapper/maven-wrapper.jar`
- `.mvn/wrapper/maven-wrapper.properties`
- `Dockerfile`
- `README.md`
- `mvnw`
- `mvnw.cmd`
- `pom.xml`
- `scripts/migrate.sh`
- `src/main/java/com/mpl/migrations/MigrationMain.java`
- `src/main/resources/db/changelog/changes/001-create-users-table.yaml`
- `src/main/resources/db/changelog/changes/002-create-user-indexes.yaml`
- `src/main/resources/db/changelog/changes/003-add-user-constraints.yaml`
- `src/main/resources/db/changelog/changes/004-create-provisioning-and-sync-journal.yaml`
- `src/main/resources/db/changelog/changes/005-add-self-registration-support.yaml`
- `src/main/resources/db/changelog/db.changelog-master.yaml`
- `src/main/resources/db/changelog/seed/001-development-seed.yaml`
- `src/test/java/com/mpl/migrations/MigrationIT.java`
- `src/test/java/com/mpl/migrations/MigrationSafetyTest.java`
- `tests/baseline-databasechangelog.csv`

## Added in mpl-iam

- `.dockerignore`
- `.gitignore`
- `Dockerfile`
- `README.md`
- `realm/user-service-realm.json`
- `scripts/contract.sh`
- `scripts/iam.sh`
- `scripts/native.sh`
- `scripts/policy.env`
- `scripts/start.sh`
- `tests/contract-test.sh`

## Preserved implementation

Controllers, request/response DTOs, entities, JPA mappings, repository contracts, UserService, UserTransactions, ProvisioningAdapter, registration behavior, frontend token handling and journal/outbox schema are unchanged. Existing production class edits are restricted to database guarding, metrics authorization, synchronization instrumentation, and counting Keycloak transport/HTTP failures. The transaction algorithm and retry/backoff statements remain unchanged. No new changeset was introduced.
