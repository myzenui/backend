# Zen Backend — Architecture Reference

> This document is intended for AI agents and developers working on this codebase.
> It describes the project structure, technology stack, key services, data flow, and conventions.

## 1. Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| Build | Maven (`pom.xml`, wrapper: `./mvnw`) |
| ORM | Spring Data JPA / Hibernate (auto DDL via `ddl-auto=update`) |
| Primary Database | Microsoft SQL Server (JDBC driver `mssql-jdbc`) |
| Database Dialect | `org.hibernate.dialect.SQLServerDialect` |
| AI Integration | Spring AI with OpenAI (`spring-ai-starter-model-openai`) |
| Firestore | Google Firebase Admin SDK — used as the primary **document store** for projects, jobs, and real-time events |
| Auth | Firebase Authentication (token validation) |
| Docker | `docker-java` SDK + CLI `ProcessBuilder` calls for container lifecycle |
| Git | JGit for programmatic Git operations (clone, commit, push) |
| DNS / Tunnels | Cloudflare API (DNS records, tunnel routing) |
| API Docs | SpringDoc OpenAPI (`springdoc-openapi-starter-webmvc-ui`) — Swagger UI at `/swagger-ui.html` |

## 2. Project Structure

```
src/main/java/com/armikom/zen/
├── BackendApplication.java          # @SpringBootApplication + @EnableAsync entry point
├── config/
│   ├── FirebaseConfig.java          # FirebaseApp + Firestore beans
│   ├── CloudflareConfig.java        # Cloudflare WebClient bean
│   ├── ChatMemoryConfig.java        # Spring AI chat memory (JDBC-backed)
│   └── OpenApiConfig.java           # Swagger/OpenAPI metadata
├── controller/
│   ├── DatabaseController.java      # /api/database — create, trim, drop, backup, restore, test
│   ├── PreviewController.java       # /api/preview — generate, status, cleanup
│   ├── ProjectController.java       # /api/project — CRUD for Firestore projects
│   ├── GeneratorController.java     # /api/generator — PlantUML → C# conversion
│   ├── GeminiController.java        # /api/gemini — AI chat endpoint
│   ├── CloudflareController.java    # /api/cloudflare — DNS & tunnel management
│   ├── GitHubController.java        # /api/github — repo operations
│   └── TestController.java          # /api/test — health/diagnostics
├── dto/                             # Request/Response DTOs with Jakarta validation
├── enums/
│   └── DatabaseEnvironment.java     # PREVIEW | PRODUCTION
├── model/
│   ├── Project.java                 # Firestore-mapped POJO (name, description, businessModel, userStories, userId)
│   └── Job.java                     # Firestore-mapped POJO (id, projectId, userId, status, type, creationDate)
├── service/
│   ├── JobService.java              # Firestore real-time listener for queued jobs; dispatches to handlers
│   ├── JobLog.java / IJobLog.java   # Buffered Firestore log writer (flushes every 1s)
│   ├── PreviewService.java          # End-to-end preview pipeline: UML → C# → Docker build → deploy
│   ├── DatabaseService.java         # Interface for SQL Server DB operations
│   ├── DatabaseServiceImpl.java     # SQL Server implementation (create DB/user, drop/trim tables, backup/restore)
│   ├── AIService.java               # PlantUML generation from project description via AI
│   ├── ModelAIService.java          # Additional AI model interactions
│   ├── PlantUmlToCSharpService.java # Parses PlantUML and generates C# entity classes
│   ├── DockerService.java           # Docker client wrapper (ping, push, image ops)
│   ├── ProjectService.java          # Firebase auth validation + Firestore project CRUD
│   ├── CloudflareService.java       # Cloudflare API: DNS records, tunnel config
│   ├── GitHubService.java           # JGit: clone/create repo, merge files, push
│   ├── FirebaseTools.java           # AI function-calling tools backed by Firebase
│   ├── FirebaseMessageListener.java # Firestore message listener
│   └── StartupService.java          # Application startup tasks
└── cli/
    └── ChatRunner.java              # CLI chat runner (optional)
```

## 3. Configuration

### Profiles
- `application.properties` — shared config, active profile set to `dev`
- `application-dev.properties` — dev-specific overrides (tokens, credentials, `job.service.enabled=true`)

### Key Properties
| Property | Purpose |
|---|---|
| `spring.datasource.*` | Primary SQL Server connection (used by JPA/Hibernate) |
| `database.preview.*` | Preview environment SQL Server connection |
| `database.production.*` | Production environment SQL Server connection |
| `firebase.project.id` | GCP project for Firebase/Firestore |
| `spring.ai.openai.api-key` | OpenAI API key for Spring AI |
| `preview.docker.image` | Base Docker image for .NET preview builds |
| `cloudflare.api.token / zone.id / account.id / tunnel.id` | Cloudflare credentials |
| `job.service.enabled` | Enable/disable Firestore job listener (default: `false`) |
| `github.token / github.username` | GitHub PAT for JGit push operations |

## 4. Database Architecture

### SQL Server Environments
The app manages **two** independent SQL Server environments:
- **PREVIEW** — databases for preview/staging containers
- **PRODUCTION** — production databases

Each project gets its own database where:
- Database name = project id (from Firestore document's `id` field)
- Username = project id
- Password = deterministic hash: `MyZen25!` + UUID-based hash of project id

### Hibernate DDL
The primary app database uses `spring.jpa.hibernate.ddl-auto=update` — Hibernate auto-creates/updates schema on startup.

### Preview Databases
Preview project databases are managed entirely through `DatabaseService`:
- Created during preview generation (`PreviewService.generatePreview`)
- Tables can be dropped via `DatabaseService.dropAllTables` (used by `reset_database` job)
- EF Core migrations in the .NET containers handle schema creation on container startup

## 5. Job System

### Overview
`JobService` listens to the Firestore `jobs` collection for documents with `status=queued`. When a new job arrives, it's dispatched to an async handler based on `job.type`.

### Job Types

| Type | Handler | Description |
|---|---|---|
| `generate` | `processGenerateJob` | Uses AI to generate a PlantUML business model from the project description. Stores result in Firestore. |
| `preview` | `processPreviewJob` | Full preview pipeline: PlantUML → C# code gen → Docker build → Docker image → create DB → run container → Cloudflare DNS. |
| `reset_database` | `processResetDatabaseJob` | Drops all tables in the project's preview database and restarts the Docker container so EF Core migrations run from scratch. Used when migrations fail or schema gets corrupted. |

### Job Lifecycle
1. Frontend creates a Firestore document in `jobs` collection with `status: "queued"`
2. `JobService` snapshot listener picks it up
3. Job is dispatched to an `@Async` handler
4. Handler logs progress via `JobLog` (buffered Firestore writes)
5. Status updated to `completed` or `failed` with optional error message

### Firestore Job Document Fields
```
{
  "projectId": "firestore-doc-id",
  "userId": "firebase-auth-uid",
  "status": "queued" | "processing" | "completed" | "failed" | "cancelled",
  "type": "generate" | "preview" | "reset_database",
  "creation_date": Timestamp,
  "logs": ["[timestamp] [LEVEL] message", ...],
  "result": "...",
  "errorMessage": "..."
}
```

### Project ID Resolution
The Firestore document ID for a project is **not** always the same as the project's logical id. The actual project id (used as database name, Docker container name, GitHub repo name) is stored in the Firestore project document's `id` field. Use `resolveProjectId(firestoreDocumentId)` to obtain it.

## 6. Preview Pipeline (`PreviewService.generatePreview`)

```
1. Resolve project id from Firestore
2. Clone/checkout project repo from GitHub (if exists)
3. Check for model.lock → skip code generation if present
4. PlantUmlToCSharpService.generate(plantUml) → Map<filename, content>
5. Write generated C# files to ~/zen/previews/<projectId>/
6. Docker build (compile .NET project inside container)
7. Build runnable Docker image tagged as myzen/<projectId>
8. Git commit and push updated files (if repo existed)
9. DatabaseService.createDatabase(PREVIEW, projectId, ...) — ensure DB exists
10. Replace and run container on `myzen` Docker network
11. Configure Cloudflare DNS: myzen-<projectId>.armikom.com → container:5000
```

### File System Layout
```
~/zen/previews/
├── Dockerfile                      # Shared Dockerfile for all projects (auto-generated)
├── <projectId>/
│   ├── Zen.csproj                  # .NET 8 project file
│   ├── nuget.config                # NuGet package sources (incl. DevExpress)
│   ├── Model/
│   │   ├── BaseEntity.cs           # Base class for all entities
│   │   ├── <Entity>.cs             # Generated entity classes
│   │   └── ...
│   ├── .devcontainer/
│   │   └── devcontainer.json       # Dev container configuration
│   └── .vscode/
│       ├── launch.json
│       └── tasks.json
```

### Docker Networking
- All preview containers run on the `myzen` Docker network
- Container name: `myzen-<projectId>`
- SQL Server hostname on the network: `mssql_zen` (port 1433)
- Application port: 5000
- Connection string format: `Server=mssql_zen,1433;Database=<projectId>;User Id=<projectId>;Password=<generated>;...`

## 7. REST API Endpoints

| Prefix | Controller | Description |
|---|---|---|
| `/api/database` | `DatabaseController` | DB management: create, trim-tables, drop-tables, backup, restore, change-password, test-connection |
| `/api/preview` | `PreviewController` | Preview: generate, status, cleanup |
| `/api/project` | `ProjectController` | Firestore project CRUD |
| `/api/generator` | `GeneratorController` | PlantUML → C# code generation |
| `/api/gemini` | `GeminiController` | AI chat interface |
| `/api/cloudflare` | `CloudflareController` | DNS records, tunnel routes |
| `/api/github` | `GitHubController` | GitHub repo operations |
| `/api/test` | `TestController` | Health checks |

## 8. Conventions & Patterns

### Code Style
- Plain Java POJOs for models (no Lombok)
- Jakarta Validation annotations on DTOs
- Constructor injection for services (no `@Autowired`)
- `@Async` for long-running job handlers
- `IJobLog` interface with `JobLog` buffered implementation for Firestore logging

### Error Handling
- Controllers return `ResponseEntity` with typed response DTOs
- Services throw `SQLException` / `IOException` for DB/IO failures
- Job handlers catch exceptions and update Firestore job status to `failed`

### Security
- Firebase auth token validation via `ProjectService.validateAuthToken`
- SQL injection prevention: parameterized queries + input validation patterns
- DB name/username/password validated against regex patterns

### Building & Running
```bash
./mvnw compile                    # Compile
./mvnw spring-boot:run            # Run with dev profile
./mvnw package -DskipTests        # Build JAR
```

### Environment Variables (override defaults)
```
FIREBASE_PROJECT_ID, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME,
SPRING_DATASOURCE_PASSWORD, PREVIEW_DB_URL, PREVIEW_DB_USERNAME,
PREVIEW_DB_PASSWORD, PRODUCTION_DB_URL, PRODUCTION_DB_USERNAME,
PRODUCTION_DB_PASSWORD, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ZONE_ID,
CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_TUNNEL_ID, GITHUB_TOKEN,
GITHUB_USERNAME, SPRING_AI_OPENAI_API_KEY, JOB_SERVICE_ENABLED,
PREVIEW_DOCKER_IMAGE
```
