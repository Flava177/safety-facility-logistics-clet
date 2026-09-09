# Security & Production-Resilience Audit — safety-facility-logistics-clet

**Date:** 2026-09-09
**Scope:** Full 5-module Maven reactor under `services/` (`sfl-service-common`, `sfl-facilities-service`, `sfl-fleet-logistics-service`, `sfl-safety-security-service`, `sfl-portal-service`), plus `deploy/compose/*.yml`, `.github/workflows/*.yml`, all Dockerfiles, and root-level config.
**Method:** Read-only static inspection (source, config, build files, git-tracked history by filename). No files were modified. Where a finding requires a runtime check or a tool this review couldn't execute, it is marked accordingly rather than assumed.
**Stack:** Spring Boot 4.1.0, Java 17, Keycloak-federated OAuth2/JWT (no local password store anywhere in the codebase).

---

## 1. Executive summary

No Critical or High-severity findings. The codebase shows evidence of prior deliberate security hardening: secure-by-default auth flags, JWKS-based JWT validation with no hardcoded key material, parameterized SQL throughout (including two large hand-written JDBC repositories that correctly allowlist sort/order clauses), layered request/command DTOs that structurally prevent mass assignment, HMAC-verified webhook ingestion with constant-time comparison and replay windows, a correctly implemented transactional-outbox pattern, and rigorous file-upload validation (magic-byte + active-content scanning) that exceeds typical baseline for a project this size.

The real gaps cluster in two places: **deployment/container hygiene** (weak default credentials in compose files, containers running as root, infra ports bound to all interfaces) and a handful of **narrow code-level gaps** (CSV formula injection in one export endpoint, no rate limiting anywhere, one unexplained secondary auth path). None of these require an architecture change — all are config or small code fixes.

**Total findings: 18** — 0 Critical, 0 High, 6 Medium, 6 Low, 6 Info (of which several are positive/no-action confirmations, included for completeness).

---

## 2. Findings

### Medium

**M1 — Weak default Postgres password across all DB containers**
- CWE-798 / CWE-521 (Use of Hard-coded / Weak Default Credentials)
- `deploy/compose/docker-compose.microservices.yml:6,22,38,58-59`
- All three Postgres containers default their password to the literal `sfl` via `${SFL_*_POSTGRES_PASSWORD:-sfl}` (matches the username — zero entropy) if the operator never sets the env var. This is inconsistent with `docker-compose.dev.yml` and `deploy/env/.env.example`, which correctly default to `change-me`.
- **Attack scenario:** anyone running `docker compose -f docker-compose.microservices.yml up` without setting env vars gets guessable DB credentials reachable from any container on the network (compounded by M3).
- **Remediation:** default to `change-me` (or fail with no default) in the microservices compose file for consistency with the other compose files.
- **Status:** Confirmed.

**M2 — RabbitMQ `guest`/`guest` default credentials**
- CWE-1188 (Insecure Default Initialization of Resource)
- `services/sfl-fleet-logistics-service/src/main/resources/application.yml:52-53`
- RabbitMQ's own factory-default credential pair is used as the fallback. It's well-known and actively scanned for.
- **Attack scenario:** if this broker is ever exposed beyond localhost with the env var unset, it's a known, actively-scanned-for weak credential pair.
- **Remediation:** default to a non-`guest` fallback (or require the env var), and keep the broker on an internal network regardless.
- **Status:** Confirmed.

**M3 — Infra ports bound to all interfaces (0.0.0.0), not localhost**
- CWE-799 (Improper Control of Interaction Frequency) / exposure
- `deploy/compose/docker-compose.microservices.yml:9,25,41,53,61-62,83-84`; `deploy/compose/docker-compose.dev.yml:13,22,33,46`
- Postgres, Redis, RabbitMQ, and Keycloak port mappings (e.g. `"5441:5432"`) bind to all interfaces by default rather than `127.0.0.1`.
- **Attack scenario:** on any host where these ports aren't independently firewalled, the databases/broker/IdP are reachable from the network, not just localhost — this combines directly with M1/M2's weak defaults.
- **Remediation:** bind as `"127.0.0.1:5441:5432"` (etc.) for local/dev compose files.
- **Status:** Confirmed.

**M4 — All four service containers run as root**
- CWE-250 (Execution with Unnecessary Privileges)
- `services/sfl-facilities-service/Dockerfile`, `sfl-fleet-logistics-service/Dockerfile`, `sfl-safety-security-service/Dockerfile`, `sfl-portal-service/Dockerfile`
- None contain a `USER` directive; all run the app as root inside `eclipse-temurin:17-jre`.
- **Attack scenario:** a container-escape or arbitrary-file-write vulnerability in any dependency has full root privileges inside the container instead of being contained by a non-root user.
- **Remediation:** add a non-root user in the runtime stage (`RUN useradd -r spring && USER spring`, or use the base image's built-in non-root user).
- **Status:** Confirmed.

**M5 — CSV formula injection in fuel transaction export**
- CWE-1236 (Improper Neutralization of Formula Elements in a CSV File)
- `services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fuel/application/service/FuelApplicationService.java:2299-2301` (the `csv()` escaping helper), used at lines 1691/1695/1703 to build the `/api/v1/fuel/reports/transactions.csv` export
- The helper only escapes double-quotes; it never neutralizes a leading `=`, `+`, `-`, or `@`. `vendorReference` (line 1703) is vendor-supplied data ingested via `FuelImportService` — i.e. attacker-influenced without going through this app's own input forms.
- **Attack scenario:** a malicious or compromised fuel-card vendor feed plants `=CMD(...)`-style content in a reference field; a staff member later opens the exported CSV in Excel and the formula executes.
- **Remediation:** prefix any cell value starting with `=`, `+`, `-`, or `@` with a leading `'` before quoting.
- **Status:** Confirmed.

**M6 — No rate limiting anywhere in the reactor**
- OWASP API4:2023 (Unrestricted Resource Consumption)
- Confirmed absent by exhaustive grep for `RateLimiter|Bucket4j|Resilience4j` across all `pom.xml` and `*.java` files — zero matches.
- **Attack scenario:** every REST endpoint, including auth-sensitive and export endpoints, is fully unthrottled — credential-stuffing against the IdP-fronted endpoints or repeated large CSV exports both go unmitigated at the application layer.
- **Remediation:** add a rate-limiting filter (Bucket4j or Resilience4j `RateLimiter`) at minimum for auth-adjacent and export/report endpoints, or enforce it at the API-gateway/ingress layer if one sits in front of these services in production.
- **Status:** Confirmed.

### Low

**L1 — `.env.example` ships with auth disabled by default**
- CWE-1188
- `services/sfl-fleet-logistics-service/.env.example:5`
- `SFL_SECURITY_ENABLED=false` is baked into the example file (with a comment noting it's dev-only). Copying it verbatim to `.env` disables authentication and trusts client-supplied `X-SFL-*` headers as identity.
- **Remediation:** default the example to `true`, with an explicit opt-in comment for local unauthenticated mode instead of shipping it pre-disabled.
- **Status:** Confirmed.

**L2 — Dev auth-bypass flag not additionally gated by `@Profile`**
- CWE-306 (Missing Authentication for Critical Function), if misconfigured
- `services/sfl-facilities-service/src/main/java/gh/edu/clet/sfl/facilities/shared/config/FacilitiesSecurityConfiguration.java:26-37` (the same `sfl.security.enabled` pattern repeats in the fleet-logistics and safety-security equivalents)
- A single env var fully disables auth and lets any caller impersonate any actor via `X-SFL-*` headers. It's gated only by `@ConditionalOnProperty`, not additionally restricted to `@Profile({"local","dev"})`, so nothing at the code level prevents the var from being set in a reachable environment. None of the compose/CI files in this repo set it to `false` — this is a residual design risk, not a currently-triggered one.
- **Remediation:** additionally gate the open security chain behind `@Profile({"local","dev"})` so a stray env var alone can't activate it against a prod-built artifact.
- **Status:** Confirmed present in code; not triggered by any config shipped in this repo. Needs runtime verification that no deployment pipeline outside this repo sets the var in a reachable environment.

**L3 — Unvalidated `siteCode` interpolated into `Content-Disposition` header**
- CWE-113 (HTTP Response Splitting) / CWE-116
- `FuelReportController.java:35`, `EmergencyReportController.java:31`, `DispatchReportController.java:45`
- The raw `siteCode` query parameter is concatenated straight into the export filename with no validation against an allowlist/pattern. Modern servlet containers reject raw CR/LF in header values, so classic response-splitting is likely blocked at the container level, but a stray `"` or `;` can still corrupt the `filename=` attribute.
- **Remediation:** validate `siteCode` against the known site-code format before use (it should already match one), or quote/escape it properly.
- **Status:** Confirmed in code; container-level mitigation of the worst-case (header injection) needs runtime verification.

**L4 — Unexplained `.httpBasic()` in safety-security-service with no backing user store**
- CWE-1021 / OWASP A05:2021
- `services/sfl-safety-security-service/src/main/java/gh/edu/clet/sfl/safetysecurity/config/SafetySecurityConfiguration.java:91`
- `.httpBasic(Customizer.withDefaults())` is enabled on this service's filter chain, but no `UserDetailsService`/`spring.security.user.*` is configured anywhere in the module. Neither `FacilitiesSecurityConfiguration` nor `FleetSecurityConfiguration` does this, and this service's own documented intent (matching its siblings) is JWT-only via Keycloak. With no custom `UserDetailsService`, Spring Boot auto-configures a default `user` account with a randomly generated password printed once to the startup log — an unintended second auth path on the module that handles incident/emergency notifications.
- **Attack scenario:** low, since a session authenticated this way would carry no matching `SflRole` authority and should fail downstream authorization — but the auto-generated-credential Basic-auth endpoint shouldn't exist at all, and its actual authority set hasn't been runtime-verified.
- **Remediation:** remove `.httpBasic(...)` unless there's a documented reason for it; if kept, wire an explicit `UserDetailsService` and document why Basic auth coexists with the OAuth2 resource server.
- **Status:** Confirmed present in code. Needs runtime verification for the actual authority/impact of a session authenticated via this path.

**L5 — Swagger/OpenAPI docs unauthenticated and inconsistent across services**
- CWE-200 (Information Exposure)
- `sfl-fleet-logistics-service/.../FleetSecurityConfiguration.java:60`, `sfl-safety-security-service/.../SafetySecurityConfiguration.java:87`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` are `permitAll()` in fleet-logistics and safety-security, but fall under `.anyRequest().authenticated()` in facilities — an inconsistency across otherwise-identical security configs.
- **Attack scenario:** unauthenticated reconnaissance of the full endpoint/schema surface for two of the three platform services.
- **Remediation:** gate Swagger/OpenAPI behind auth in non-dev profiles across all three services, or make the permitAll exposure a deliberate, consistent choice.
- **Status:** Confirmed.

**L6 — Shared scheduler thread starves safety-relevant sweeps behind others**
- CWE-405 (Asymmetric Resource Consumption, availability)
- `sfl-fleet-logistics-service/.../FleetServiceConfiguration.java:16` (`@EnableScheduling`); `application.yml:115-143`
- No `TaskScheduler`/`ThreadPoolTaskScheduler` bean is defined anywhere in the reactor, so Spring's default single-thread scheduling pool applies. Fleet-logistics alone registers 6 `@Scheduled` jobs (outbox drain, SLA escalation, compliance sweep, dashboard refresh, fuel sweep, dispatch sweep), all serialized on that one thread.
- **Attack scenario (business-risk, not a security exploit):** a slow compliance/dashboard sweep can delay the SLA-escalation sweep behind it — a real delay for a safety/compliance-relevant job, not just a performance nit.
- **Remediation:** set `spring.task.scheduling.pool.size` > 1 (sized to the number of distinct `@Scheduled` jobs) in each module with more than one scheduled job.
- **Status:** Confirmed.

### Info (strengths confirmed, or gaps needing tooling this review couldn't run)

- **Springdoc-openapi (`springdoc-openapi:3.0.3`, `services/pom.xml:44`)** is the only non-BOM-managed *runtime* dependency, so its CVE status isn't tracked by the Spring Boot BOM upgrade path. Needs periodic manual/tooled CVE check.
- **No `maven-enforcer-plugin`** anywhere — no dependency-convergence or banned-version enforcement. Confirmed absent by repo-wide grep.
- **No Dependabot/Renovate config** at the project level — no automated dependency-update tooling watching for new CVEs going forward. Confirmed absent.
- **Positive:** all other dependency versions are current, correctly scoped (`test`/`provided`/`runtime` used correctly everywhere), no version ranges (`LATEST`/`RELEASE`/bracket ranges), no arbitrary-code-execution build plugins.
- **Positive:** no hardcoded JWT signing secrets, no local password storage, no PII found in log statements, no cookies issued (stateless bearer-token API), no `@Cacheable` on sensitive data, no XXE/SpEL-injection/deserialization/SSRF attack surface (none of the enabling primitives — XML parsers, `ExpressionParser`, `ObjectInputStream`, outbound HTTP clients — exist in the codebase at all).
- **Positive:** file-upload validation (`UploadedFileScanner.java`, fleet-logistics) performs magic-byte + content-type cross-validation and active-content rejection (PDF `/JavaScript`, `/Launch`, `/EmbeddedFile`) — a genuinely strong control above typical baseline.
- **Positive:** actuator exposure is deliberately narrow and consistent (`health,info,metrics,prometheus` only; `/actuator/metrics` and `/actuator/prometheus` require authentication in all three platform services).
- **Positive:** at least one previously-real BOLA gap in `DispatchIntegrationController.java` (carrier-status endpoint missing site scoping) has already been fixed and is documented in the method's own Javadoc.
- **Needs runtime verification (not assessable from source alone):**
  - A full **git-history content scan** for secrets — this review checked filenames of added files across commit history, not diff contents. Recommend `gitleaks detect --source . --log-opts="--all"` or `trufflehog git file://.` before making the repo public.
  - Actual **CVE exposure** of the Spring Boot 4.1.0 BOM's transitive dependency set (Jackson, Tomcat/Netty, Postgres driver) — recommend `mvn org.owasp:dependency-check-maven:check` (or Dependency-Track) against all 5 modules; this review could not execute `mvn dependency:tree` or query a CVE database.
  - Whether **HTTPS/TLS is actually enforced** in front of these services — no code-level HSTS/redirect-to-HTTPS was found, consistent with delegating TLS termination to a reverse proxy/gateway (reasonable for a backend-only API), but should be confirmed operationally.
  - Whether **CORS** is actually permissive anywhere — all three security chains call `.cors(Customizer.withDefaults())` with no custom `CorsConfigurationSource` bean found in this pass, which should mean CORS denies unlisted origins by default, but the source of truth wasn't fully traced to rule out a permissive config with full confidence.

---

## 3. Block release / safe-to-push conclusion

**Conditional safe to push / make public.** No secrets, credentials, or key material were found committed anywhere in the current tree, and no Critical or High-severity code defect exists. The application-layer security posture (authz, injection resistance, upload validation, webhook verification) is genuinely strong for this project's stage.

Before flipping the repository to public, do these two things — they're the only items this static review could not fully close out itself:

1. **Run a real secret-scanning pass over full git history** (`gitleaks` or `trufflehog`, see §5) — this review only checked filenames of files added across commit history, not diff contents. If that comes back clean, there is no blocking secrets concern.
2. **Fix M1–M4** (weak compose credentials, ports bound to all interfaces, root containers) if any of the `deploy/compose/*.yml` files or Dockerfiles are ever going to be used as a real deployment starting point rather than pure local-dev scaffolding — a public repo inviting people to `docker compose up` these files as-is is the actual exposure vector, not the source code itself.

Everything else (M5, M6, L1–L6) is real but non-blocking — normal fast-follow hardening, not a reason to hold back a public release.

---

## 4. Pre-public-release checklist

- [ ] Run `gitleaks detect --source . --log-opts="--all"` (or `trufflehog git file://.`) over full history; resolve any hit before making the repo public
- [ ] Fix M1: remove/replace the `sfl` default Postgres password fallback in `docker-compose.microservices.yml`
- [ ] Fix M2: replace the RabbitMQ `guest`/`guest` fallback in fleet-logistics `application.yml`
- [ ] Fix M3: bind infra ports to `127.0.0.1` instead of all interfaces in both compose files
- [ ] Fix M4: add a non-root `USER` directive to all 4 service Dockerfiles
- [ ] Fix M5: escape leading `=+-@` in the fuel CSV export helper (`FuelApplicationService.java`)
- [ ] Decide on and implement M6: rate limiting for auth-adjacent and export/report endpoints (app-layer or gateway-layer)
- [ ] Fix L1: flip `.env.example`'s `SFL_SECURITY_ENABLED` default to `true`
- [ ] Fix L2: gate the security-disabled dev bypass behind `@Profile({"local","dev"})` in all 3 platform services
- [ ] Fix L3: validate/allowlist `siteCode` before it reaches `Content-Disposition` headers
- [ ] Fix L4: remove `.httpBasic()` from `SafetySecurityConfiguration` (or justify and properly wire it)
- [ ] Fix L5: make Swagger/OpenAPI exposure consistent (and non-public in non-dev profiles) across all 3 services
- [ ] Fix L6: size the scheduling thread pool in fleet-logistics beyond the default of 1
- [ ] Run `mvn org.owasp:dependency-check-maven:check` across all 5 modules; address any confirmed CVE
- [ ] Add `maven-enforcer-plugin` with `dependencyConvergence` + `bannedDependencies`
- [ ] Add Dependabot or Renovate for both the Maven and npm (frontend) ecosystems
- [ ] Confirm TLS/HSTS enforcement at whatever sits in front of these services in production
- [ ] Confirm (don't just infer) that no `CorsConfigurationSource` bean anywhere allows a permissive origin + credentials combination

---

## 5. Recommended tooling

| Purpose | Tool | Suggested invocation |
|---|---|---|
| Secret scanning (full git history) | [gitleaks](https://github.com/gitleaks/gitleaks) | `gitleaks detect --source . --log-opts="--all"` |
| Secret scanning (alternative/cross-check) | [trufflehog](https://github.com/trufflesecurity/trufflehog) | `trufflehog git file://. --since-commit=<first-commit>` |
| Dependency / CVE scanning (Java) | OWASP Dependency-Check Maven plugin | `mvn org.owasp:dependency-check-maven:check` (run per-module or at the reactor root) |
| Dependency / CVE scanning (alternative, SBOM-based) | [Trivy](https://github.com/aquasecurity/trivy) | `trivy fs --scanners vuln,secret .` and `trivy image <each built image>` |
| SAST (Java) | [Semgrep](https://semgrep.dev/) with the `p/java` and `p/owasp-top-ten` rulesets | `semgrep --config p/java --config p/owasp-top-ten services/` |
| SAST (Spring-specific patterns) | [SpotBugs](https://spotbugs.github.io/) + [find-sec-bugs](https://find-sec-bugs.github.io/) plugin | `mvn com.github.spotbugs:spotbugs-maven-plugin:check` |
| Container image scanning | Trivy or [Grype](https://github.com/anchore/grype) | `trivy image <image>` / `grype <image>` |
| Dependency-convergence / banned versions | maven-enforcer-plugin (add to `services/pom.xml`) | `mvn enforcer:enforce` |
| Automated dependency updates | Dependabot (`.github/dependabot.yml`) or Renovate | n/a — config file, not a CLI |
| Existing test suite (regression safety net for any fix above) | Maven Surefire (already in place) | `mvn test` per module or at the reactor root |

---

*This report reflects a static, read-only review. Several findings are explicitly marked "needs runtime verification" and should be confirmed against the actual deployed environment, not assumed from source alone.*
