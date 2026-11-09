.formatVersion == 1 and
(.components | keys | sort) == ["backend","iam","keycloak","migrations","postgres"] and
all(.components[];
  (.image | test("^[a-zA-Z0-9./_-]+:[a-zA-Z0-9_.-]+(@sha256:[a-f0-9]{64})?$")) and
  (.image | split("@")[0] | split(":")[-1]) == .version and .version != "latest") and
.components.keycloak.version == "26.5.5" and
.components.iam.configCli == "6.5.1-26.5.5" and .components.migrations.liquibase == "5.0.4" and
.schema.database == "user_service_db" and
.schema.requiredChangesets == ["user-create-profile-table-001","user-profile-indexes-002","user-profile-invariants-003","user-provisioning-sync-journal-004","user-self-registration-support-005"] and
(.schema.verifiedApiRollbackImages | type) == "array" and
all(.schema.verifiedApiRollbackImages[]; test("^[a-zA-Z0-9./_:-]+@sha256:[a-f0-9]{64}$")) and
.iamContract == {realm:"user-service",audience:"user-service-api",runtimeClient:"user-service-admin",frontendClient:"user-service-frontend"}
