# Implementation Roadmap — Production-Ready Spring Security + JWT

**Author role:** Lead Staff Engineer (review → implementation planning)
**Basis:** `ARCHITECTURE_REVIEW_REPORT.md` (2026-08-04 audit)
**Constraint:** This document is the plan; no code changes are part of this deliverable.
**Guiding principles:** Simplicity first, incremental delivery, correctness of failure semantics before features, no over-engineering, nothing added without a clear architectural reason.

---

## 1. Current State

### 1.1 What already exists (keep, harden)

| Asset | State | Verdict |
|---|---|---|
| `SecurityConfig` (stateless, CSRF off, filter chain) | Correct skeleton | Keep, extend |
| `ApplicationConfig` (BCrypt, `DaoAuthenticationProvider`, `AuthenticationManager`) | Correct | Keep as-is |
| `JwtFilter` (bearer parsing, context population) | Correct shape | Keep, refactor (error handling, single parse) |
| `JwtService` (jjwt 0.12 API usage) | Correct API usage | Keep, refactor (secret, claims, moves to `security`) |
| `CustomUserDetails` / `CustomUserDetailsService` | Correct | Keep, modify (role fetch join, remove `getUser()`) |
| `AuthenticationService` / `AuthController` | Correct main flow | Keep, modify (typed exceptions, interface) |
| `User` / `Role` entities, repositories | Working | Keep, patch (`password` length 60) |
| `GlobalExceptionHandler` + typed exceptions | Partial | Keep, expand |
| DTOs (`LoginUserDTO`, `UserDTO`, `AuthenticationResponse`) | Working | Keep, rename/adjust validation |
| `ResourceService`/`ActionService`/`RoleService` impls | Good patterns | Keep unchanged except `@PreAuthorize` wiring + transactional annotation consistency |

### 1.2 What should be deleted

| File | Reason |
|---|---|
| `controller/UserController.java` | Legacy duplicate auth (signup, broken unmapped `update` with `substring(8)` bug) |
| `service/UserService.java` | Only exists to back `UserController` |
| `service/impl/UserServiceImpl.java` | Does not compile; plain-text passwords; dead `login`/`deactivateProfile` |
| `mapper/UserMapper.java` | `userToDto` leaks `password`; signup mapper was the plain-text vector |
| `dto/UpdateProfileDTO.java` | Near-duplicate of `UserDTO`; fold into a single profile DTO |
| `utils/DateUtils.java` | Empty placeholder |
| `dto/ApiResponse.java` | Merge into single error/success envelope contract (see §4) |

### 1.3 What should be refactored

- `utils/JwtService` → `security/JwtService` (+ secret externalization, issuer/audience, single-parse API)
- `config/JwtFilter` → `security/JwtFilter` (+ try/catch → 401, parse-once)
- `AuthenticationService` → interface + impl in `auth` package (+ typed exceptions, no TOCTOU)
- `GlobalExceptionHandler` → add security/validation handlers; fix the 404-vs-409 bug
- `User.password` column length 10 → 60
- `application.yaml` → profile split, env-var injection, remove `show-sql` from prod
- `ActionRepository` ID type `Integer` → `Long`
- Naming: `UserDTO`→`RegisterRequest`, `LoginUserDTO`→`LoginRequest`, `AuthenticationResponse`→`AuthResponse`
- `@Transactional` standardization (`org.springframework.transaction` everywhere)

### 1.4 What should remain unchanged

- `ApplicationConfig` beans and wiring
- `SecurityConfig` core chain structure (stateless, CSRF off, filter position)
- `AuthController` endpoints and route design
- `User`/`Role` entity relationship design (`ManyToMany`, lazy)
- `ResourceService`/`ActionService` race-condition backstops (good engineering)
- `CustomUserDetails` authorities mapping (`roleKey` → `SimpleGrantedAuthority`)

---

## 2. Implementation Roadmap

### Phase 0 — Restore the build & delete the legacy auth path

- **Goal:** Compile cleanly; one identity path exists; no plain-text passwords anywhere.
- **Files to modify:** none beyond deletions.
- **Delete:** `UserController.java`, `UserService.java`, `UserServiceImpl.java`, `UserMapper.java`, `UpdateProfileDTO.java`, `DateUtils.java`.
- **Modify:** `entity/User.java` (password length 60 — required for BCrypt storage), `repository/ActionRepository.java` (ID type Long).
- **New classes:** none.
- **Complexity:** S (half-day).
- **Dependencies:** none — this is the foundation.

### Phase 1 — JWT implementation hardening

- **Goal:** No hardcoded secrets; tokens verified once per request; invalid tokens return 401, never 500.
- **Modify:** `security/JwtService` (moved): secret from `@Value("${jwt.secret}")` with startup validation (≥32 bytes), `jwt.expiration` configurable, add `issuer`/`audience`/`jti`, add `parseClaims(token)` returning a single `Claims` object used by both extraction and validation; `security/JwtFilter` (moved): try/catch `ExpiredJwtException`/`JwtException` → `AuthenticationEntryPoint` (401), parse once.
- **New classes:** `security/SecurityConstants.java` (bearer prefix, role keys, header names — kills magic strings).
- **Refactor:** `CustomUserDetailsService` → `findByEmailWithRoles` (`JOIN FETCH roles`) to kill the per-request N+1; set `spring.jpa.open-in-view: false` as a gate for correctness.
- **Complexity:** S–M (1 day).
- **Dependencies:** Phase 0 (moving files is cleanest on a compiling tree).

### Phase 2 — Error contract & security exception semantics

- **Goal:** Every auth failure has a correct, documented HTTP status with a JSON body.
- **New classes:**
  - `security/JwtAuthenticationEntryPoint` — 401 + `ErrorResponse` (missing/invalid/expired token).
  - `security/CustomAccessDeniedHandler` — 403 + `ErrorResponse` (authenticated but forbidden).
  - `exception/AuthErrorCode` (enum) or simply extend the typed-exception set with `UserAlreadyExistsException` reuse; add `exception/BadCredentialsException`-facing handler.
- **Modify:** `SecurityConfig` (register entry point + handler), `GlobalExceptionHandler` (add: `BadCredentialsException`→401, `MethodArgumentNotValidException`→400 with field map, `DataIntegrityViolationException`→409, generic `Exception`→500 sanitized; fix the 404-with-409-statusCode bug), `AuthenticationService` (typed exceptions instead of `RuntimeException`; catch `DataIntegrityViolationException` on save → 409).
- **Complexity:** M (1 day).
- **Dependencies:** Phase 1 (entry point must be called from `JwtFilter`).

### Phase 3 — Role-based authorization

- **Goal:** Method security enabled; admin endpoints locked; roles seeded.
- **Modify:** `SecurityConfig` (`@EnableMethodSecurity`), `RoleController`/`ResourceController`/`ActionController` (`@PreAuthorize("hasRole('ADMIN')")` on create/update paths; hierarchy GET stays authenticated).
- **New classes:** `config/DataInitializer` (or `data.sql`): seeds `ROLE_USER`, `ROLE_ADMIN`.
- **Refactor:** `CustomUserDetails` (remove `getUser()`; keep `getUserId()`, `getFullName()`).
- **Complexity:** M (2 days).
- **Dependencies:** Phases 0–2 (secure error semantics must exist before denying access).

### Phase 4 — Account lifecycle basics

- **Goal:** Logout semantics and a sane session window without over-engineering.
- **Decision (MVP):** access token expiry lowered to a configurable window (default 15–30 min); logout = client discards token + (optional) denylist of `jti` in-memory (single node, TTL-bound). **No refresh tokens in MVP** — re-login is acceptable for an internal tool and avoids the Redis dependency.
- **New classes:** `AuthController#logout` (accepts token, blacklists `jti` via `TokenDenylistService`), `service/TokenDenylistService` (in-memory Caffeine, TTL = token lifetime).
- **Complexity:** M (2 days).
- **Dependencies:** Phase 1 (jti claim must exist for denylisting), Phase 3 (auth controller ownership settled).

### Phase 5 — Production hardening

- **Goal:** Deployable to a single prod node with secrets managed externally.
- **New/modify:** `application-prod.yaml` (env-var datasource, `ddl-auto: validate`, `show-sql: false`, logging levels, actuator exposure `health,info`), Flyway migrations replacing `ddl-auto: update`, CORS `CorsConfigurationSource` bean (allowlist origins), security headers (defaults via Spring Security; add `X-Content-Type-Options` etc. explicitly), login rate limiting (in-app, simple per-IP+account counter — Bucket4j or a hand-rolled Caffeine bucket).
- **Complexity:** M (2 days).
- **Dependencies:** Phases 0–4 (hardening assumes the semantics are correct).

### Phase 6 — Scalability (recommended later)

- **Goal:** Multi-node readiness with caching, no architectural rewrites.
- **Modify:** `CustomUserDetailsService` user+roles cache (Caffeine, short TTL 5 min, eviction on profile update), reference-data cache for roles/resources/actions, `ResourceServiceImpl.getResourceHierarchy` fetch-join + pagination.
- **Complexity:** M (2 days).
- **Dependencies:** Phase 5.

### Phase 7 — Internet-scale (future optimization — do NOT build now)

- Redis-backed denylist + refresh-token rotation with reuse detection.
- Distributed rate limiting (gateway/WAF).
- RS256/JWKS asymmetric keys for cross-service trust.
- OAuth2/OpenID Connect delegation only if a third-party identity provider becomes a requirement.
- **Trigger criteria:** >1 app node serving auth traffic, or a second service that must verify tokens, or a compliance requirement.

---

## 3. Dependency Graph

```
Phase 0
└─ AuthController ──→ AuthenticationService ──→ UserRepository
        │                    │                        └─→ MySQL
        │                    ├─→ RoleRepository ──→ MySQL (ROLE_USER lookup)
        │                    ├─→ PasswordEncoder (BCrypt)
        │                    └─→ JwtService ──→ SecretKey (@Value jwt.secret)
Phase 1
JwtService ──→ (issuer/audience/jti validation, single parse)
JwtFilter ──→ JwtService ──→ CustomUserDetailsService ──→ UserRepository (JOIN FETCH roles)
   │              │
   └──────────────┴─→ JwtAuthenticationEntryPoint (401)      [Phase 2]
Phase 2
SecurityConfig ──→ JwtFilter
               ├─→ JwtAuthenticationEntryPoint
               └─→ CustomAccessDeniedHandler
GlobalExceptionHandler ──→ ErrorResponse (single envelope)
Phase 3
SecurityConfig (@EnableMethodSecurity)
   └─→ @PreAuthorize on RoleController / ResourceController / ActionController
DataInitializer ──→ RoleRepository (seeds ROLE_USER, ROLE_ADMIN)
Phase 4
AuthController.logout ──→ TokenDenylistService (Caffeine, jti)
Phase 5
SecurityConfig ──→ CorsConfigurationSource
Flyway ──→ schema migrations   |   application-prod.yaml ──→ env secrets
Phase 6
CustomUserDetailsService ──→ UserCache (Caffeine)
```

---

## 4. Delete / Refactor / Rename — Exact Operations

### 4.1 Delete (files)

1. `src/main/java/com/expensive/Expensive/Tracker/controller/UserController.java`
2. `src/main/java/com/expensive/Expensive/Tracker/service/UserService.java`
3. `src/main/java/com/expensive/Expensive/Tracker/service/impl/UserServiceImpl.java`
4. `src/main/java/com/expensive/Expensive/Tracker/mapper/UserMapper.java`
5. `src/main/java/com/expensive/Expensive/Tracker/dto/UpdateProfileDTO.java`
6. `src/main/java/com/expensive/Expensive/Tracker/utils/DateUtils.java`
7. `src/main/java/com/expensive/Expensive/Tracker/dto/ApiResponse.java` — **merge**, see 4.2

### 4.2 Merge

| Merge | Result |
|---|---|
| `ApiResponse` (success) + `ErrorResponse` (failure) | One envelope per path: success endpoints return the `ApiResponse<T>` shape (kept, renamed fields fixed: `timeStamps`→`timestamp`); failures always return `ErrorResponse`. Services stop returning `HttpStatus` — controllers map domain results → envelopes. |
| `UpdateProfileDTO` fields into `RegisterRequest`-adjacent `UpdateProfileRequest` | Single profile DTO; controller reuses validation |
| `AuthenticationService` logic + `AuthController` profile endpoints | `AuthController` gains `GET /auth/me`, `PUT /auth/profile` (replaces dead `UserController.update`) |

### 4.3 Rename / Move

| From | To | Why |
|---|---|---|
| `com.expensive.Expensive.Tracker` | `com.expensetracker` | Java naming convention; do it now, never again (low churn while small) |
| `utils/JwtService.java` | `security/JwtService.java` | Cohesion |
| `config/JwtFilter.java` | `security/JwtFilter.java` | Cohesion |
| `dto/UserDTO.java` | `dto/auth/RegisterRequest.java` | Clear intent |
| `dto/LoginUserDTO.java` | `dto/auth/LoginRequest.java` | Clear intent |
| `dto/AuthenticationResponse.java` | `dto/auth/AuthResponse.java` (+ add `tokenType: "Bearer"`, `expiresIn`) | Completeness of the auth contract |

### 4.4 Duplicate-auth guarantee

After Phase 0, grep for `findByEmail` call sites: the only consumers must be `AuthenticationService` and `CustomUserDetailsService`. Any controller that manually parses `Authorization` headers is removed (`UserController.update`). Add a build gate (ArchUnit rule, optional) forbidding `HttpHeaders.AUTHORIZATION` reads outside `security/` package — this is the architectural invariant that prevents regression.

---

## 5. Final Package Structure (target)

```
com.expensetracker
├── ExpensiveTrackerApplication.java
├── config/
│   ├── SecurityConfig.java            (filter chain, method security, entry point, CORS)
│   ├── ApplicationConfig.java         (unchanged)
│   ├── DataInitializer.java           (role seeding — Phase 3)
│   └── CorsConfig.java                (Phase 5)
├── security/
│   ├── JwtService.java                (Phase 1 hardening)
│   ├── JwtFilter.java                 (Phase 1 hardening)
│   ├── JwtAuthenticationEntryPoint.java      (Phase 2)
│   ├── CustomAccessDeniedHandler.java        (Phase 2)
│   ├── CustomUserDetails.java
│   ├── CustomUserDetailsService.java
│   └── SecurityConstants.java
├── auth/                              (identity module — single owner)
│   ├── AuthController.java            (register, login, logout, me, profile)
│   ├── AuthenticationService.java     (interface)
│   ├── impl/AuthenticationService.java (impl)
│   └── dto/
│       ├── RegisterRequest.java
│       ├── LoginRequest.java
│       ├── UpdateProfileRequest.java
│       └── AuthResponse.java
├── user/                              (if separate profile concerns emerge later)
├── role/   controller/ service/ service/impl/ dto/
├── resource/ controller/ service/ service/impl/ dto/
├── action/  controller/ service/ service/impl/ dto/
├── template/                          (future — permission model)
├── repository/
├── entity/
├── mapper/
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ErrorResponse.java
│   └── <typed exceptions>
├── enums/
└── common/
    └── ApiResponse.java               (single success envelope)
```

Feature packages (`auth`, `role`, `resource`, `action`) group controller+service+dto by domain; `security` is the cross-cutting security kernel; `config` holds only wiring.

---

## 6. Class-by-Class Plan

| Class | Responsibility | Public API | Dependencies | Action |
|---|---|---|---|---|
| `SecurityConfig` | Chain, method security, entry point, CORS | `securityFilterChain(HttpSecurity)` | `JwtFilter`, `AuthenticationProvider`, entry point, access-denied handler | **Modify** (add `@EnableMethodSecurity`, handlers, CORS) |
| `ApplicationConfig` | Encoder/provider/manager beans | `passwordEncoder()`, `authenticationProvider()`, `authenticationManager()` | `CustomUserDetailsService` | **Keep** (unchanged) |
| `JwtService` | Issue/parse/validate tokens | `generateToken(email,userId)`, `extractEmail(Claims)`, `extractUserId(Claims)`, `validateToken(Claims,UserDetails)`, `parseClaims(token)` | `@Value` secret/expiry, `SecurityConstants` | **Modify** (move to `security`, externalize secret, add issuer/audience/jti, parse-once API) |
| `JwtFilter` | Per-request authentication | `doFilterInternal(...)` | `JwtService`, `UserDetailsService`, entry point | **Modify** (move to `security`, try/catch → 401, single parse) |
| `CustomUserDetails` | Spring principal | `getAuthorities()`, `getUsername()`, `isEnabled()`, `getUserId()`, `getFullName()` | `User` | **Modify** (drop `getUser()`, `getPassword()` stays internal) |
| `CustomUserDetailsService` | Load user by email | `loadUserByUsername(String)` | `UserRepository` | **Modify** (`JOIN FETCH roles`, generic not-found message) |
| `AuthenticationService` (interface + impl) | Register/login/logout/me/profile | `register(RegisterRequest)`, `login(LoginRequest)`, `logout(String token)`, `getProfile(Long)`, `updateProfile(Long, UpdateProfileRequest)` | `UserRepository`, `RoleRepository`, `PasswordEncoder`, `JwtService`, `AuthenticationManager`, `TokenDenylistService` | **Modify** (interface added; typed exceptions; moved to `auth`) |
| `AuthController` | Auth HTTP surface | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `PUT /auth/profile` | `AuthenticationService` | **Modify** (absorb profile endpoints) |
| `UserController` / `UserService` / `UserServiceImpl` / `UserMapper` | — | — | — | **Delete** (Phase 0) |
| `RoleController` | Role admin surface | `createRole`, `getRole` | `RoleService` | **Modify** (add `@PreAuthorize`) |
| `ResourceController` / `ActionController` | Resource/action admin | existing | existing services | **Modify** (add `@PreAuthorize`) |
| `User` entity | Identity aggregate | getters/setters/builder | `Role` (ManyToMany) | **Modify** (password length 60; add `createdAt/updatedAt/@Version` in Phase 5) |
| `Role` entity | Role catalog | getters/setters | `User` | **Keep** |
| `Resource` / `Action` / `ResourceAction` / `Template` / `RoleTemplate` / `TemplateResourceAction` | Permission catalog + template model | existing | — | **Keep** (Phase 3 wires them into authorization) |
| `UserRepository` | User persistence | `findByEmail`, `findByPhone`, `existsByEmail`, `existsByPhone`, `findByEmailWithRoles` (new) | — | **Modify** (add fetch-join query) |
| `RoleRepository` | Role persistence | `findByRoleKey`, `existsByRoleKey` | — | **Keep** |
| `ResourceRepository` / `ActionRepository` | Reference data | existing | — | **Modify** (`ActionRepository` ID → `Long`) |
| `GlobalExceptionHandler` | Uniform error mapping | handlers per exception type | `ErrorResponse` | **Modify** (add auth/validation/constraint handlers; fix 404 bug) |
| `ErrorResponse` | Error envelope | builder | — | **Keep** (single error contract) |
| `ApiResponse` | Success envelope | builder | — | **Modify** (field rename `timeStamps`→`timestamp`; service layer stops returning it — controllers adapt) |
| `TokenDenylistService` | jti denylist (Phase 4) | `deny(jti, ttl)`, `isDenied(jti)` | Caffeine | **New** |
| `JwtAuthenticationEntryPoint` | 401 contract | `commence(...)` | `ErrorResponse` | **New** (Phase 2) |
| `CustomAccessDeniedHandler` | 403 contract | `handle(...)` | `ErrorResponse` | **New** (Phase 2) |
| `DataInitializer` | Seed roles | `run(...)` | `RoleRepository` | **New** (Phase 3) |
| `SecurityConstants` | Centralized literals | constants | — | **New** (Phase 1) |
| `DateUtils` | — | — | — | **Delete** |

---

## 7. Authorization Design (RBAC + Permission Catalog)

### 7.1 How the model works together

```
User ──(ManyToMany)── Role ──(RoleTemplate, allowed)── Template ──(TemplateResourceAction, allowed)── ResourceAction ──(ManyToMany)── Resource + Action
```

Permission check at runtime:
1. `User.getRoles()` → set of role keys (authorities in `CustomUserDetails`).
2. Coarse check: `@PreAuthorize("hasRole('ADMIN')")` — role-level gate (MVP, Phase 3).
3. Fine-grained check (when templates ship): for a `(resourceKey, actionKey)` request —
   - resolve `ResourceAction` for the pair;
   - resolve `RoleTemplate` rows where `role ∈ user.roles` and `allowed = true`;
   - for each such `templateId`, resolve `TemplateResourceAction` rows for `(templateId, resourceActionId)` where `allowed = true`;
   - grant if any matches.

### 7.2 Assessment of the current model

**Sufficient for the planned feature** with one gap:
- There is no direct `Role → ResourceAction` grant table — permissions are expressed *only* through templates. This is a legitimate design (permission sets = templates), but it forces every grant through a template row. If product needs ad-hoc grants ("give this one role this one action"), add a `RolePermission(role_id, resource_action_id, allowed)` join table later — do not build it now.
- The `allowed` boolean on both `RoleTemplate` and `TemplateResourceAction` permits deny-overrides; document that **deny wins** (any `allowed=false` on the matched path overrides a grant).

### 7.3 Implementation plan

| Layer | Phase | Mechanism |
|---|---|---|
| Role-level | 3 | `@EnableMethodSecurity` + `@PreAuthorize("hasRole('X')")` |
| Endpoint-level | 3 | Map per §3 of the audit (role/resource/action controllers → ADMIN; hierarchy GET → authenticated) |
| Permission-level | later (with template feature) | `CustomPermissionEvaluator implements PermissionEvaluator` (or `AuthorizationManager<T>` — preferred: type-safe, no SpEL strings) backed by the join queries above, with Caffeine cache on `(role, template, resourceAction)` resolution |
| Cache | Phase 6 | Reference-data cache; evict on template/role mutation |

---

## 8. Security Improvements — Phase Placement & Rationale

| Improvement | Phase | Why this phase |
|---|---|---|
| Delete plain-text auth path | 0 | It is the single biggest vulnerability and blocks the build; must be first |
| Password length fix (60) | 0 | BCrypt storage is a hard prerequisite for the entire scheme |
| Secret externalization + validation | 1 | The key material underpins token trust; do it while the token format is being touched |
| jti / issuer / audience / single parse | 1 | One token change window; enables denylisting later without a second token-format migration |
| `AuthenticationEntryPoint` / `AccessDeniedHandler` | 2 | `JwtFilter` of Phase 1 must invoke the entry point; error contract precedes authorization so denies are well-formed |
| Typed exceptions + `GlobalExceptionHandler` expansion | 2 | Same contract layer |
| Method security + `@PreAuthorize` + role seeding | 3 | Only meaningful once 401/403 semantics exist; otherwise clients see 500s for denies |
| Logout + jti denylist | 4 | Requires the jti claim from Phase 1; delivers revocation semantics for MVP without Redis |
| CORS | 5 | Deployment concern; needs no architectural work — but must exist before the first frontend integration |
| Security headers | 5 | Free with Spring Security defaults; explicit config in the hardening pass |
| Password policy | 5 | Tighten `RegisterRequest` validation (complexity, max length raised to 64) during hardening; avoids re-touching the DTO twice |
| Rate limiting (in-app, simple) | 5 | Ships with the prod config; bucket-based, no infra |
| Flyway / `ddl-auto: validate` / profiles / actuator | 5 | Deployment correctness |
| User cache + fetch join + `open-in-view: false` | 6 | Multi-node performance; correctness gate first (Phase 1 fetch join), caching second |
| Redis denylist, token rotation, distributed rate limits, RS256/JWKS, OAuth2 | 7 | **Future only** — triggers: multi-node auth traffic, second verifying service, compliance mandate |

---

## 9. Scalability Tiers — What Is Required Where

| Improvement | MVP (1 node, internal) | Enterprise internal (multi-node) | Large enterprise | Internet-scale |
|---|---|---|---|---|
| Stateless JWT + env secrets | **Required now** | Required | Required | Required |
| Fetch-join roles, `open-in-view: false` | **Required now** | Required | Required | Required |
| Typed error contract + entry point | **Required now** | Required | Required | Required |
| `@PreAuthorize` RBAC | **Required now** | Required | Required | Required |
| Role seeding | **Required now** | Required | Required | Required |
| CORS + headers + prod profile + Flyway | **Required now** | Required | Required | Required |
| In-app rate limiting | Recommended later | **Required** | Required | Gateway/WAF |
| Logout + jti denylist (in-memory) | Recommended later | **Required** (or refresh flow) | Required | Required |
| Refresh-token rotation | Recommended later | **Required** | Required | Required (Redis) |
| User/reference-data cache (Caffeine) | Future | **Recommended** | Required | Required (Redis) |
| Audit logging | Recommended later | **Required** | Required | Required |
| Redis distributed denylist | — | Future | **Required** | Required |
| RS256/JWKS | — | Future | Future | **Required** (multi-service) |
| OAuth2/OIDC delegation | — | — | Future | Only if 3rd-party IdP is a requirement |

**Explicit non-recommendations for now:** Redis, refresh tokens, OAuth2, distributed revocation — there is no second node, no second service, and no IdP requirement today. Revisit when a trigger from §8/Phase 7 fires.

---

## 10. Final Deliverables

### 10.1 Target Architecture Diagram

```
                         ┌────────────────────────────────────────────┐
                         │                 Client                     │
                         └────────────────────┬───────────────────────┘
                                              │ Authorization: Bearer <JWT>
                         ┌────────────────────▼───────────────────────┐
                         │           Spring Security Chain            │
                         │  CORS → JwtFilter → Authorization          │
                         │  CSRF off · STATELESS · entry point (401)  │
                         └────────────────────┬───────────────────────┘
                                              │
              ┌───────────────────────────────┼───────────────────────────────┐
              │                               │                               │
   ┌──────────▼─────────┐          ┌──────────▼─────────┐          ┌──────────▼─────────┐
   │     Auth path      │          │   Admin path       │          │   Protected path   │
   │ /auth/register     │          │ /api/role/**       │          │ /api/v1/...        │
   │ /auth/login        │          │ /api/actions/**    │          │ (any authenticated)│
   │ /auth/logout       │          │ /api/v1/resource*  │          │                    │
   └──────────┬─────────┘          │ @PreAuthorize ADMIN│          └──────────┬─────────┘
              │                    └──────────┬─────────┘                     │
   ┌──────────▼─────────┐                     │                    ┌──────────▼─────────┐
   │ AuthenticationService                   │                    │   Controllers     │
   │   → UserRepository                      │                    │   → Services      │
   │   → RoleRepository                      │                    │   → Repositories  │
   │   → PasswordEncoder (BCrypt)            │                    └──────────┬─────────┘
   │   → JwtService                          │                               │
   │   → TokenDenylistService                │                     ┌──────────▼─────────┐
   └──────────┬─────────┘                    │                     │      MySQL        │
              ▼                              │                     │ (Flyway-managed)  │
   ┌──────────────────────┐                  │                     └────────────────────┘
   │  JwtService          │◄─────────────────┘
   │  (issuer, audience,  │      All requests:
   │   jti, HS256, env key)       verify sig + exp + issuer → load user (cached, fetch-join roles)
   └──────────────────────┘
```

### 10.2 Package Structure
See §5 (final). Changes are additive and mechanical; `security` becomes the only package allowed to touch `Authorization` headers (ArchUnit-guarded).

### 10.3 Dependency Diagram
See §3. Key invariant: `security` depends only on `repository` + `entity` + config values; `auth` depends on `security`; controllers depend on `auth`/services; nothing depends on `config` besides Spring wiring.

### 10.4 Implementation Order

```
Phase 0 (build)   → Phase 1 (JWT) → Phase 2 (errors) → Phase 3 (RBAC)
→ Phase 4 (lifecycle) → Phase 5 (hardening) → Phase 6 (cache) → Phase 7 (future, gated)
```
Each phase ends with: `mvn clean verify` green + manual smoke test (register → login → protected call → 401/403 cases).

### 10.5 Refactoring Order (inside Phase 0)

1. Delete legacy identity files (§4.1)
2. Patch `User.password` → 60
3. Fix `ActionRepository` ID type
4. Rename package root → `com.expensetracker` (mechanical IDE refactor)
5. Move `JwtService`/`JwtFilter` → `security`
6. Rename DTOs (§4.3)
7. Re-add profile endpoints on `AuthController` with `UpdateProfileRequest`
8. `mvn clean verify` → green

### 10.6 Risk Analysis

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Legacy delete breaks a consumer not seen in audit | Low | High | `mvn clean verify` gate; grep for `findByEmail`/`UserService` usages before delete |
| Secret rotation after Phase 1 leaves invalid tokens in the wild | Medium | Medium | Short initial expiry; document rotation procedure; versioned secrets via `kid` later |
| `open-in-view: false` surfaces latent `LazyInitializationException`s | Medium | Medium | Phase 1 order: fetch-join first, disable second; smoke test all endpoints |
| `@PreAuthorize` misconfiguration locks out legit users | Medium | Medium | Matrix of endpoint→role in PR checklist; integration tests per endpoint class |
| MapStruct + Lombok upgrade drift (existing `pom.xml` duplicate plugin) | Low | Low | Deduplicate `maven-compiler-plugin` block in Phase 0 |
| Team unfamiliarity with method security SpEL | Low | Low | Keep expressions trivial (`hasRole('ADMIN')`); document pattern |

### 10.7 Estimated Effort (single experienced engineer)

| Phase | Effort |
|---|---|
| 0 — Build restore & deletion | 0.5 day |
| 1 — JWT hardening | 1 day |
| 2 — Error contract | 1 day |
| 3 — RBAC | 2 days |
| 4 — Lifecycle (logout/denylist) | 2 days |
| 5 — Production hardening | 2 days |
| 6 — Caching/scalability | 2 days |
| 7 — Internet-scale | (gated, not scheduled) |
| **Total (Phases 0–6)** | **≈ 10.5 days engineering + 3 days testing/QA** |

### 10.8 Production Readiness Checklist

**Gate A — must be green to ship (Phases 0–3):**
- [ ] `mvn clean verify` passes; zero dead code; single `findByEmail` consumer path
- [ ] No plain-text passwords anywhere; `User.password` length 60; BCrypt on all writes
- [ ] JWT secret from environment, validated ≥ 32 bytes; expiration configurable
- [ ] Invalid/expired token → 401 JSON; missing token → 401 JSON; forbidden → 403 JSON
- [ ] No auth failure returns 500 (`BadCredentialsException`, duplicates → 401/409)
- [ ] `@EnableMethodSecurity` on; role/resource/action admin endpoints gated; roles seeded
- [ ] Registration race returns 409 via constraint-catch, not 500

**Gate B — must be green for enterprise internal (Phases 4–5):**
- [ ] Logout + denylist works; access token window ≤ 30 min
- [ ] CORS allowlist configured; security headers present
- [ ] Prod profile: env secrets, `ddl-auto: validate`, Flyway migrations, `show-sql` off
- [ ] Actuator limited to `health,info`; login rate limiting active
- [ ] Audit log for login success/failure (file/structured log)

**Gate C — enterprise-scale (Phase 6+, gated):**
- [ ] Fetch-join + user cache; `open-in-view: false` proven by smoke tests
- [ ] Reference-data caching for permission resolution
- [ ] (Future) Redis denylist, token rotation, JWKS — only when triggers fire

---

*This roadmap is intentionally conservative: it removes duplicate complexity first, hardens semantics before adding features, and defers every infrastructure decision (Redis, refresh tokens, OAuth2) until an explicit architectural trigger exists. Ship Phase 0–5, measure, then decide Phase 6–7 with data.*
