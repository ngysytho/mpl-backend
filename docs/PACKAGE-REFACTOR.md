# Package reorganization — 2026-11-05

Scope: folder/package reorganization only, following the user’s explicit request. Existing business classes and behavior are retained. No application, Maven build, tests, migration or Docker commands were run.

## Final Java layout

```text
com.example.userservice/
├── UserServiceApplication.java
├── config/
├── core/
│   ├── audit/
│   ├── exception/
│   ├── filter/
│   ├── response/
│   ├── security/
│   └── specification/
├── integration/
│   └── keycloak/
└── modules/
    └── user/
        ├── controller/
        ├── service/
        ├── repository/
        ├── entity/
        ├── dto/
        │   ├── request/
        │   └── response/
        ├── mapper/
        └── specification/
```

## Moved files

Paths are relative to the project root. Class names are unchanged.

| Previous path | New path |
|---|---|
| `src/main/java/com/example/userservice/modules/user/domain/repository/UserFilter.java` | `src/main/java/com/example/userservice/modules/user/repository/UserFilter.java` |
| `src/main/java/com/example/userservice/modules/user/domain/repository/UserRepository.java` | `src/main/java/com/example/userservice/modules/user/repository/UserRepository.java` |
| `src/main/java/com/example/userservice/modules/user/domain/model/User.java` | `src/main/java/com/example/userservice/modules/user/entity/User.java` |
| `src/main/java/com/example/userservice/modules/user/domain/model/UserStatus.java` | `src/main/java/com/example/userservice/modules/user/entity/UserStatus.java` |
| `src/main/java/com/example/userservice/modules/user/domain/exception/UserNotFoundException.java` | `src/main/java/com/example/userservice/modules/user/service/UserNotFoundException.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/specification/UserSpecifications.java` | `src/main/java/com/example/userservice/modules/user/specification/UserSpecifications.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/repository/UserSyncMetrics.java` | `src/main/java/com/example/userservice/modules/user/repository/UserSyncMetrics.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/repository/ProvisioningAdapter.java` | `src/main/java/com/example/userservice/modules/user/repository/ProvisioningAdapter.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/repository/IdentitySyncAdapter.java` | `src/main/java/com/example/userservice/modules/user/repository/IdentitySyncAdapter.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/repository/UserRepositoryAdapter.java` | `src/main/java/com/example/userservice/modules/user/repository/UserRepositoryAdapter.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/repository/IdentitySyncTransaction.java` | `src/main/java/com/example/userservice/modules/user/repository/IdentitySyncTransaction.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/repository/UserJpaRepository.java` | `src/main/java/com/example/userservice/modules/user/repository/UserJpaRepository.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/entity/UserJpaEntity.java` | `src/main/java/com/example/userservice/modules/user/entity/UserJpaEntity.java` |
| `src/main/java/com/example/userservice/modules/user/infrastructure/persistence/mapper/UserPersistenceMapper.java` | `src/main/java/com/example/userservice/modules/user/mapper/UserPersistenceMapper.java` |
| `src/main/java/com/example/userservice/modules/user/application/command/UpdateProfileCommand.java` | `src/main/java/com/example/userservice/modules/user/dto/request/UpdateProfileCommand.java` |
| `src/main/java/com/example/userservice/modules/user/application/command/CreateUserCommand.java` | `src/main/java/com/example/userservice/modules/user/dto/request/CreateUserCommand.java` |
| `src/main/java/com/example/userservice/modules/user/application/service/IdentityManagementPort.java` | `src/main/java/com/example/userservice/modules/user/service/IdentityManagementPort.java` |
| `src/main/java/com/example/userservice/modules/user/application/service/UserService.java` | `src/main/java/com/example/userservice/modules/user/service/UserService.java` |
| `src/main/java/com/example/userservice/modules/user/application/service/UserTransactions.java` | `src/main/java/com/example/userservice/modules/user/service/UserTransactions.java` |
| `src/main/java/com/example/userservice/modules/user/application/service/IdentitySyncPort.java` | `src/main/java/com/example/userservice/modules/user/service/IdentitySyncPort.java` |
| `src/main/java/com/example/userservice/modules/user/application/service/ProvisioningPort.java` | `src/main/java/com/example/userservice/modules/user/service/ProvisioningPort.java` |
| `src/main/java/com/example/userservice/modules/user/application/query/UserQuery.java` | `src/main/java/com/example/userservice/modules/user/service/UserQuery.java` |
| `src/main/java/com/example/userservice/modules/user/api/response/UserResponse.java` | `src/main/java/com/example/userservice/modules/user/dto/response/UserResponse.java` |
| `src/main/java/com/example/userservice/modules/user/api/controller/UserController.java` | `src/main/java/com/example/userservice/modules/user/controller/UserController.java` |
| `src/main/java/com/example/userservice/modules/user/api/request/ChangeStatusRequest.java` | `src/main/java/com/example/userservice/modules/user/dto/request/ChangeStatusRequest.java` |
| `src/main/java/com/example/userservice/modules/user/api/request/UpdateUserRequest.java` | `src/main/java/com/example/userservice/modules/user/dto/request/UpdateUserRequest.java` |
| `src/main/java/com/example/userservice/modules/user/api/request/CreateUserRequest.java` | `src/main/java/com/example/userservice/modules/user/dto/request/CreateUserRequest.java` |
| `src/main/java/com/example/userservice/modules/user/api/request/PatchMeRequest.java` | `src/main/java/com/example/userservice/modules/user/dto/request/PatchMeRequest.java` |
| `src/main/java/com/example/userservice/integration/keycloak/dto/KeycloakUser.java` | `src/main/java/com/example/userservice/integration/keycloak/KeycloakUser.java` |
| `src/main/java/com/example/userservice/integration/keycloak/config/KeycloakConfig.java` | `src/main/java/com/example/userservice/integration/keycloak/KeycloakConfig.java` |
| `src/main/java/com/example/userservice/integration/keycloak/config/KeycloakProperties.java` | `src/main/java/com/example/userservice/integration/keycloak/KeycloakProperties.java` |
| `src/main/java/com/example/userservice/integration/keycloak/exception/IdentityException.java` | `src/main/java/com/example/userservice/integration/keycloak/IdentityException.java` |
| `src/main/java/com/example/userservice/integration/keycloak/client/KeycloakAdminClient.java` | `src/main/java/com/example/userservice/integration/keycloak/KeycloakAdminClient.java` |
| `src/main/java/com/example/userservice/core/pagination/PageResult.java` | `src/main/java/com/example/userservice/core/response/PageResult.java` |
| `src/test/java/com/example/userservice/modules/user/application/UserServiceTest.java` | `src/test/java/com/example/userservice/modules/user/service/UserServiceTest.java` |
| `src/test/java/com/example/userservice/modules/user/infrastructure/UserSpecificationsTest.java` | `src/test/java/com/example/userservice/modules/user/specification/UserSpecificationsTest.java` |
| `src/test/java/com/example/userservice/modules/user/infrastructure/UserPersistenceMapperTest.java` | `src/test/java/com/example/userservice/modules/user/mapper/UserPersistenceMapperTest.java` |
| `src/test/java/com/example/userservice/modules/user/domain/UserTest.java` | `src/test/java/com/example/userservice/modules/user/entity/UserTest.java` |

## Related changes

- Updated Java package declarations, imports and fully qualified references in production and test sources.
- Updated the existing ArchUnit rules to target the relocated model and service classes.
- Updated README and architecture documentation to describe the actual layout.
- Removed only empty old package directories after moving their files.
- Added this move manifest; no production or test class was added, merged or deleted.

## Preserved behavior and limits

REST paths, methods, DTO fields, validation, service logic, JPA mappings, security checks and Keycloak operations are preserved in source. Liquibase, application configuration, realm JSON, Maven dependencies, wrapper and Docker files are unchanged.

The retained User/UserJpaEntity pair and repository adapter are intentional in this folder-only change. The broader class consolidation described in the reference document was not performed.

File-level checks compare source bodies after package substitution, confirm matching package paths and local imports, and compare hashes of protected configuration files. These are static checks, not compilation or runtime verification. Maven compilation and all tests remain unexecuted at the user’s request. There were no role-management REST endpoints before this change; integration client role operations are retained.
