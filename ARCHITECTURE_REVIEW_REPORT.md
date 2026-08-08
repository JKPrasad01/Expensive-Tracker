# Staff Engineering Review — Spring Security & JWT Architecture

**System under review:** Expensive-Tracker (Spring Boot 4.0.6, Java 21, Spring Security 6/7 line, JPA/Hibernate, MySQL, jjwt 0.12.7, MapStruct, Lombok)
**Date:** 2026-08-04
**Reviewer stance:** Production design review, as if preparing for millions of users behind a load balancer, subject to external security audits.
**Constraint:** Read-only. No code was modified.

> **Reviewer's summary (TL;DR):**
> This codebase is a *prototype with production ambitions*. The JWT plumbing (filter, service, provider) is structurally correct — a competent engineer wrote it — but it is surrounded by (a) a parallel legacy auth implementation that stores plain-text passwords and does not compile, (b) zero authorization enforcement, (c) a hardcoded signing secret, and (d) no error semantics for the security chain. **It is not buildable today, and it is not deployable.** The core gap is architectural: there is no single ownership boundary for identity (two auth services), no connection between the permission data model (`Resource`/`Action`/`ResourceAction`/`Template`) and the authorization engine, and no security observability. Everything below is derived from the actual source; nothing is assumed.

---

# 1. Existing Architecture

## 1.1 Authentication architecture

Two **parallel, inconsistent** authentication subsystems exist:

| Concern | Canonical path | Legacy path (should not exist) |
|---|---|---|
| Controller | `AuthController` (`/auth/register`, `/auth/login`) | `UserController` (`/app/v1/user/signup`, unmapped `update`) |
| Service | `AuthenticationService` | `UserServiceImpl.signup/login/updateProfile` |
| Password handling | `BCryptPasswordEncoder` via `DaoAuthenticationProvider` | **Plain-text comparison** (`user.getPassword().equals(...)`) and plain-text persistence via MapStruct |
| Token issuance | `JwtService.generateToken(email, userId)` | Same `JwtService` (inconsistent reuse) |
| Compiles? | Yes | **No** (`UserNotFoundException` undefined, `userInfo` undefined — `UserServiceImpl.java:44,54`) |

This is the single most important architectural finding: **two identities exist for one user** — a login can succeed in one path and fail in the other. Any future feature (lockout, refresh tokens, audit) will be built twice or built inconsistently.

## 1.2 Authorization architecture

**None.** The security model is "authenticated or not". Specifically:

- `SecurityConfig.java:30-37` — `anyRequest().authenticated()` is the *only* rule.
- No `@EnableMethodSecurity` → `@PreAuthorize`/`hasRole`/`hasAuthority` are inert.
- `CustomUserDetails.getAuthorities()` maps `roleKey` → `SimpleGrantedAuthority` (so role keys like `ROLE_USER` would work with `hasRole` once method security is enabled), but nothing consumes authorities.
- The permission data model (`Resource`, `Action`, `ResourceAction`, `RoleTemplate`, `TemplateResourceAction`) is a **design for template/RBAC-based permissioning that is entirely disconnected from the authorization path**. There is no `PermissionEvaluator`, no permission service, no `@PreAuthorize` expression using it.

## 1.3 Security architecture

```
                    ┌─────────────────────────────────────────────┐
                    │           Deployment (single node)          │
                    │   No LB, no Redis, no profiles, no secrets  │
                    └─────────────────────────────────────────────┘
                                     │
        ┌────────────────────────────▼────────────────────────────┐
        │                   SecurityFilterChain                   │
        │   CSRF off · STATELESS · JwtFilter · no entry point     │
        └────────────────────────────┬────────────────────────────┘
                                     │
        ┌────────────────────────────▼────────────────────────────┐
        │        DAO layer: CustomUserDetailsService (every req)  │
        └────────────────────────────┬────────────────────────────┘
                                     ▼
                                MySQL (single)
```

- Secret: hardcoded in source (`JwtService.java:19-20`).
- DB credentials: `root/root` in `application.yaml:6-8`.
- Session: stateless (correct for JWT).
- CSRF: disabled (correct for stateless JWT).
- CORS: **absent** — frontend cannot call the API from another origin.

## 1.4 Package organization & layer separation

```
com.expensive.Expensive.Tracker
├── config/       ← SecurityConfig, ApplicationConfig, JwtFilter
├── security/     ← CustomUserDetails, CustomUserDetailsService
├── controller/   ← Auth, User, Role, Action, Resource controllers
├── service/      ← interfaces (all single-impl)
├── service/impl/ ← impls + AuthenticationService
├── repository/   ← JPA repositories
├── entity/       ← 8 JPA entities
├── dto/          ← flat + resource/ + resourceAction/ subpackages
├── mapper/       ← MapStruct mappers
├── exception/    ← 8 exceptions + GlobalExceptionHandler + ErrorResponse
├── enums/        ← ResourceType
└── utils/        ← JwtService, DateUtils (empty!)
```

Issues:
- `JwtService` (a security service with lifecycle) lives in `utils`; `JwtFilter` (a security filter) lives in `config`. Both belong in `security/`.
- `DateUtils` is an empty class (`utils/DateUtils.java:3-4`) — placeholder committed to main.
- Package name `com.expensive.Expensive.Tracker` violates Java naming (CamelCase segments) — painful across imports forever; cheap to fix before first release.
- Service interfaces (`UserService`, `RoleService`, `ActionService`, `ResourceService`) each have exactly one implementation — premature abstraction. The one *real* service (`AuthenticationService`) has **no interface**, inverting the pattern.
- **HTTP coupling leak:** every service returns `ApiResponse<T>` containing `org.springframework.http.HttpStatus` (`UserServiceImpl.java:34-37`, `ResourceServiceImpl.java:68-72`, etc.). The service layer cannot be reused by non-HTTP clients and cannot be unit-tested without the web stack. A service should return domain results; controllers translate to HTTP.
- **Two response envelopes:** `ApiResponse<T>` (with `HttpStatus`) and `ErrorResponse` (with `int statusCode`). Clients must implement two error contracts.

## 1.5 Dependency flow

```
Controller → Service interface → Service impl → Repository → Entity → DB
                                ↘ Mapper (MapStruct)
                                ↘ Exception (RuntimeException subclasses)
Controller → GlobalExceptionHandler (advice)
Filter → JwtService → (SecretKey)
Filter → UserDetailsService → Repository
```

No circular dependencies detected. All injection is constructor-based (`@RequiredArgsConstructor`) — good. The exception classes have zero inheritance depth — every error is a bare `RuntimeException` subclass with a message; no error codes, no cause hierarchy.

---

# 2. Authentication Flow (full request lifecycle trace)

### Register — `POST /auth/register`

```
Client
  └─> AuthController.register (@Valid UserDTO)                    [controller/AuthController.java:20]
        └─> AuthenticationService.register                         [service/impl/AuthenticationService.java:31]
              ├─ existsByEmail()  → RuntimeException("Email already exists.")   → 500  ⚠ should be 409
              ├─ existsByPhone()  → RuntimeException("Phone ...")              → 500  ⚠ should be 409
              ├─ roleRepository.findByRoleKey("ROLE_USER")                     → 500 if role never seeded ⚠
              ├─ passwordEncoder.encode(password)   ← BCrypt ✔
              ├─ userRepository.save(user)
              └─ jwtService.generateToken(email, userId) → JWT (HS256, 24h, hardcoded key)
        └─ 201 CREATED + AuthenticationResponse{token,userId,fullName,email}
```

Race condition: `existsByEmail` → `save` is not atomic (TOCTOU). Two concurrent registrations with the same email → the second hits the DB unique constraint → `DataIntegrityViolationException` → **unhandled → 500**.

### Login — `POST /auth/login`

```
Client
  └─> AuthController.login (@Valid LoginUserDTO)                  [controller/AuthController.java:32]
        └─> AuthenticationService.login                           [service/impl/AuthenticationService.java:69]
              └─ authenticationManager.authenticate(UsernamePasswordAuthenticationToken(email,password))
                    └─ DaoAuthenticationProvider                  [config/ApplicationConfig.java:26]
                          ├─ CustomUserDetailsService.loadUserByUsername  → findByEmail
                          ├─ BCryptPasswordEncoder.matches (comparison) ✔
                          └─ failure → BadCredentialsException → UNHANDLED → 500 ⚠ should be 401
              └─ findByEmail AGAIN (second DB hit)  ⚠ redundant
              └─ generateToken
        └─ 200 OK + token
```

Note: the authenticated principal is discarded; the email is re-fetched from the DB. The `Authentication` result already contains the `CustomUserDetails` — the query is pure waste.

### Legacy signup — `POST /app/v1/user/signup` (protected, but duplicate)

```
Client → UserController.signup → UserServiceImpl.signup
   └─ validate() → existsByEmail/existsByPhone → UserAlreadyExistsException (409) ✔
   └─ userMapper.dtoToUser(userDto)  ← MapStruct copies password UNENCODED ⚠ CRITICAL
   └─ save → account with plain-text password, NO roles
```

### Legacy login — dead code, does not compile (`UserServiceImpl.java:41-61`)
Compares plain text, references nonexistent `UserNotFoundException` and `userInfo`.

### JWT request lifecycle — `Authorization: Bearer <token>`

```
Client
  └─> SecurityFilterChain                                          [config/SecurityConfig.java:22]
        ├─ CSRF off (stateless — correct)
        ├─ SessionManagement → STATELESS
        ├─ JwtFilter (BEFORE UsernamePasswordAuthenticationFilter) [config/JwtFilter.java:31]
        │     ├─ header == null OR !startsWith("Bearer ") → continue (anonymous)
        │     ├─ token = header.substring(7)
        │     ├─ jwtService.extractEmail(token)     ← PARSES + VERIFIES SIGNATURE + EXPIRY HERE
        │     │       ⚠ throws ExpiredJwtException/MalformedJwtException/SignatureException
        │     │       ⚠ NO try/catch → propagates out of the filter → 500
        │     ├─ if username != null && SecurityContext empty:
        │     │     ├─ userDetailsService.loadUserByUsername(email)   → DB hit #1 (every request)
        │     │     ├─ jwtService.validateToken(token, userDetails)   → re-parses token (DB hit #2 for roles via LAZY)
        │     │     └─ set SecurityContext (UsernamePasswordAuthenticationToken)
        │     └─ continue chain
        ├─ Authorization: anyRequest().authenticated()   ← only rule
        └─ ⚠ No AuthenticationEntryPoint → missing/invalid token = 403 with EMPTY body
           ⚠ No AccessDeniedHandler
Controller ← (if authenticated)
```

Problems in this path: (1) token is parsed/verified *twice* (`extractEmail`, then `validateToken` re-parses); (2) every request performs a full DB user load; (3) roles are lazy-loaded per request (`user.getRoles()` inside `getAuthorities()`, `CustomUserDetails.java:20-24`), relying on Spring Boot's default `open-in-view=true` to avoid `LazyInitializationException` — a ticking bomb; (4) exceptions escape as 500s; (5) filter runs even on permitAll paths.

---

# 3. Authorization Flow

```
SecurityFilterChain
  ├─ /auth/**              → permitAll
  ├─ /swagger-ui/**        → permitAll   (no swagger dependency exists! SecurityConfig.java:33)
  ├─ /v3/api-docs/**       → permitAll   (same)
  └─ anyRequest()          → authenticated()
                                 │
                                 ▼
                        ┌─────────────────────┐
                        │  RBAC ENGINE: NONE  │
                        │  Method security:   │
                        │    NOT ENABLED      │
                        │  Roles: unused      │
                        │  Permissions:       │
                        │    data model only  │
                        └─────────────────────┘
```

**Consequences:** any registered `ROLE_USER` can `POST /api/role/create`, `POST /api/actions`, `POST /api/v1/resources` — i.e., **any authenticated user can mint new roles and grant themselves anything**. That is a privilege escalation primitive, not merely missing feature work.

### How it should be implemented (design recommendation)

1. `@EnableMethodSecurity` on `SecurityConfig` (role + expression-based).
2. Keep `CustomUserDetails.getAuthorities()` as-is (role keys `ROLE_*` → `hasRole()` works; raw keys → `hasAuthority()`).
3. Map each controller endpoint:
   - `RoleController` → `@PreAuthorize("hasRole('ADMIN')")`
   - `ResourceController` / `ActionController` create/bulk → `hasRole('ADMIN')`
   - hierarchy GET → `hasRole('USER')`
4. Wire the permission data model into the engine when the template feature lands:
   - Add `@PreAuthorize("@permissionEvaluator.hasPermission(authentication, #resourceKey, #actionKey)")`
   - Implement `PermissionEvaluator` (Spring Security) or a custom `AuthorizationManager<T>` (interface-based, cleaner in modern Spring Security) that resolves `User → roles → templates → ResourceAction → {resource, action}`. The schema is already designed for this — that is presumably why it exists — but nothing connects it to security.
5. Prefer `AuthorizationManager<RequestAuthorizationContext>` over `PermissionEvaluator` for new work (no String-expression parsing, type-safe, unit-testable).

---

# 4. Existing Components

| Component | File | Assessment |
|---|---|---|
| `SecurityConfig` | `config/SecurityConfig.java` | Structurally correct skeleton; missing CORS, entry point, access-denied handler, method security, security headers |
| `ApplicationConfig` | `config/ApplicationConfig.java` | Correct: `DaoAuthenticationProvider` (constructor-injected `UserDetailsService` — modern API), `BCryptPasswordEncoder`, `AuthenticationManager` |
| `JwtFilter` | `config/JwtFilter.java` | Correct shape (OncePerRequestFilter, bearer parsing, context population); fragile exception behavior |
| `JwtService` | `utils/JwtService.java` | Correct jjwt 0.12 API usage (`parser().verifyWith(...)`); hardcoded key, no issuer/audience/jti |
| `CustomUserDetails` | `security/CustomUserDetails.java` | Fine; account flags hardcoded `true`; exposes full `User` entity via `getUser()` (leak vector) |
| `CustomUserDetailsService` | `security/CustomUserDetailsService.java` | Correct; message leaks email (enumeration); no role fetch optimization |
| `AuthenticationService` | `service/impl/AuthenticationService.java` | Correct main flow; raw `RuntimeException`s; TOCTOU; no interface |
| `AuthController` | `controller/AuthController.java` | Correct |
| `UserRepository` | `repository/UserRepository.java` | Complete for current needs |
| `RoleRepository` | `repository/RoleRepository.java` | Complete |
| `ResourceRepository` | `repository/ResourceRepository.java` | Fine; `findAll()` for hierarchy is unscalable |
| `ActionRepository` | `repository/ActionRepository.java` | **ID type mismatch** (`JpaRepository<Action, Integer>` vs entity `Long id`) — compiles, wrong |
| `User`, `Role` entities | `entity/*` | See §7 |
| `GlobalExceptionHandler` | `exception/GlobalExceptionHandler.java` | 8 exception types, 2 bugs, zero security handlers |
| `ResourceServiceImpl` | `service/impl/ResourceServiceImpl.java` | Good engineering (batch validation, race backstops) but `jakarta.transaction.Transactional` (inconsistent), N+1 in hierarchy, HTTP-coupled returns |
| `ActionServiceImpl` | `service/impl/ActionServiceImpl.java` | Good pattern, same coupling issue |

---

# 5. Missing Components

| Component | Purpose | Notes |
|---|---|---|
| `JwtAuthenticationEntryPoint` | 401 + JSON body for missing/invalid token | Without it, clients get empty 403s |
| `CustomAccessDeniedHandler` | 403 + JSON body for authenticated-but-forbidden | |
| `@EnableMethodSecurity` | Enable `@PreAuthorize`/`hasRole` | Zero authorization today |
| `RefreshToken` entity + `/auth/refresh` | Token rotation without re-login | 24h access tokens with no refresh = bad UX + stale sessions |
| Token blacklist/denylist (Redis or DB) | Logout + revocation | None exists; no logout endpoint either |
| `CorsConfigurationSource` bean | Cross-origin access | API is unusable from a browser frontend today |
| `SecurityConstants` | Centralize `"Bearer "`, `"ROLE_USER"`, expirations | Magic values in 3+ files |
| Auth exception handlers | 401/403/409/400 contract | `BadCredentialsException` → 500 today |
| Role seeding | `ROLE_USER` must exist or register 500s | `spring.sql.init.mode=always` has no script |
| Rate limiting / brute-force protection | Login throttling, account lockout | Nothing; OWASP ASVS requirement |
| Audit log (auth events) | Login success/failure, token events | Nothing |
| Password policy / reset / email verification | Account lifecycle | Nothing |
| `User` auditing columns (`createdAt`, `updatedAt`, `@Version`) | Traceability, optimistic locking | `Template` has `@Version`; `User` doesn't |
| `application-prod.yaml` + secret injection | Environment separation | Single file with `root/root` |
| Security test suite | Integration tests for filter/401/403 | Only `contextLoads()` exists |
| CI pipeline | Build gate | None visible in repo |
| `data.sql` seed for permissions | Default role/resource/action set | The permission model has zero seed data |

---

# 6. Architectural Problems

| # | Severity | Problem | Files | Impact | Recommended solution | Why preferred |
|---|---|---|---|---|---|---|
| 1 | **Critical** | Two parallel auth systems; the legacy one stores plain-text passwords and doesn't compile | `service/impl/UserServiceImpl.java:30-61`, `controller/UserController.java:22-42`, `mapper/UserMapper.java:12` | Cannot ship; dual identity; future auth features built twice | Delete `UserController` + `UserServiceImpl` (keep only `AuthenticationService`/`AuthController`), or rewrite legacy path to delegate to `AuthenticationService` | Single ownership of identity lifecycle; removes the plain-text vector at the root |
| 2 | **Critical** | `User.password` column length = 10; BCrypt output = 60 chars | `entity/User.java:36-39` | Registration fails or silently truncates; login impossible | `@Column(length = 60)` | Minimal change; unblocks BCrypt storage |
| 3 | **High** | HTTP types (`HttpStatus`) in service layer; services return `ApiResponse<T>` | `service/impl/*`, `dto/ApiResponse.java` | Services unusable outside web transport; hard unit testing; contract drift between `ApiResponse` and `ErrorResponse` | Services return domain objects/exceptions; controllers build `ApiResponse`/`ErrorResponse`; introduce a single `ApiError` envelope | Clean Architecture; controllers become thin adapters; two envelopes collapse into one |
| 4 | **High** | Permission data model (Resource/Action/Template) not connected to authorization | `entity/ResourceAction.java`, `entity/RoleTemplate.java`, `entity/TemplateResourceAction.java` | The RBAC system the schema implies does not exist; role keys are decorative | Define `AuthorizationManager`/`PermissionEvaluator` backed by the template model; gate endpoints | Leverages schema already designed; type-safe; testable |
| 5 | **High** | Every request = full DB user load + lazy roles load | `config/JwtFilter.java:51`, `security/CustomUserDetails.java:20-24` | DB-bound auth; cannot scale past ~single node with load; relies on `open-in-view` | `JOIN FETCH roles` query in `CustomUserDetailsService`; disable `open-in-view`; add short-TTL user cache | Removes N+1 and the LazyInitializationException time-bomb |
| 6 | **Medium** | `AuthenticationService` has no interface while 4 trivial services do — pattern is inverted | `service/impl/AuthenticationService.java`, `service/*.java` | Inconsistent architecture, no abstraction benefit | Either add `AuthenticationService` interface or drop interfaces for single-impl services | Consistency; YAGNI |
| 7 | **Medium** | `@Transactional` from two different packages (`jakarta.transaction` vs `org.springframework.transaction`) | `service/impl/ResourceServiceImpl.java:13,32,77` vs `service/impl/RoleServiceImpl.java:27` | Semantic drift; both work but communicate confusion | Standardize on `org.springframework.transaction.annotation.Transactional` | One transactional contract |
| 8 | **Medium** | `ActionRepository extends JpaRepository<Action, Integer>` while `Action.id` is `Long` | `repository/ActionRepository.java:9`, `entity/Action.java:30` | Latent `ClassCastException`/wrong-type lookups | Use `JpaRepository<Action, Long>` | Type safety |
| 9 | **Medium** | `JwtService` in `utils`, `JwtFilter` in `config`, empty `DateUtils` | `utils/*`, `config/JwtFilter.java` | Wrong placement; dead placeholder | Move to `security/`; delete `DateUtils` | Cohesion; removes dead code |
| 10 | **Low** | Package name `com.expensive.Expensive.Tracker` | all files | Convention violation, churn-resistant | Rename to `com.expensetracker` before first release | One-time cost now, permanent cost later |

---

# 7. Security Vulnerabilities

| # | Severity | Vulnerability | Evidence | Impact | Fix | Why preferred |
|---|---|---|---|---|---|---|
| 1 | **Critical** | Hardcoded JWT signing secret | `utils/JwtService.java:19-20` | Anyone with repo access can forge tokens for any user | Externalize via env var; validate length ≥ 32 bytes at startup; rotate immediately | Only viable defense; source control is not a secret store |
| 2 | **Critical** | Plain-text password persistence & comparison | `service/impl/UserServiceImpl.java:30-38,47` | Credential theft if DB leaks; the file doesn't compile anyway | Delete legacy path (see Arch #1) | Remove the root cause, not patch it |
| 3 | **Critical** | Unauthenticated users can create roles/resources/actions (broken access control) | `SecurityConfig.java:36`, `RoleController.java:24-29`, `ResourceController.java:24-51`, `ActionController.java:20-29` | Privilege escalation: any user can mint `ROLE_ADMIN`-equivalent roles | `@EnableMethodSecurity` + endpoint-level `@PreAuthorize` | OWASP Top 10 #1; requires the missing engine from §3 |
| 4 | **High** | Invalid/expired tokens → **500 Internal Server Error** | `config/JwtFilter.java:47` (no try/catch), `GlobalExceptionHandler` (no `JwtException`/`AuthenticationException` handler) | Client confusion; monitors flooded with 500s; automated scanners flag | try/catch in filter → 401 via entry point | Correct HTTP semantics; entry point also fixes missing-token case |
| 5 | **High** | Login failure → 500 (`BadCredentialsException` unhandled); register duplicates → 500 | `service/impl/AuthenticationService.java:33-43,79-80`; `GlobalExceptionHandler.java` | No API contract for the two most common auth failures | 401 for bad credentials; 409 for duplicates; catch `DataIntegrityViolationException` for races | Stable, standard semantics; stops leaking 500s |
| 6 | **High** | No `AuthenticationEntryPoint`/`AccessDeniedHandler` | `config/SecurityConfig.java` | Missing token → empty 403 body | Custom handlers returning `ErrorResponse` JSON | Uniform error contract |
| 7 | **High** | No rate limiting / brute-force protection / account lockout | entire auth path | Credential stuffing at any scale | Bucket4j/Resilience4j at service + LB/API-gateway rate limit; lockout after N failures | Defense-in-depth; cheap |
| 8 | **High** | TOCTOU race on registration | `AuthenticationService.java:33-54` | Concurrent duplicate registration → 500 or duplicate rows blocked awkwardly | Rely on DB unique constraints; catch `DataIntegrityViolationException` → 409 | DB is the only correct arbiter |
| 9 | **Medium** | User enumeration: `UsernameNotFoundException("User not found with email : " + email)` | `security/CustomUserDetailsService.java:23` | Enumeration via timing/message; also logged | Generic message; log detail server-side | Standard OWASP practice |
| 10 | **Medium** | No refresh token, no revocation, no logout — 24h unforgeable sessions | `JwtService.java:22-23` | Compromised token lives 24h; users cannot be signed out; no rotation | Refresh token rotation + denylist | Industry-standard session lifecycle |
| 11 | **Medium** | No `issuer`/`audience`/`jti` claims; no clock-skew tolerance | `JwtService.java:44-51` | Replay across services; no jti → no denylisting possible | Add claims; `setAllowedClockSkewSeconds`; validate issuer/audience | Enables revocation & multi-service trust |
| 12 | **Medium** | `Token parsed twice` per request | `JwtFilter.java:47,53` | 2x crypto cost per request; at scale measurable | Parse once; reuse claims | Performance + simplicity |
| 13 | **Medium** | `CustomUserDetails.getUser()` exposes the full JPA entity to controllers | `security/CustomUserDetails.java:66-68` | Accidental `User` serialization leaks password hash + phone | Expose only primitives (`getUserId`, `getFullName`) | Principle of least exposure |
| 14 | **Medium** | DB creds `root/root` in committed config | `src/main/resources/application.yaml:6-8` | Straight credential theft if repo leaks | Env vars + profile separation | Standard |
| 15 | **Medium** | `GlobalExceptionHandler` bug: 404 returned with `statusCode: 409` | `exception/GlobalExceptionHandler.java:36-47` | Broken client contract | Return 409 | Correctness |
| 16 | **Medium** | `UserMapper.userToDto` maps `password` into the DTO (MapStruct ignores `@JsonIgnore`) | `mapper/UserMapper.java:16` | Any future use leaks password hash | Remove/annotate `@Mapping(target="password", ignore=true)` | MapStruct is a trap; make it explicit |
| 17 | **Low** | `substring(8, ...)` off-by-one + duplicate token parsing in `UserController.update` | `controller/UserController.java:38-39` | Would fail auth; dead code anyway | Delete (covered by Arch #1) | Removes the whole broken method |
| 18 | **Low** | Actuator endpoints unconstrained (`management` unconfigured) | `pom.xml:34-36` | Info/health exposure defaults are safe-ish but unmanaged | Explicit `exposure.include=health,info` + auth | Explicit > implicit |
| 19 | **Low** | `@Size(max=16)` password cap discourages passphrases; no complexity rule | `dto/UserDTO.java:34` | Weak credential hygiene | Raise max, add complexity pattern | NIST 800-63B alignment |

---

# 8. Code Smells

| # | Severity | Smell | Location |
|---|---|---|---|
| 1 | High | Dead, non-compiling code shipped to main (`UserServiceImpl.login`, `updateProfile` inverted condition `if(!userExists.isEmpty())`, `deactivateProfile` returns `null`) | `UserServiceImpl.java:41-84` |
| 2 | High | Duplicate register/login across two services | `AuthenticationService` vs `UserServiceImpl` |
| 3 | Medium | `DateUtils` empty class; `ResourceMapper.createRequestToEntity` unused | `utils/DateUtils.java`, `mapper/ResourceMapper.java:13` |
| 4 | Medium | Magic strings: `"Bearer "`, `"ROLE_USER"`, `"Email already exists."` scattered | `JwtFilter.java:40`, `AuthenticationService.java:41` |
| 5 | Medium | `@Data` on JPA `User` — mutable, all-fields `equals/hashCode` | `entity/User.java:18` |
| 6 | Medium | Duplicate `maven-compiler-plugin` declaration in `pom.xml` | `pom.xml:133-192` |
| 7 | Medium | README is a stale template describing an expense app; no API/security docs | `README.md` |
| 8 | Medium | No `@Version`/audit columns on `User` (lock/retry impossible) | `entity/User.java` |
| 9 | Low | `Resource` entity throws `ResourceKeyNotFoundException` for a validation failure | `entity/Resource.java:80` |
| 10 | Low | `ApiResponse.timestamps` typos in field naming (`timeStamps`) | `dto/ApiResponse.java:21` |
| 11 | Low | `ResourceServiceImpl.getResourceHierarchy` N+1 on `resource.getParent()` per row | `service/impl/ResourceServiceImpl.java:210-218` |
| 12 | Low | Naming: `RoleResponseDTO` vs `AuthenticationResponse` vs `UserDTO` — no suffix convention | `dto/*` |

---

# 9. Performance Issues

| # | Severity | Issue | Evidence | Impact at scale | Fix |
|---|---|---|---|---|---|
| 1 | **High** | Full user row + lazy roles loaded from DB on **every** authenticated request | `JwtFilter.java:51`, `CustomUserDetails.java:20-24` | At 10M users: auth DB read per request; cacheless; DB becomes bottleneck | `@EntityGraph`/`JOIN FETCH` roles; short-TTL cache (Caffeine); `open-in-view: false` |
| 2 | **High** | JWT parsed & signature-verified twice per request | `JwtFilter.java:47` + `JwtService.java:102` | 2x HMAC cost on every request | Parse once, reuse `Claims` |
| 3 | Medium | `findByEmail` executed twice per login | `AuthenticationService.java:78` (after `loadUserByUsername` already loaded it) | Login is 2x DB round-trips | Reuse the `Authentication` principal |
| 4 | Medium | `getResourceHierarchy` loads all resources + N+1 parent loads | `ResourceServiceImpl.java:189-218` | Degrades with resource count; no pagination anywhere in app | `findAll` with fetch join, or tree query; page/limit params |
| 5 | Medium | BCrypt cost fixed at default (10) with no config knob | `ApplicationConfig.java:22` | Login CPU cost ~100ms/core; brute-force amplification inverts at scale | Configurable `BCryptPasswordEncoder(strength, SecureRandom)`; consider Argon2id for new work |
| 6 | Medium | `show-sql: true` + `format_sql` in config | `application.yaml:14-17` | Log volume explosion; sensitive binds in logs | Profile-gated (dev only) |
| 7 | Low | No connection pool tuning, no read replica config | `application.yaml:5-9` | Defaults suffice only at small scale | HikariCP explicit config + replica `@DataSource` for read paths |
| 8 | Low | Single `findAll()`-based hierarchy; no caching of reference data | `ResourceServiceImpl.java:189` | Permission model reads will repeat it | Caffeine cache on immutable reference data (keys/actions) |

---

# 10. Scalability Issues (10M users, multi-node)

| # | Severity | Issue | Analysis | Fix |
|---|---|---|---|---|
| 1 | **High** | Stateless JWT — the one thing that scales — is undermined by a **per-request DB hit** | Horizontal scaling works only if auth is CPU-cheap and DB-free; current design converts every request into a DB query | Cache user+roles with short TTL; use `claims` for non-sensitive context; keep DB check only where freshness matters |
| 2 | **High** | No token revocation infrastructure | With multiple nodes, in-memory denylists don't work; revocation must be shared | Redis denylist (jti + userId); distributed cache is the shared state JWT's statelessness delegates to |
| 3 | **High** | No refresh-token rotation design | Long-lived access tokens (24h) with no rotation = theft window; rotation needs shared state (Redis) or hashed-token DB rows | Rotation with reuse-detection (OWASP recommendations) |
| 4 | Medium | No rate limiting anywhere | Brute-force/credential-stuffing lands directly on BCrypt (expensive to compute) — attacker makes *you* pay CPU | Gateway-level + in-app bucket limiter; per-IP + per-account |
| 5 | Medium | Single MySQL instance; `ddl-auto: update` | Schema drift across nodes; no read scaling | Flyway + read replicas; `ddl-auto: validate` |
| 6 | Medium | No caching layer for reference data (roles, resources, actions) | Permission checks will hammer DB | Caffeine (single node) → Redis (multi-node) |
| 7 | Low | Monolith is fine — do **not** microservice the auth path | If you do split: extract an Auth service behind a gateway *only when* traffic justifies it | Keep monolith; enforce modularity (`security`/`identity` packages) until then |
| 8 | Low | JWT secret shared across future services | Multi-service trust requires shared key (HS256) or asymmetric RS256/ES256 | Plan for RS256 keypair now; kid header; JWKS later |

---

# 11. Production Readiness Score

| Area | Score /10 | Rationale |
|---|---|---|
| Authentication | 3 | JWT+BCrypt flow is correct but: secret hardcoded, 500s on failure, no lockout/rate-limit, legacy plain-text path |
| Authorization | 0 | No method security, no role enforcement, permission model unused |
| JWT | 2 | Correct jjwt API; hardcoded key, no jti/issuer/audience, no refresh/revocation, double parsing |
| Architecture | 3 | Two parallel auth systems; HTTP coupled to services; permission model orphaned |
| Security | 1 | Hardcoded secrets, broken access control, plain-text passwords (in dead code), no entry point |
| Code Quality | 3 | Does not compile; good exception-package discipline and race handling in ResourceService, undone by dead code |
| Maintainability | 3 | MapStruct+DTO discipline is decent; dual envelopes, magic values, stale README hurt |
| Scalability | 2 | Stateless JWT ✔ but per-request DB, no cache/Redis/rate-limit |
| Performance | 3 | Double parse, double login query, N+1 hierarchy |
| Testing | 0 | One `contextLoads` test; zero security coverage |
| Documentation | 1 | README describes a different product; no API/security docs |

**Overall: 19 / 100** — Pre-alpha. The correct skeleton exists but the load-bearing walls (authorization, error semantics, secret management, single identity path) are missing, and the tree does not compile.

---

# 12. Prioritized Roadmap

## Phase 0 — Make it build & stop the bleeding (1–2 days)
1. Delete legacy identity path: `UserController`, `UserService`, `UserServiceImpl`, `UserMapper` signup/login/update/deactivate methods → single auth path through `AuthenticationService`/`AuthController`. *(Severity Critical — removes plain-text passwords and compile errors.)*
2. Fix `User.password` length → 60. *(Critical — BCrypt storage.)*
3. Externalize JWT secret + expiration to env-config; fail fast if weak. *(Critical.)*
4. Externalize DB credentials; add `application-prod.yaml`. *(High.)*

## Phase 1 — Correct security semantics (3–5 days)
5. Add `JwtAuthenticationEntryPoint` (401) + `CustomAccessDeniedHandler` (403) with the `ErrorResponse` envelope.
6. try/catch in `JwtFilter` → 401 on expired/malformed/bad-signature tokens; parse the token once.
7. Add handlers: `BadCredentialsException`→401, `DataIntegrityViolationException`→409, `MethodArgumentNotValidException`→400, generic `Exception`→500 (sanitized). Fix the 404-vs-409 bug in `GlobalExceptionHandler`.
8. Replace `RuntimeException`s in `AuthenticationService` with typed exceptions (`UserAlreadyExistsException`, `RoleNotFoundException`, `BadCredentialsException`).

## Phase 2 — Authorization engine (1 week)
9. `@EnableMethodSecurity`; annotate `RoleController`/`ResourceController`/`ActionController` with `@PreAuthorize("hasRole('ADMIN')")` etc.
10. Seed `ROLE_USER`, `ROLE_ADMIN`, and the resource/action catalog (`data.sql` or a `CommandLineRunner`).
11. Design `AuthorizationManager` over `ResourceAction`/`Template` for fine-grained permission checks; gate endpoints with custom expressions.

## Phase 3 — Session lifecycle & scale (1–2 weeks)
12. Refresh-token rotation with reuse detection (hashed tokens in DB or Redis) + `POST /auth/refresh` + `POST /auth/logout`.
13. jti claim + Redis denylist for revocation.
14. `JOIN FETCH roles` in `CustomUserDetailsService`; `open-in-view: false`; Caffeine user cache (short TTL).
15. Rate limiting (in-app + gateway); account lockout after N failures; audit log for auth events.
16. CORS configuration bean.
17. Flyway migrations; `ddl-auto: validate`; disable `show-sql` in prod; restrict actuator.

## Phase 4 — Hardening & testing (ongoing)
18. Security integration tests: register → login → protected call; expired token → 401; role-denied → 403; duplicate register → 409. Testcontainers + MySQL.
19. CI build gate (`mvn verify`), static analysis (SpotBugs/OWASP dependency check).
20. Unify the error envelope; remove `ApiResponse` from service layer; align DTO/validation (`RegisterRequest` vs `UpdateProfileRequest`).
21. Password policy (NIST-aligned), email verification, password reset.
22. Plan RS256/JWKS if multi-service trust is on the horizon; monitor with structured logs + metrics (Micrometer + tracing).

---

## Appendix A — Endpoint inventory & required access level

| Method | Path | Current | Required |
|---|---|---|---|
| POST | `/auth/register` | public | public (rate-limited) |
| POST | `/auth/login` | public | public (rate-limited) |
| POST | `/app/v1/user/signup` | protected | **remove** (duplicate) |
| POST | `/app/v1/user/update` | unmapped dead code | **remove** |
| POST | `/api/role/create` | any authenticated | `ROLE_ADMIN` |
| GET | `/api/role/{id}` | any authenticated | `ROLE_ADMIN` |
| POST | `/api/actions` | any authenticated | `ROLE_ADMIN` |
| POST | `/api/v1/resources` | any authenticated | `ROLE_ADMIN` |
| POST | `/api/v1/resources/bulk` | any authenticated | `ROLE_ADMIN` |
| GET | `/api/v1/resources/hierarchy` | any authenticated | any authenticated |
| `/swagger-ui/**`, `/v3/api-docs/**` | — | permitAll, but no swagger dependency | remove or add springdoc |

## Appendix B — Scoring rubric note
Scores assume the review standard of a Google internal design review: *deployability today, correctness of failure semantics, security posture, and the ability to evolve at 10M users* — not "does it run locally". Under that standard, no score above 3 is achievable while the code does not compile and the signing key is in git history.
