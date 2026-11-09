# mpl-be

Operational scripts use Bash (3.2+), jq and curl; Java 21 runs the application, migration runner and integration tests. Python is not required. Run scripts with `bash scripts/<name>.sh`.

MPL Platform's full Java 21 / Spring Boot 4.0.9 **modular monolith backend**. User is the first business module; future modules belong in this application. PostgreSQL/JPA business persistence and Keycloak Admin REST integration remain here.

Schema migration ownership is in sibling **mpl-db-migrations** (Liquibase 5.0.4). Realm/client/role configuration ownership is in sibling **mpl-iam** (Keycloak 26.5.5, config-cli 6.5.1-26.5.5). There is no infrastructure repository or second backend. API startup never runs migrations or imports IAM configuration; Hibernate always validates.

See [current verification](docs/VERIFICATION.md), [baseline](docs/EXTRACTION-BASELINE.md), [release/rollback guide and exact commands](docs/DEPLOYMENT.md), [native setup](docs/LOCAL-SETUP.md) and [file inventory](docs/EXTRACTION-CHANGES.md). Historical 35-test/local results are not the current acceptance record.

## Local startup

Keep the existing `.env`, PostgreSQL data and installed Keycloak unchanged. From this directory:

```sh
(cd ../mpl-db-migrations && ./mvnw -B -ntp package)
bash ../mpl-db-migrations/scripts/migrate.sh --env-file .env verify-history
bash ../mpl-db-migrations/scripts/migrate.sh --env-file .env validate
bash ../mpl-db-migrations/scripts/migrate.sh --env-file .env updateSQL
# Review SQL; run migration before API startup:
bash scripts/local.sh migrate
MPL_ENV_FILE="$PWD/.env" bash ../mpl-iam/scripts/start.sh
# In another terminal:
./mvnw spring-boot:run
```

A populated realm is not reapplied on startup. Apply only an explicitly reviewed IAM change:

```sh
MPL_IAM_APPLY_APPROVED=true bash scripts/local.sh configure
```

The optional background `bash scripts/local.sh start` starts Keycloak, runs the independent migration, verifies completion and starts the packaged API; it never applies IAM. `api` verifies migrations first. The packaged API receives only runtime configuration; native `spring-boot:run` retains the existing local dotenv import for convenience. Keep deployment job secrets separate in production.

## Docker integration and tests

```sh
docker build -t mpl-db-migrations:1.0.0 ../mpl-db-migrations
docker build -t mpl-iam:1.0.0 ../mpl-iam
./mvnw -B -ntp verify
docker build -t mpl-be:1.0.0 .
docker compose up -d --wait app-db keycloak
# Explicit initial/reviewed configuration:
MPL_IAM_APPLY_APPROVED=true docker compose run --rm keycloak-config
docker compose up -d --wait api
# Separate disposable acceptance, never the existing local environment:
bash scripts/acceptance.sh
```

API startup waits for successful independent migration and read-only IAM contract jobs. Production release SQL/backup review is described in DEPLOYMENT.md. Database volumes persist; no reset is part of normal startup. Use the optional compose.build.yaml to build from siblings. Secrets are injected at runtime. Images have explicit versions, and release-manifest.yaml coordinates the tested combination.

`./mvnw test` runs fast Java tests. `./mvnw verify` also runs PostgreSQL Testcontainers with the external migration image. Boot manages JUnit Jupiter 6.0.3, Framework 7.0.10 and Testcontainers 2.0.5; Spring now owns test context lifecycle. ArchUnit and existing behavioral coverage are retained. The API artifact contains no Liquibase library or changelog. Authentication remains authorization code + PKCE with issuer/audience validation and USER/ADMIN mapping.

## Identity ownership and lifecycle

| Data | Owner / rule |
|---|---|
| Passwords, MFA, sessions, token issuance, roles | Keycloak only |
| Application UUID, phone, avatar, timestamps, account state | Application PostgreSQL |
| Email / username | Normalized on creation; immutable in this initial API |
| Display name | Application authoritative; mirrored to Keycloak `firstName`, `lastName` empty |
| `keycloakUserId` | Immutable unique link to the verified JWT `sub` |
| `mplProvisioningId` | Admin-only Keycloak attribute identifying the durable provisioning request |

Keycloak account UI cannot modify email, username, display name, or the ownership marker. Do not change shared fields directly in the Admin Console for application-owned users; normal profile updates go through this API. A privileged external administrator can still cause drift, so investigate such changes through the recovery procedure. Email changes require a future verified-email workflow and are intentionally rejected as unknown request fields.

`ACTIVE` means the desired Keycloak `enabled` value is true. `INACTIVE` and `SUSPENDED` both disable the identity and terminate its sessions. Only an ADMIN can change status. Transitions among these states are allowed, including reactivation. `DELETE` is idempotent deactivation: **no profile or identity is erased, and email remains reserved**. It changes a suspended account to inactive; an ADMIN may reactivate it. The low-level owned-identity deletion method is reserved for separately reviewed erasure/testing, never invoked by REST deletion or automatic compensation.

JWTs already issued remain cryptographically valid until expiry after Keycloak disable/logout. The API therefore also checks the application status on authenticated API requests and rejects inactive/suspended profiles immediately. External consumers must enforce their own equivalent revocation policy. Keycloak-managed administrator identities without an application profile can administer users; `/me` for an unlinked identity returns 404. No just-in-time linking by untrusted email is performed.

## Provisioning, idempotency and synchronization

`POST /api/v1/users` requires a caller-generated UUID `Idempotency-Key`. Store and reuse that key with the identical normalized request until the operation completes.

1. Validate and normalize all fields before external I/O.
2. Commit a journal reservation keyed by the idempotency UUID and a SHA-256 payload fingerprint. Unique email reservation prevents different keys from racing for the same email.
3. In a separate transaction, lock the journal row. Exact username/email lookup rejects unrelated identities. Recovery adopts an identity only if its admin-only ownership marker matches this journal key and its email matches.
4. Create a disabled Keycloak identity **once per attempt**; the POST is never blindly retried. Timeouts are ambiguous and recovered by exact lookup on the next API retry.
5. Commit the application profile, outbox task, and journal completion together.
6. After commit, synchronize the display name and USER role, then enable the identity. Return 201, including on an idempotent replay.

There is a database transaction around step 3–5 to serialize journal access; Keycloak remains an external side effect and is **not** rolled back by it. The reservation from step 2 survives. Bounded HTTP timeouts and database lock/transaction timeouts limit this lock-holding strategy. This deliberately trades throughput for clear single-user correctness; load-test and size pools before large provisioning batches.

If persistence fails after identity creation, the identity remains disabled and the committed journal remains incomplete. Retry the **original key and body**. No automatic deletion is attempted, so an unrelated identity cannot be compensated away. Incomplete create requests require caller/operator retry; the background worker only delivers profiles that have already committed.

Profile/status changes commit the new desired state and an outbox task atomically. A synchronous attempt follows; if it fails, the API returns **503 `IDENTITY_SYNC_PENDING`**, `Retry-After: 10`. This means the profile change **has committed**, not that it rolled back. Read the profile, or retry the same PUT/status/DELETE. A worker retries with bounded backoff (up to five minutes). It locks the profile and reads its latest state so an old event cannot re-enable a newly suspended user. Multiple instances serialize via database locks; network calls can repeat and are idempotent.

Observe `user.identity.sync.pending` and `user.identity.provisioning.incomplete` metrics via a private meter exporter, plus structured synchronization logs. Default public Actuator exposure is only health/info; info is denied by the security chain. See [operations and recovery](docs/OPERATIONS.md).

## Authentication and authorization

The frontend uses realm `user-service`, client `user-service-frontend`, Authorization Code flow with **PKCE S256**, exact callback `/auth/callback`, `state` and OIDC `nonce`. Never embed a client secret in the frontend. Direct password grant, implicit flow, and Keycloak browser self-registration are disabled; the application provides its own registration endpoint. The API has no password login or JWT issuance endpoint.

The frontend sends `Authorization: Bearer <access_token>`. The API checks RS256 signature via JWKS, expiry, issuer, required subject and audience `user-service-api`. It maps only `USER` and `ADMIN` from realm roles and this resource client's roles to `ROLE_USER` / `ROLE_ADMIN`; roles belonging to unrelated clients are ignored.

The public issuer remains `http://localhost:8081/realms/user-service` locally, even from inside Docker. JWKS and Admin API calls use `http://keycloak:8080` internally. Production issuer/frontend URLs must be HTTPS and are checked on startup. If public hostnames change, update Keycloak hostname, issuer, redirect URI, origins, and audience configuration together.

Create the first human application administrator via the Keycloak Admin Console using the temporary bootstrap account, then assign **realm ADMIN** explicitly. This account does not need a profile to manage application users. Use its browser OIDC login to obtain an API access token. Newly provisioned users get USER only. Retire bootstrap admin credentials and require MFA for human administrators before production.

The runtime backend authenticates to Admin REST using its confidential service-account client and client credentials. Its built-in realm-management roles are `manage-users`, `view-users`, `query-users`, `view-clients`, `view-realm`, and `view-realm`; it has no `realm-admin`, `manage-realm`, or `manage-clients`. Built-in `manage-users` is coarse and includes capabilities beyond this API's allowed operations. The client further allowlists USER role changes and never exposes role escalation routes. If your threat model requires server-enforced per-role restrictions against a compromised service credential, deploy/review Keycloak fine-grained admin permissions and rerun the real-Keycloak contract test. Do not describe built-in `manage-users` as per-user/per-role isolation.

## API

All success bodies except DELETE are `{ "data": ... }`. Errors are `application/problem+json` with `status`, `code`, safe `detail`, and a generated `traceId`. `X-Request-ID` correlates requests and logs. No tokens, secrets, credentials, upstream error bodies or request bodies are logged.

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/users` | ADMIN + UUID Idempotency-Key |
| GET | `/api/v1/users` | ADMIN |
| GET | `/api/v1/users/{id}` | ADMIN |
| PUT | `/api/v1/users/{id}` | ADMIN |
| PATCH | `/api/v1/users/{id}/status` | ADMIN |
| DELETE | `/api/v1/users/{id}` | ADMIN; deactivate, 204 on successful sync |
| GET | `/api/v1/users/me` | Authenticated subject's profile |
| PATCH | `/api/v1/users/me` | Authenticated subject's mutable profile fields |

Create:

```sh
curl -i -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: $REQUEST_UUID" \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Nguyen Van A","email":"a@example.com","phoneNumber":"+84901234567"}'
```

Admin creation does not accept a password or send an email automatically. Configure SMTP, then use Keycloak Admin Console **Send execute actions email** with `UPDATE_PASSWORD` to onboard the user. The integration client also implements `sendActions` for a future explicitly authorized workflow. Email delivery is not retried automatically. This avoids repeated onboarding emails on POST retries.

PUT replaces `fullName`, `phoneNumber`, `avatarUrl` (name required; optional fields omitted/null clear them). PATCH `/me` merges provided fields under a row lock. Omitted/null fields are retained; use `clearPhoneNumber: true` or `clearAvatarUrl: true` to clear them. Avatar must be an absolute HTTPS URL and is stored only; the backend never fetches it.

```json
{"fullName":"New Name","clearPhoneNumber":true}
```

Status body: `{"status":"SUSPENDED"}`. Profile, email, and keycloakUserId cannot be supplied to change authorization. Unknown JSON fields are rejected.

List example:

```http
GET /api/v1/users?keyword=nguyen&status=ACTIVE&page=0&size=20&sort=createdAt,desc
```

Supported predicates: keyword across name/email, `fullName`, `email` (case-insensitive substring), status, inclusive `from`/`to` ISO-8601 creation timestamps. SQL wildcard characters in search are escaped as literals. Only `fullName`, `email`, `createdAt` are accepted sort fields; direction must be `asc`/`desc`. A UUID tie-breaker stabilizes ordering. Page is zero-based, size 1–100. Response pagination includes content/page/size/totalElements/totalPages. Search is database-side; broad substring searches can scan large tables. Introduce a reviewed PostgreSQL trigram migration when measured data volume warrants it.

## Schema and IAM workflows

See [migration repository](../mpl-db-migrations/README.md), [IAM repository](../mpl-iam/README.md), and [deployment guide](docs/DEPLOYMENT.md). Every applied migration identity/checksum is checked before update or rollback. No clearCheckSums/changelogSync workaround is exposed. IAM apply uses the retained no-delete/merge policy and requires an explicit reviewed action.

## Configuration variables

| Variables | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` for local runtime/docs, `prod` for HTTPS deployment/private docs |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` | Application runtime database connection |
| `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD` | Independent migration job only |
| `APP_DB_ADMIN_PASSWORD`, `KEYCLOAK_DB_ADMIN_PASSWORD` | Container DB bootstrap only |
| `KEYCLOAK_DB_PASSWORD` | Keycloak database owner login only |
| `KEYCLOAK_PUBLIC_URL` | Keycloak public base URL / hostname setting |
| `KEYCLOAK_ISSUER_URL` | Exact public realm issuer expected in JWTs |
| `KEYCLOAK_JWKS_URL` | Reachable JWKS endpoint, may use internal DNS |
| `KEYCLOAK_ADMIN_URL`, `KEYCLOAK_REALM` | Internal Admin REST base and realm |
| `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` | Runtime confidential service-account credentials |
| `JWT_AUDIENCE` | Resource audience; coordinate changes with realm config |
| `FRONTEND_URL`, `CORS_ORIGINS` | Exact browser callback origin / comma-separated allowed origins |
| `KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME`, `KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD` | Local/initial provisioning only |
| `KEYCLOAK_STATE_ENCRYPTION_KEY` | Stable encryption key for config-cli remote state |
| `SMTP_*` | Verification/reset email transport |
| `APP_PORT`, `LOG_LEVEL`, `APP_LOG_LEVEL` | HTTP port and operational logging |
| `IDENTITY_SYNC_ENABLED`, `IDENTITY_SYNC_DELAY_MS` | Worker control (enabled by default) |

Spring external configuration also supports config trees/secret files. Use your platform secret manager rather than committing dotenv files. Defaults in application-local are intentionally local; Compose fixes realm/client/audience names to the delivered realm file. Change both code configuration and realm declarations together if renaming those identifiers.

## Tests and acceptance commands

Exact build, unit, PostgreSQL, real Keycloak, Compose and read-only smoke commands are in [DEPLOYMENT.md](docs/DEPLOYMENT.md). `RealKeycloakIT` requires both explicit opt-in and disposable-environment acknowledgement; prefer the isolated Compose acceptance harness. Never run destructive lifecycle tests against an existing local/production realm.

## Production deployment

Compose is a persistent **local environment**, not a complete production topology. It uses Keycloak `start`, not `start-dev`, but explicitly permits local HTTP. In production, deploy pinned images behind a controlled HTTPS ingress (or terminate TLS directly in Keycloak), set a stable HTTPS hostname, restrict internal Admin/JWKS/database network access, configure trusted proxy addresses if using proxy headers, and do not expose management ports publicly.

Use `application-prod.yml`; startup validates the public issuer/origins are HTTPS and Hibernate validates the migrated schema. Run migrations first with an isolated job identity; apply IAM config in a separate authorized job; then roll out the API with only runtime credentials. Configure Keycloak build-time options in an optimized image for production, PostgreSQL backups/PITR, SMTP, MFA for admins, secret rotation, resource limits, readiness/liveness probes, rate limits at ingress, and an audit log sink. Restrict outbound network traffic; avatar URLs are data, not fetch targets.

Do not grant ADMIN by default. Keep IAM configuration credentials separate from runtime identity-management credentials. Scan pinned dependencies/images and review upgrades regularly; pin by digest in your release pipeline after verification. External IAM changes, emails and multiple databases do not share a transaction. Pending synchronization and incomplete provisioning must be monitored and reconciled, not hidden behind a success response.

Read [architecture decisions](docs/ARCHITECTURE.md) and [operations/recovery](docs/OPERATIONS.md) before production rollout.

### Đăng ký tài khoản

Gửi `POST /api/v1/users/register` không cần token, với header `Idempotency-Key` là một UUID. Ví dụ:

```bash
curl -X POST http://localhost:8080/api/v1/users/register \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 1d5e4489-e06b-4bdb-bec4-90dc9509f876' \
  -d '{"fullName":"Nguyen Van A","email":"a@example.com","password":"Example-Password-42!"}'
```

Dùng UUID mới cho mỗi lần đăng ký; khi thử lại cùng yêu cầu thì giữ nguyên UUID. Mật khẩu đầu tiên đã được Keycloak chấp nhận sẽ được giữ nguyên khi thử lại. Keycloak lưu mật khẩu; database ứng dụng chỉ lưu hồ sơ. Tài khoản nhận vai trò USER và không yêu cầu xác minh email. Các tài khoản đã tạo trước đó có thể vẫn cần gỡ required action `Verify Email` trong Keycloak. Apply lại cấu hình realm bằng keycloak-config-cli hoặc tắt Verify email trong Realm settings → Login. API giới hạn mặc định 10 lần đăng ký mỗi giờ cho mỗi địa chỉ kết nối; cấu hình bằng `REGISTRATION_MAX_ATTEMPTS_PER_HOUR`. Khi chạy sau reverse proxy cần cấu hình giới hạn ở ingress theo địa chỉ client đáng tin cậy.


Swagger login: open `/swagger-ui/index.html` (redirects to `/docs/index.html`), choose **Authorize → keycloak**, and sign in. The UI uses a public PKCE client and refreshes an expiring access token before sending API requests. Concurrent requests share one refresh operation. Tokens stay in memory; reloading the page requires authorization again. Refresh failure stops the API request and requires reauthorization when the session expires. Manual bearer tokens do not refresh. Set `SWAGGER_PUBLIC_URL` to the exact API origin when applying realm configuration. Production documentation remains disabled by default.
