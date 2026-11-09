> Updated extraction workflow: see [DEPLOYMENT.md](DEPLOYMENT.md) and [VERIFICATION.md](VERIFICATION.md). Earlier setup dates below describe historical native installation.

# Native local setup

Configured on 2026-11-05. PostgreSQL, Keycloak and API run directly on macOS. Docker is not needed for this setup.

## Running services

| Service | Address | Credentials |
|---|---|---|
| PostgreSQL | localhost:5432, database `user_service_db` | `app_runtime`, password `DB_PASSWORD` in `.env` |
| Liquibase connection | Same database | `app_migrator`, password `DB_MIGRATION_PASSWORD` |
| Keycloak database | localhost:5432, database `keycloak_db` | `keycloak`, password `KEYCLOAK_DB_PASSWORD` |
| Keycloak Admin Console | http://localhost:8081/admin/ | Username `KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME`, password `KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD` |
| API | http://localhost:8080 | Keycloak access token for protected routes |
| Swagger UI | http://localhost:8080/swagger-ui/index.html | Bearer token for API calls |
| Readiness | http://localhost:8080/actuator/health/readiness | Public health status |

Passwords are stored only in the ignored `.env`, permissions 600. Do not replace it with `.env.example`, publish it, or regenerate database passwords without rotating the corresponding PostgreSQL roles. Docker-only DB bootstrap secrets in `.env` are unused in this native setup.

Postgres.app is installed at `/Applications/Postgres.app`; existing data is under `~/Library/Application Support/Postgres/var-18`. Existing databases, including `ie103`, were preserved. The project created only its two databases and three non-superuser roles. No business user was seeded; smoke-test users were removed.

## Chạy từng thành phần bằng Java/Maven

Không cần Python hoặc Docker cho cách chạy này. Môi trường local đã được setup; không tạo lại database và không ghi đè `.env`.

### 0. Kiểm tra có đang chạy sẵn không

```sh
lsof -nP -iTCP:5432 -iTCP:8081 -iTCP:8080 -sTCP:LISTEN
```

- 5432: PostgreSQL.
- 8081: Keycloak.
- 8080: Spring Boot API.

Nếu thành phần đã chạy thì dùng luôn, không chạy thêm bản thứ hai trên cùng cổng. Nếu cần chuyển từ bản chạy nền sang terminal, xác nhận PID bằng lệnh trên rồi dùng `kill -TERM <PID>` cho đúng tiến trình Java API/Keycloak. Không dừng PostgreSQL đang phục vụ database khác. Không dùng `kill -9` hoặc `killall java`.

### 1. PostgreSQL

Mở **Postgres.app**, chọn server PostgreSQL 18 rồi bấm **Start** nếu chưa chạy. Hoặc mở app bằng:

```sh
open -a Postgres
```

Hai database `user_service_db` và `keycloak_db` cùng các role đã được tạo. `open -a Postgres` chỉ mở giao diện; kiểm tra server đã Running. Có thể kiểm tra từ terminal:

```sh
/Applications/Postgres.app/Contents/Versions/latest/bin/pg_isready -h localhost -p 5432
```

### 2. Keycloak — terminal thứ nhất

Trong thư mục project:

```sh
MPL_ENV_FILE="$PWD/.env" bash ../mpl-iam/scripts/start.sh
```

Script shell tự đọc `.env`, chọn Java 21 trên macOS nếu chưa có JAVA_HOME, và chạy Keycloak local với database/port đã cấu hình. Không dùng Python.

Giữ terminal này mở. Khi log báo `Listening on: http://127.0.0.1:8081`, Keycloak đã chạy. Realm và bootstrap admin đã có trong database; không cần import realm lại mỗi lần khởi động. Các lệnh này chạy server Java của Keycloak, không chạy backend Spring Boot.

Mở **http://localhost:8081/admin/** trên trình duyệt:

1. Username: giá trị `KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME` trong `.env` (hiện là `bootstrap-admin`).
2. Password: giá trị `KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD` trong `.env`.
3. Đăng nhập xong, chọn realm **user-service** ở menu chọn realm góc trên bên trái.
4. **Users**: xem danh sách user. **Clients**: xem `user-service-api`, `user-service-frontend`, `user-service-admin`. **Realm roles**: xem `USER`, `ADMIN`.

Lần đầu danh sách business user có thể trống vì chưa tạo user; tài khoản service-account là tài khoản hệ thống. Bootstrap admin đăng nhập realm master, không tự trở thành ADMIN của ứng dụng.

### 3. Independent migration, then Spring Boot — terminal thứ hai

```sh
(cd ../mpl-db-migrations && ./mvnw -B -ntp package)
bash ../mpl-db-migrations/scripts/migrate.sh --env-file .env verify-history
bash ../mpl-db-migrations/scripts/migrate.sh --env-file .env updateSQL
# Review SQL before applying:
bash scripts/local.sh migrate
```


Trong thư mục project:

```sh
./mvnw spring-boot:run
```

Maven run tự chọn profile local; Spring tự import `.env`. Không cần nạp biến bằng tay. `.env` được đọc như Java properties: giữ giá trị không bọc dấu nháy, không dùng cú pháp nội suy shell.

Đợi log `Started UserServiceApplication`. Maven compile source và chạy ứng dụng trực tiếp; cách này không dùng file JAR cũ và không cần script Python. API không chạy Liquibase. Chạy `bash scripts/local.sh migrate` trước khi khởi động; schema được Hibernate validate.

- Swagger: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health/readiness
- Các API User cần access token Keycloak; API quản trị cần role ADMIN.

### 4. Dừng hoặc chạy lại sau khi sửa code

Bấm **Ctrl+C** trong terminal Spring Boot để dừng API, rồi chạy lại `./mvnw spring-boot:run`. PostgreSQL và Keycloak có thể tiếp tục chạy. Muốn dừng Keycloak thì Ctrl+C trong terminal Keycloak.

## Optional Bash launcher (không bắt buộc)

Open Postgres.app and ensure its PostgreSQL 18 server is running. Then, from the project directory:

```sh
source ~/.zshrc
bash scripts/local.sh start
```

The launcher checks for occupied ports, starts Keycloak, runs the independent migration and starts the packaged API. It does not reapply the realm. It uses Java 21, listens on loopback, caps the Keycloak heap at 512 MiB and API heap at 384 MiB. Keycloak uses local cache for this single-node development setup. These caps are heap limits, not total process memory limits.

```sh
bash scripts/local.sh status
bash scripts/local.sh stop
```

Stop leaves PostgreSQL running so it does not interrupt other databases. PID/log files are in `.local/` and ignored by Git and Docker build context. If a service fails, inspect `.local/api.log`, `../mpl-iam/.local/keycloak.log` or `../mpl-iam/.local/keycloak-config.log`.

After Java source changes:

```sh
bash scripts/local.sh stop
./mvnw -B -ntp package
bash scripts/local.sh start
```

If Maven reports the wrong Java version, set `JAVA_HOME=$(/usr/libexec/java_home -v 21)` in your current terminal. New interactive terminals already load Java 21 and the Postgres.app psql path from `.zshrc`.

## Installed Keycloak files

The pinned server and CLI jar are under `~/.local/share/mpl-be/`, outside the repository. `.env` contains `KEYCLOAK_HOME` and `KEYCLOAK_CONFIG_CLI_JAR`. The launcher expects those files to exist; it does not download or install software automatically. Its explicit macOS Java lookup is for this local setup, not a production deployment script.

The realm is `user-service`. The backend client is `user-service-admin`; its secret is `KEYCLOAK_CLIENT_SECRET`. Frontend uses `user-service-frontend` and Authorization Code + PKCE. Bootstrap admin authenticates to the master realm's console and is not automatically an application ADMIN. Create an application administrator in `user-service` and explicitly assign realm role ADMIN when you need browser/API user access. Do not grant ADMIN to the backend runtime service account.

## Email and tests

SMTP values remain unconfigured because a real provider/sandbox was not supplied. Verification/password-reset email requires those values and realm configuration reapplication (`MPL_IAM_APPLY_APPROVED=true bash scripts/local.sh configure`). Other tested backend operations do not require sending mail.

`./mvnw test` runs without Docker. `./mvnw verify` additionally invokes PostgreSQL Testcontainers and requires the external migration image. Docker was not used during the original 2026-11-05 native setup. Current extraction results are recorded in VERIFICATION.md; do not reuse the historical smoke result as current evidence.

## Java hoặc cổng Keycloak bị lỗi

`mpl-iam/scripts/start.sh` chọn Java 21 trên macOS và đặt cả `JAVA_HOME` lẫn `JAVA`, tránh kế thừa Java mới hơn từ terminal. Nếu chính bản Keycloak đã chạy trên cổng 8081, script chỉ thông báo địa chỉ để mở, không tạo thêm server. Nếu cổng do tiến trình khác chiếm, script dừng và hướng dẫn kiểm tra. Không cần thêm `--add-opens` để xử lý lỗi Java 24+ khi dùng đúng Java 21.
