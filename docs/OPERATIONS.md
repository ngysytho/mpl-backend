# Operations and recovery

## Database and migration isolation

The two Compose database services have independent volumes, databases and credentials. Only the application database has the application bootstrap grants. `app_migrator` owns application objects; `app_runtime` has DML only. Keycloak logs in as its own non-superuser schema owner and performs its own migrations. Bootstrap `postgres` credentials must not be delivered to the API or Keycloak runtime.

After migration, a database administrator can restrict application tracking metadata further:

```sql
REVOKE ALL ON databasechangelog, databasechangeloglock FROM app_runtime;
```

Never point Liquibase at Keycloak, manually edit Keycloak tables, enable Hibernate schema creation, or set Liquibase drop-first. Run the independent migration validate/updateSQL/update/verify sequence from DEPLOYMENT.md using an isolated credential. Snapshot/back up before a migration and verify restore procedures. If Liquibase reports a lock, first prove the owning process has stopped; do not release a live migration lock to force another migration through.

## Incomplete creation

Inspect only the **application** journal with a restricted operator role:

```sql
SELECT id, email, created_at, user_id FROM user_provisioning_requests
WHERE user_id IS NULL ORDER BY created_at;
```

The original caller should replay the POST with the exact normalized body and original UUID key. The hash verifies the payload; it is not reversible, so the server cannot reconstruct phone/avatar/name from the journal alone. Never generate a new key to work around a conflict. If the payload is unavailable, pause that provisioning attempt and reconstruct it from an authorized source with an operator-reviewed procedure.

Find the Keycloak identity through the supported Admin API by exact username. Confirm `mplProvisioningId` equals the journal UUID before considering it related. The marker is admin-only in the declarative user profile. Check no profile is already linked. A timeout can mean the remote create succeeded; repeat lookup before deciding it did not.

If identity is owned and disabled, replay the original POST. If it is unrelated, do not adopt or delete it. Correct the external conflict through an explicit reviewed IAM decision. The API returns 409 and leaves the reservation in place. Do not clear a reservation until an operator has verified identity ownership, profile links and outstanding requests; retention is intentionally conservative. No automated journal cleanup is shipped.

If an owned disabled orphan must be permanently erased, use the supported Admin API under a separately approved erasure procedure and audit it. The integration deletion method checks the ownership marker, but it is not exposed by the user API. Never compensate by deleting an arbitrary identity found only by email.

## Pending synchronization

```sql
SELECT user_id, attempts, created_at, available_at FROM user_identity_sync ORDER BY created_at;
```

Restore Keycloak connectivity/service-account permissions/role declarations. The worker retries automatically. A repeated PUT, status change, or DELETE also makes an immediate delivery attempt. Investigate long-lived rows and high retry counts. Do not delete outbox rows to suppress an alert; that discards required desired state. Deliveries are serialized per profile, and read the newest state under lock.

`INACTIVE`/`SUSPENDED` blocks API access immediately through the database status check even if Keycloak disable is pending. Other APIs using the same identity must independently apply their access policy. Access tokens expire after five minutes; logout is not universal JWT revocation.

## External drift

Do not change application-owned email/display-name/status directly in Keycloak. The application overwrites display name/enabled state when a sync task is delivered, but does not continuously scan every Keycloak identity. If an operator changed one, issue the corresponding application PUT/status request to restore desired state. A deleted identity requires reviewed repair; the worker deliberately does not silently create a replacement identity or change its UUID. Email changes are not supported by this release and need a dedicated verified-email migration/workflow.

## Development seed

The optional Liquibase seed requires a real Keycloak identity created and disabled through Admin REST/Console first. Its username/email must be `developer@example.test`, its display name `Development User`, and it must have USER role. Record its UUID. No application migration creates identities.

Set `KEYCLOAK_DEV_SEED_USER_ID` to that UUID. Run mpl-db-migrations with `MIGRATION_CONTEXTS=local,development-seed` and `MPL_DEVELOPMENT_SEED_APPROVED=true` to opt in. API startup never invokes migrations. The fixed seed application UUID is `a96e4262-0694-4cf6-b480-05f4aff87355`; status is INACTIVE, so it cannot log in to this API. Use an ADMIN status request to activate it and synchronize the already-existing identity. Normal local, test, and production contexts exclude this seed; its context starts with `@`, requiring explicit selection. Never enable development-seed in production. The seed is not a substitute for integration-test fixtures.

## Production rollout order

1. Back up both databases independently; ensure old/new application versions tolerate schema expansion.
2. Apply application migrations to `user_service_db` using the migrator identity.
3. Apply reviewed IAM changes using the configuration automation identity. For the first deployment only, bootstrap this identity through the Keycloak administrative process.
4. Roll out API instances with profile `prod`, runtime database login, HTTPS public issuer, internal JWKS/Admin URLs, and only the runtime service secret.
5. Confirm readiness, execute a protected API smoke test through PKCE, and observe pending sync/incomplete provisioning metrics.
6. Remove temporary bootstrap admin access, retain controlled break-glass access, and archive deployment/audit evidence.

Keycloak `start` in local Compose is not an optimized production image. Production should build its DB/health/metrics options into a pinned image, then run `start --optimized`; configure HTTPS directly or a trusted TLS reverse proxy, public hostname and trusted proxy addresses explicitly. Keep management and Admin API network access restricted. Use secret files/secret-manager injection. Set container limits and multiple replicas only with sufficient DB pool capacity for bounded external calls.

## Logs and audit

Success responses and request errors carry correlation IDs. Audit logs contain verified actor subject, action and application UUID, not names/emails/passwords/tokens. Ship these to an access-controlled append-only sink. Synchronization failures log application UUID and exception type only; do not enable HTTP wire logs or config-cli realm-config TRACE in production. The current log-based audit is not a transactional regulatory audit ledger; use a separately specified durable audit sink if that guarantee is required.

For config-cli, `no-delete` prevents destructive pruning but does not revoke privileges removed from a merge list. Review explicit role revocations and credential rotation separately. Preserve the remote-state encryption key across reapplications; rotate it via a documented migration, not an arbitrary environment change.

## Release coordination and metrics

See [DEPLOYMENT.md](DEPLOYMENT.md) for the release manifest, guarded commands, migration SQL previews and separate API/DB/IAM recovery procedures. Actuator metrics require ADMIN authority; metric definitions, pending-age and retry monitoring are listed there. Hibernate JDBC error detail logging is disabled to avoid leaking rejected row values; application error mapping and synchronization logs remain sanitized.
