# Run SFL Spring Boot locally

This is the Java equivalent of pressing **Start** in Visual Studio for the old C# app.

Spring Boot starts an embedded web server inside the application process. For this project that means IntelliJ runs `gh.edu.clet.sfl.SflApplication`, Spring Boot starts the web app, Flyway applies pending migrations, and the API becomes available at `http://localhost:8081`.

## IntelliJ IDEA

1. Open `C:\Users\danie\IdeaProjects\SFL`.
2. Select JDK 17 for the project.
3. Let IntelliJ import the Maven project from `pom.xml`.
4. Open **Run | Edit Configurations | SFL Java**.
5. Set the environment variable `SFL_DB_PASSWORD` to your local PostgreSQL password.
6. Run **SFL Java**.

Useful URLs after startup:

- `http://localhost:8081/api/health`
- `http://localhost:8081/api/version`
- `http://localhost:8081/actuator/health`

## Command line

From the repo root:

```powershell
.\scripts\dev\run-local.ps1
```

The script prompts for the PostgreSQL password if `SFL_DB_PASSWORD` is not already set.

To verify a running app:

```powershell
.\scripts\dev\verify-local.ps1
```

## Development loop

- Change Java code.
- Let IntelliJ build the project, or press **Build Project**.
- Spring Boot DevTools restarts the app when compiled classes change.
- Refresh the endpoint or client you are testing.

If the restart does not trigger, stop and rerun **SFL Java**. That is still normal during early setup.

## Database

Local Java development uses `sfl_java` by default:

```text
jdbc:postgresql://localhost:5434/sfl_java
```

The existing C# database is not used by this Spring Boot app.
