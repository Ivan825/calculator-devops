# calculator-devops

A Calculator REST API in **Java 21 / Spring Boot 3.5 / Maven**, wrapped in a fully automated
CI/CD pipeline: **GitHub → Jenkins → Docker → Docker Hub → Docker Compose**.

CSE 816 — Software Production Engineering, Mini-Project.

- GitHub: <https://github.com/Ivan825/calculator-devops>
- Docker Hub: <https://hub.docker.com/r/vortexfrenzy/calculator-devops>

```
 git push ──► GitHub ──(webhook / poll SCM)──► Jenkins
                                                 │
     ┌──────────┬──────────┬────────────────┬────┴─────┬──────────────────────┐
     ▼          ▼          ▼                ▼          ▼                      ▼
 Checkout     Build       Test         Docker Build   Push                 Deploy
 (git)    (mvn package) (mvn test,     (multi-stage  (Docker Hub,     (docker compose up -d,
                         JUnit report)  image :N)     Jenkins creds)    health check via Nginx)
                                                                              │
                                                    email notification ◄──────┘
```

Runtime stack started by `docker compose up`:

```
            host :8081
                │
         ┌──────▼──────┐   frontend network
         │    nginx    │
         └──────┬──────┘   backend network (internal)
        ┌───────┴───────┐
 ┌──────▼─────┐  ┌──────▼─────┐
 │ calculator │  │ calculator │   2 replicas, each with /actuator/health check
 │ replica 1  │  │ replica 2  │
 └────────────┘  └────────────┘
```

## API

| Operation | Endpoint                      | Result                                    |
|-----------|-------------------------------|-------------------------------------------|
| Add       | `GET /api/v1/add?a=&b=`       | `a + b`                                   |
| Subtract  | `GET /api/v1/subtract?a=&b=`  | `a - b`                                   |
| Multiply  | `GET /api/v1/multiply?a=&b=`  | `a * b`                                   |
| Divide    | `GET /api/v1/divide?a=&b=`    | `a / b` (16 significant digits); `b = 0` → HTTP 400 |
| Health    | `GET /actuator/health`        | `{"status":"UP"}`                         |

Numbers are handled as `BigDecimal`, so decimal arithmetic is exact (`0.1 + 0.2 = 0.3`).

```bash
$ curl 'http://localhost:8081/api/v1/add?a=2&b=3'
{"operation":"add","a":2,"b":3,"result":5}

$ curl 'http://localhost:8081/api/v1/divide?a=5&b=0'
{"timestamp":"2026-10-29T08:00:00Z","status":400,"error":"Bad Request",
 "message":"Division by zero is not allowed","path":"/api/v1/divide"}
```

Every client error returns HTTP 400 with the same JSON shape (`timestamp`, `status`, `error`,
`message`, `path`):

| Case                         | Example                         | `message`                                   |
|------------------------------|---------------------------------|---------------------------------------------|
| Division by zero             | `/divide?a=5&b=0`               | `Division by zero is not allowed`           |
| Missing / empty parameter    | `/add?a=1`                      | `Required parameter 'b' is missing`         |
| Not a number                 | `/subtract?a=abc&b=2`           | `Parameter 'a' must be a number, got 'abc'` |
| Out of range                 | `/multiply?a=1e999999999&b=2`   | `Parameter 'a' is out of range: ...`        |

Operands are limited to 50 significant digits and an exponent within ±1000, so a request can't
force huge `BigDecimal` computations.

## Build, test and run locally

Requires JDK 21. The Maven wrapper downloads Maven itself.

```bash
./mvnw clean package            # build target/calculator.jar (runs tests)
./mvnw test                     # tests only; JUnit XML in target/surefire-reports/
java -jar target/calculator.jar # http://localhost:8080
```

Tests:

- `CalculatorServiceTest`: JUnit 5 unit tests for every operation, including divide by zero.
- `CalculatorControllerIntegrationTest`: `@SpringBootTest` + MockMvc tests for every endpoint, the
  response shape, every 400 error case and the actuator health endpoint.

## Docker

Multi-stage [`Dockerfile`](Dockerfile):

1. **build**: `maven:3.9.11-eclipse-temurin-21`. `pom.xml` is copied and dependencies are
   resolved first, so that layer stays cached until the POM changes. Then the sources are copied
   and the JAR is packaged.
2. **runtime**: `eclipse-temurin:21-jre-alpine` with only `app.jar`. It runs as the non-root user
   `spring`, has an explicit `EXPOSE 8080` and a `HEALTHCHECK` on `/actuator/health`.

[`.dockerignore`](.dockerignore) keeps `target/`, `.git/`, IDE files and so on out of the
build context.

```bash
docker build -t vortexfrenzy/calculator-devops:dev .
docker run --rm -p 8080:8080 vortexfrenzy/calculator-devops:dev
curl 'http://localhost:8080/api/v1/multiply?a=6&b=7'
```

## Docker Compose

[`docker-compose.yml`](docker-compose.yml) starts the whole stack with one command:

- `calculator`: 2 replicas (`APP_REPLICAS`), health check on `/actuator/health`,
  `restart: unless-stopped`, environment variables (`SERVER_PORT`, `APP_VERSION`, `JAVA_OPTS`).
  These are attached only to the internal `backend` network.
- `nginx`: reverse proxy and load balancer ([`nginx/nginx.conf`](nginx/nginx.conf)) on host
  port `8081`. It starts only after the API is healthy and has its own health check. Nginx
  re-resolves the service name, so it finds every replica even after containers are recreated.
  The `X-Upstream-Server` response header shows which replica served the request.

```bash
docker compose up -d            # uses ./.env defaults (image tag "latest", port 8081)
docker compose ps
curl -i 'http://localhost:8081/api/v1/add?a=2&b=3'   # repeat: X-Upstream-Server alternates
docker compose down
```

[`.env`](.env) holds non-secret defaults only. Jenkins overrides `IMAGE_TAG` with the build number.

## Jenkins CI/CD pipeline

[`Jenkinsfile`](Jenkinsfile) is a declarative pipeline:

| Stage        | What it does                                                                 |
|--------------|-------------------------------------------------------------------------------|
| Checkout     | `checkout scm`: pulls the commit that triggered the build                      |
| Build        | `./mvnw clean package -DskipTests`, archives the JAR                           |
| Test         | `./mvnw test`, publishes JUnit results. A failing test fails the build          |
| Docker Build | builds `vortexfrenzy/calculator-devops:<BUILD_NUMBER>` and `:latest`           |
| Push         | `docker login` with the Jenkins credential `dockerhub-credentials`, pushes both tags |
| Deploy       | `docker compose up -d --no-build --remove-orphans --wait`, then checks health through Nginx |

- **Triggers:** GitHub webhook (`githubPush()`), with Poll SCM every minute as a fallback.
- **Notifications:** an email with the build result is sent after every build (`emailext`).
- **Idempotent:** `disableConcurrentBuilds()` stops two deploys from racing. Compose only
  recreates containers whose image or config changed, so re-running a build leaves a healthy
  deployment unchanged. Old numbered image tags are pruned (the last 5 are kept).
- **No secrets in the repo:** the Docker Hub token and SMTP password live only in the Jenkins
  credential store.

### Jenkins setup (one time)

1. **Plugins** (*Manage Jenkins → Plugins → Available*): Pipeline, Git, GitHub, JUnit,
   Credentials Binding, Email Extension, Timestamper. Restart Jenkins afterwards.
2. **Docker Hub credential** (*Manage Jenkins → Credentials → System → Global → Add*):
   kind *Username with password*, username `vortexfrenzy`, password = a Docker Hub
   **access token** (Docker Hub → Account settings → Personal access tokens, Read & Write),
   ID `dockerhub-credentials`.
3. **Email** (*Manage Jenkins → System → Extended E-mail Notification*), for example Gmail:
   SMTP server `smtp.gmail.com`, port `465`, *Use SSL*, and credentials = your Gmail address and
   a Gmail **app password**. Also set *System Admin e-mail address* under *Jenkins Location*.
4. **Job**: *New Item → Pipeline*, name `calculator-devops`.
   - *Build Triggers*: tick **GitHub hook trigger for GITScm polling** (and Poll SCM is set by the
     Jenkinsfile).
   - *Pipeline*: **Pipeline script from SCM**, SCM *Git*, repository
     `https://github.com/Ivan825/calculator-devops.git`, branch `*/main`, script path `Jenkinsfile`.
5. **Webhook (optional, for instant triggers)**: Jenkins runs on `localhost`, so expose it with
   `ngrok http 8080`, then in GitHub → *Settings → Webhooks → Add webhook* set the payload URL to
   `https://<ngrok-id>.ngrok-free.app/github-webhook/`, content type `application/json`,
   event *Just the push event*. Without a webhook, Poll SCM picks up pushes within a minute.
6. Click **Build Now** once. The first run also registers the triggers.

## Repository layout

```
calculator-devops/
├── src/main/java/com/spe/calculator/   # Spring Boot app: controller, service, DTOs, error handling
├── src/test/java/com/spe/calculator/   # JUnit 5 unit + MockMvc integration tests
├── pom.xml                             # Maven build (Spring Boot 3.5, Java 21)
├── mvnw, .mvn/                         # Maven wrapper
├── Dockerfile                          # multi-stage build
├── .dockerignore
├── docker-compose.yml                  # 2 API replicas + Nginx
├── nginx/nginx.conf                    # reverse proxy / load balancer
├── .env                                # non-secret compose defaults
├── Jenkinsfile                         # declarative CI/CD pipeline
├── .gitignore
└── README.md
```

## Git workflow

`main` is the single deployable branch. The initial features were developed on feature branches
(`feature/calculator-api`, `feature/containerization`, `feature/jenkins-pipeline`) and merged into
`main` with merge commits. Every push to `main` is built and deployed by Jenkins.
