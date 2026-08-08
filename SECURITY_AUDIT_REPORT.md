# Spring Security JWT Authentication — Codebase Audit Report

**Project:** Expensive-Tracker (Spring Boot 4.0.6, Java 21, MySQL, Maven)
**Date:** 2026-08-04
**Scope:** Read-only audit of all authentication/authorization/security code. No files were modified.

---

## 1. Existing Architecture

### 1.1 Package Tree (security-related)

```
com.expensive.Expensive.Tracker
├── config/                          # Security configuration layer
│   ├── SecurityConfig.java          # SecurityFilterChain, CSRF, session policy, filter registration
│   ├── ApplicationConfig.java       # PasswordEncoder, AuthenticationProvider, AuthenticationManager
│   └── JwtFilter.java               # OncePerRequestFilter — JWT authentication
├── security/                        # UserDetails layer
│   ├── CustomUserDetails.java       # Implements UserDetails
│   └── CustomUserDetailsService.java # Loads users by email
├── controller/
│   ├── AuthController.java          # POST /auth/register, POST /auth/login
│   ├── UserController.java          # /app/v1/user/** (signup + broken update)
│   ├── RoleController.java          # /api/role/**
│   ├── ActionController.java        # /api/actions
│   └── ResourceController.java      # /api/v1/resources/**
├── service/
│   ├── UserService.java / impl/UserServiceImpl.java      # Legacy, broken auth duplication
│   ├── RoleService.java / impl/RoleServiceImpl.java
│   ├── ActionService.java / impl/ActionServiceImpl.java
│   ├── ResourceService.java / impl/ResourceServiceImpl.java
│   └── impl/AuthenticationService.java  # Real register/login flow
├── repository/
│   ├── UserRepository.java          # findByEmail/Phone, existsByEmail/Phone
│   ├── RoleRepository.java          # findByRoleKey, existsByRoleKey
│   ├── ResourceRepository.java
│   └── ActionRepository.java
├── entity/
│   ├── User.java                    # User + user_roles join table
│   ├── Role.java                    # ManyToMany with User
│   ├── Resource.java, Action.java, ResourceAction.java
│   ├── Template.java, RoleTemplate.java, TemplateResourceAction.java
├── dto/
│   ├── AuthenticationResponse.java  # token, userId, fullName, email
│   ├── LoginUserDTO.java
│   ├── UserDTO.java                 # Register + signup DTO (password 8-16 chars)
│   ├── UpdateProfileDTO.java        # Duplicate of UserDTO
│   ├── ApiResponse.java, RoleRequestDTO, RoleResponseDTO, CreateActionRequest/Response
│   └── resource/*, resourceAction/*
├── mapper/                          # MapStruct: UserMapper, RoleMapper, ResourceMapper, ActionMapper
├── exception/
│   ├── GlobalExceptionHandler.java  # @RestControllerAdvice (partial coverage)
│   ├── ErrorResponse.java
│   ├── UserAlreadyExistsException, RoleNotFoundException, RoleAlreadyExistsException,
│   ├── ActionAlreadyExistsException, ResourceNotFoundException,
│   ├── ResourceNameAlreadyExistsException, ResourceKeyNotFoundException
├── enums/ResourceType.java
└── utils/
    ├── JwtService.java              # JWT generate/parse/validate (HARDCODED SECRET)
    └── DateUtils.java
```

### 1.2 Security Stack Summary

| Concern | Implementation | Status |
|---|---|---|
| Stateless sessions | `SessionCreationPolicy.STATELESS` | ✅ |
| CSRF | Disabled | ✅ (correct for JWT API) |
| CORS | **None configured** | ❌ |
| Password hashing | `BCryptPasswordEncoder` via `DaoAuthenticationProvider` | ✅ in main flow, ❌ in legacy signup |
| JWT filter | `JwtFilter` before `UsernamePasswordAuthenticationFilter` | ✅ |
| Method security | **Not enabled** — no `@EnableMethodSecurity` | ❌ |
| Public endpoints | `/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**` | ✅ |
| Error handling for auth | None (no `AuthenticationEntryPoint`, no handler for auth exceptions) | ❌ |
| Secret storage | **Hardcoded in source** | ❌ |

---

## 2. Authentication Flow Diagram (current)

```
Client
  │  POST /auth/register  |  POST /auth/login
  ▼
AuthController (AuthController.java)
  │  @Valid DTO → AuthenticationService
  ▼
AuthenticationService (service/impl/AuthenticationService.java)
  │  register: existsByEmail / existsByPhone checks → BCrypt encode → save → generate JWT
  │  login:    authenticationManager.authenticate(...)
  ▼
AuthenticationManager (ApplicationConfig.java:37)
  │
  ▼
DaoAuthenticationProvider (ApplicationConfig.java:26)
  │  passwordEncoder: BCryptPasswordEncoder
  ▼
CustomUserDetailsService.loadUserByUsername(email)  (security/CustomUserDetailsService.java)
  │  → UserRepository.findByEmail(email)
  ▼
UserRepository → MySQL (user, user_roles tables)
  │
  ▼  (success) → returns CustomUserDetails
  ▼
JwtService.generateToken(email, userId)  (utils/JwtService.java)
  │  claims: { userId }, sub=email, iat, exp (24h), HS256 hardcoded key
  ▼
Client stores token → sends "Authorization: Bearer <token>" on every request
  ▼
JwtFilter.doFilterInternal (config/JwtFilter.java)
  │  1. extract header, must start with "Bearer "
  │  2. jwtService.extractEmail(token)
  │  3. if no auth in SecurityContext → loadUserByUsername
  │  4. jwtService.validateToken(token, userDetails)
  │  5. build UsernamePasswordAuthenticationToken → set SecurityContext
  ▼
Protected Controller (UserController / RoleController / ResourceController / ...)
  │  anyRequest().authenticated()
```

---

## 3. Authorization Flow Diagram (current)

```
SecurityFilterChain (SecurityConfig.java:30)
  │
  ├── /auth/**            → permitAll
  ├── /swagger-ui/**      → permitAll
  ├── /v3/api-docs/**     → permitAll
  └── anyRequest()        → authenticated()   ← ONLY check: "is logged in"
                                      │
                                      ▼
            NO @EnableMethodSecurity → @PreAuthorize / hasRole / hasAuthority DEAD
                                      │
                                      ▼
            NO AccessDeniedHandler → default 403 handling
            NO AuthenticationEntryPoint → default (empty 403 body for missing token)
```

**Current authorization reality:** There is *zero role-based authorization*. Any authenticated user (including a freshly registered `ROLE_USER`) can call `POST /api/role/create`, `POST /api/actions`, `POST /api/v1/resources`, etc. Role/permission entities exist but are not enforced anywhere in the authorization path.

---

## 4. Classes Already Implemented

| Class | File | Quality |
|---|---|---|
| `SecurityConfig` | `config/SecurityConfig.java` | Basic but incomplete (no CORS, no entry point, no method security) |
| `ApplicationConfig` | `config/ApplicationConfig.java` | Correct beans |
| `JwtFilter` | `config/JwtFilter.java` | Functional but fragile (no exception handling) |
| `JwtService` | `utils/JwtService.java` | Functional but hardcoded secret, no issuer/audience |
| `CustomUserDetails` | `security/CustomUserDetails.java` | OK, hardcoded account flags |
| `CustomUserDetailsService` | `security/CustomUserDetailsService.java` | Correct |
| `AuthenticationService` | `service/impl/AuthenticationService.java` | Main flow works, but throws raw `RuntimeException` |
| `AuthController` | `controller/AuthController.java` | Correct |
| `UserRepository` / `RoleRepository` | `repository/*` | Complete for current needs |
| `User` / `Role` entities | `entity/*` | Working but several flaws (see §7) |
| `GlobalExceptionHandler` | `exception/GlobalExceptionHandler.java` | Partial coverage, 2 bugs (see §8) |
| DTOs | `dto/*` | Registration DTOs OK; duplicates exist |

---

## 5. Missing Classes / Components

| Missing | Why it matters | Recommended file |
|---|---|---|
| `AuthenticationEntryPoint` (custom `JwtAuthenticationEntryPoint`) | Missing/invalid token currently yields default **403 with empty body**, not a structured 401 | `security/JwtAuthenticationEntryPoint.java` |
| `AccessDeniedHandler` (custom `CustomAccessDeniedHandler`) | Authenticated-but-forbidden users get default handling with no JSON body | `security/CustomAccessDeniedHandler.java` |
| `@EnableMethodSecurity` | `@PreAuthorize`, `hasRole`, `hasAuthority` currently **do nothing** | `SecurityConfig` |
| `RefreshToken` entity/table + `POST /auth/refresh` | Access token is 24h, no rotation, no way to renew without re-login | `entity/RefreshToken.java`, `dto/RefreshTokenRequest` |
| Logout endpoint + token blacklist/denylist | Tokens remain valid until expiry; no revocation | `AuthController` + `TokenBlacklistService` (Redis or DB) |
| Global `RuntimeException`, `BadCredentialsException`, `MethodArgumentNotValidException`, `DataIntegrityViolationException` handlers | Auth failures currently return 500 | `GlobalExceptionHandler` |
| Role seeding (`data.sql` / `CommandLineRunner`) | `ROLE_USER` must exist or **registration always fails**; `spring.sql.init.mode=always` has no script | `src/main/resources/data.sql` |
| CORS configuration (`CorsConfigurationSource` bean) | Frontend on another origin cannot call the API at all | `SecurityConfig` or `WebConfig` |
| Security constants class | Hardcoded `"Bearer "`, `"ROLE_USER"`, secret, expiry magic values scattered | `utils/SecurityConstants.java` |
| Password policy (complexity) | Password only length-checked (8–16), no complexity rule | `UserDTO`/`RegisterRequest` |
| Audit logging (login attempts, token events) | No trace of auth events; required for production compliance | `Aspect` or events |
| `JwtAuthenticationFilter` tests / security integration tests | Only `contextLoads()` test exists | `src/test` |
| `application-prod.yaml` / profiles | No environment separation; dev creds would ship to prod | `resources/` |
| DB index for `user_roles` join table & `role.roleKey` unique | Join table unscanned, but indexes are cheap wins | schema |

---

## 6. Configuration Problems

| # | Severity | Problem | File | Recommendation |
|---|---|---|---|---|
| 1 | **Critical** | **JWT secret hardcoded** in source: `"thisismysecretkey_thisneeds_to_be_at_least_32_bytes_long!!"` | `utils/JwtService.java:19-20` | Move to env var / config server; fail fast if unset (`@Value("${jwt.secret}")`, validate length ≥ 32 bytes). Never commit. |
| 2 | **High** | JWT expiration hardcoded `1000L * 60 * 60 * 24` (24h), not configurable | `utils/JwtService.java:22-23` | Externalize via `jwt.expiration`. |
| 3 | **High** | DB credentials `root` / `root` hardcoded in `application.yaml` | `resources/application.yaml:6-8` | Use env vars `${DB_USERNAME}`, `${DB_PASSWORD}`. |
| 4 | **High** | No CORS config — default is *deny all* cross-origin | `SecurityConfig` | Add `CorsConfigurationSource` bean + `.cors(Customizer.withDefaults())`. |
| 5 | **High** | `spring.sql.init.mode: always` with **no** `data.sql`/`schema.sql` | `application.yaml:19-21` | Either provide seed script or remove the setting. |
| 6 | **Medium** | `ddl-auto: update` in config — schema auto-mutated at runtime; dangerous in prod | `application.yaml:13` | `validate` in prod + Flyway/Liquibase migrations. |
| 7 | **Medium** | `show-sql: true` + `format_sql` — SQL with bound values visible in logs | `application.yaml:14-17` | Off in prod. |
| 8 | **Medium** | No profile split (`application-prod.yaml`) | `resources/` | Add profiles; prod should disable devtools and set proper logging. |
| 9 | **Medium** | Swagger matchers permitted (`/swagger-ui/**`, `/v3/api-docs/**`) but **no swagger dependency** in `pom.xml` | `SecurityConfig.java:33-34`, `pom.xml` | Either add `springdoc-openapi` or remove the permit rules. |
| 10 | **Low** | `devtools` runtime dependency — remote restart endpoints in prod build | `pom.xml:54-59` | Exclude from prod build/profiles. |
| 11 | **Low** | Duplicate `maven-compiler-plugin` block in `pom.xml` (lines 133–192) | `pom.xml` | Remove the second copy. |

---

## 7. Security Vulnerabilities

| # | Severity | Vulnerability | Evidence | Fix |
|---|---|---|---|---|
| 1 | **Critical** | Hardcoded JWT signing key — anyone with the repo can forge tokens for any user | `JwtService.java:19-20` | Externalize secret; rotate immediately if code was ever shared. |
| 2 | **Critical** | **Legacy signup stores plain-text passwords** — `UserServiceImpl.signup` maps `UserDTO` → `User` via MapStruct with no encoding | `service/impl/UserServiceImpl.java:30-38`, `mapper/UserMapper.java:12` | Delete the legacy flow (see §9/§10) or encode with `PasswordEncoder` + assign roles. |
| 3 | **Critical** | **`User.password` column is `length = 10`** — BCrypt hashes are 60 chars; inserts will fail (MySQL strict) or truncate, and login is impossible | `entity/User.java:36-39` | `@Column(length = 60)` (or larger), plus `ddl-auto` migration. |
| 4 | **Critical** | **Dead code doesn't compile**: `UserServiceImpl.login` references undefined `UserNotFoundException` (line 44) and undefined variable `userInfo` (line 54) | `service/impl/UserServiceImpl.java:44,54` | App cannot be built; remove `login`/`deactivateProfile` or implement properly. |
| 5 | **High** | No `AuthenticationEntryPoint` — unauthenticated requests get a bare 403, no JSON, no 401 semantics | `SecurityConfig` | Custom entry point returning 401 + `ErrorResponse`. |
| 6 | **High** | `JwtFilter` swallows token parse errors — an expired/corrupt token throws `JwtException` out of the filter → **500 Internal Server Error** instead of 401 | `config/JwtFilter.java:47` | try/catch `ExpiredJwtException` / `MalformedJwtException` / `SignatureException` → 401 response. |
| 7 | **High** | Zero role enforcement — `POST /api/role/create` and all resource/action admin endpoints are callable by any authenticated user (roles not checked anywhere) | `RoleController.java:24-29`, `SecurityConfig.java:36` | `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` etc. |
| 8 | **High** | Auth failures leak 500s: `RuntimeException("Email already exists")`, `RuntimeException("ROLE_USER not found")`, `BadCredentialsException` (login) have **no** handlers | `AuthenticationService.java:34,38,43,80`; `GlobalExceptionHandler.java` | Map to 409/401 with dedicated exceptions. |
| 9 | **High** | Registration is not concurrency-safe — `existsByEmail` then `save` is a TOCTOU race → `DataIntegrityViolationException` (unhandled → 500) | `AuthenticationService.java:33-54` | Catch constraint violation → 409, or use DB unique constraints as source of truth. |
| 10 | **High** | `UserController.update` does `authHeader.substring(8, ...)` — off-by-one (should be `substring(7)`), parses token twice, and its result differs from `JwtFilter`'s behavior | `controller/UserController.java:38-39` | Delete this method (no mapping anyway) and read user from `SecurityContext`. |
| 11 | **Medium** | Token lacks `issuer`/`audience`/`tokenType` claims; no check that the token was issued by this app | `JwtService.java:44-51` | Add issuer/audience, verify on parse. |
| 12 | **Medium** | No token revocation/logout — 24h validity window cannot be shortened server-side | entire auth flow | Refresh tokens + denylist. |
| 13 | **Medium** | `UserServiceImpl.updateProfile` inverted condition: `if (!userExists.isEmpty())` returns NOT_FOUND when the user **does** exist (and updates a non-existent user) | `service/impl/UserServiceImpl.java:65-71` | `if (userExists.isEmpty())`. |
| 14 | **Medium** | `GlobalExceptionHandler.handleResourceNameAlreadyExistsException` returns HTTP **404 with statusCode 409** — inconsistent contract | `exception/GlobalExceptionHandler.java:36-47` | Return 409. |
| 15 | **Medium** | `@JsonIgnore` on `User.password` relies on Jackson annotation for secrecy; if any mapper/config serializes via a different mechanism (or MapStruct copies), password can leak; also `User` is returned nowhere currently, but DTO mapping `userToDto` maps **everything except nothing excluded** — password flows through MapStruct objects | `entity/User.java:38`, `mapper/UserMapper.java:16` | Never map entity → wire directly; use explicit DTOs that exclude password; keep `@JsonIgnore` as defense-in-depth. |
| 16 | **Medium** | `UsernameNotFoundException` message includes the queried email — logged/leaked user enumeration vector | `security/CustomUserDetailsService.java:23` | Return generic message; log detail server-side only. |
| 17 | **Medium** | Password policy is only length 8–16; no complexity; `@Size(max=16)` also silently truncates nothing but blocks strong passphrases | `dto/UserDTO.java:33-35` | Add complexity pattern + raise max. |
| 18 | **Low** | JWT filter chain: if `validateToken` fails, filter silently continues (no auth set → 403 downstream). Acceptable but masks the reason | `config/JwtFilter.java:53-66` | Log/respond 401 on invalid token. |
| 19 | **Low** | `isAccountNonLocked/Expired/CredentialsNonExpired` hardcoded `true`; `isEnabled` maps `isActive` but nothing ever sets `isActive=false` | `security/CustomUserDetails.java:39-51` | Back with real fields. |
| 20 | **Low** | Actuator included; endpoints authenticated by default, but no explicit `management` config — health/details exposure not controlled | `pom.xml:34-36`, `application.yaml` | Restrict exposure (`management.endpoints.web.exposure.include=health,info`). |

---

## 8. Code Smells

| # | Severity | Smell | Location |
|---|---|---|---|
| 1 | **High** | **Duplicate registration flow**: `/auth/register` (proper) vs `/app/v1/user/signup` (plain-text password, no roles) | `AuthenticationService.register` vs `UserServiceImpl.signup` + `UserController.java:22-26` |
| 2 | **High** | **Duplicate login flow**: `AuthenticationService.login` (correct) vs `UserServiceImpl.login` (broken, non-compiling, compares plain text) | `UserServiceImpl.java:41-61` |
| 3 | **High** | Dead method: `UserController.update` has **no HTTP mapping annotation** and contains the `substring(8)` bug — unreachable yet misleading | `controller/UserController.java:28-42` |
| 4 | **High** | `UserServiceImpl.deactivateProfile` returns `null`; interface promises behavior | `service/impl/UserServiceImpl.java:81-84` |
| 5 | Medium | Hardcoded magic values: `"Bearer "` (JwtFilter.java:40), `"ROLE_USER"` (AuthenticationService.java:41), secret & expiry (JwtService) | multiple |
| 6 | Medium | `JwtService` in `utils` package is a service; `JwtFilter` in `config` is a security component — wrong package placement | `utils/JwtService.java`, `config/JwtFilter.java` |
| 7 | Medium | Package name contains mixed case + capital letter (`com.expensive.Expensive.Tracker`) — violates Java naming conventions | all files |
| 8 | Medium | `UserDTO` (register) and `UpdateProfileDTO` are near-duplicates; `UserDTO` doubles as register & legacy signup | `dto/UserDTO.java`, `dto/UpdateProfileDTO.java` |
| 9 | Medium | No error handler for `MethodArgumentNotValidException` → validation failures return Spring's default 400 body, not the `ErrorResponse` shape | `GlobalExceptionHandler` |
| 10 | Medium | `Entity → DTO` mapping exists but is inconsistent: `AuthenticationResponse` is hand-built (fine), `UserMapper.userToDto` would expose password field | `mapper/UserMapper.java:16` |
| 11 | Low | `@Data` on JPA entities (`User`, `Role` missing too) — mutable equals/hashCode on entities; `@Getter/@Setter` preferred with explicit `equals/hashCode` | `entity/User.java:18` |
| 12 | Low | `isActive` boolean default duplicated in Java field + `@ColumnDefault` | `entity/User.java:45-46` |
| 13 | Low | `User` has no audit columns (`createdAt`, `updatedAt`, `@Version`) — no optimistic locking | `entity/User.java` |
| 14 | Low | Only one test (`contextLoads`) — zero security tests | `src/test` |
| 15 | Low | `Resource` entity imports `ResourceKeyNotFoundException` into the domain layer (exception used as validation, wrong naming — it's a validation failure, not a not-found) | `entity/Resource.java:4,80` |
| 16 | Low | Duplicated `maven-compiler-plugin` declaration | `pom.xml:133-192` |
| 17 | Low | Unused dependency import style: `Role` entity `@Setter`-only lombok; `RoleResponseDTO` naming inconsistent with `AuthenticationResponse` | `entity/Role.java`, `dto/*` |

---

## 9. Production Readiness Score

**Score: 18 / 100**

Breakdown:
- Authentication core (JWT + BCrypt + filter): 45% — works on paper, but secret is hardcoded, no 401 handling, and the app doesn't even compile (dead code).
- Authorization: 0% — no method security, no role checks.
- Error handling: 15% — partial advice with bugs; auth errors → 500.
- Configuration hygiene: 15% — hardcoded secrets/creds, no profiles, no CORS.
- Testing: 5% — no security tests.
- Password safety: 30% — BCrypt in main flow, plain-text in legacy flow, column length 10 breaks BCrypt storage.

**Blocking facts:** the project as committed **does not compile** (`UserNotFoundException`, `userInfo` in `UserServiceImpl`) and **cannot store BCrypt hashes** (`password` column length 10). These are release-blockers.

---

## 10. Prioritized Action Plan

### Phase 1 — Make it work and stop the bleeding (Critical)

1. **Fix compile errors** — implement or remove `UserNotFoundException`; fix `userInfo` reference; remove dead `login()` from `UserServiceImpl` (`service/impl/UserServiceImpl.java:44,54`).
2. **Fix `User.password` column length** → 60 (`entity/User.java:36-39`) and regenerate schema.
3. **Remove the legacy auth paths** — delete `UserServiceImpl.signup`/`login`/`updateProfile`/`deactivateProfile` and `UserController` (or rewrite it around `AuthenticationService`); keep a single registration flow (`/auth/register`). Eliminates the plain-text password hole (`UserServiceImpl.java:30-38`).
4. **Externalize JWT secret & expiration** to env config, add length validation (`JwtService.java:19-23`).
5. **Externalize DB credentials** (`application.yaml:6-8`).

### Phase 2 — Correct auth failure semantics (High)

6. Add `JwtAuthenticationEntryPoint` (401 + `ErrorResponse`), `CustomAccessDeniedHandler` (403 + `ErrorResponse`), register in `SecurityConfig`.
7. Wrap `JwtFilter` token parsing in try/catch → 401 on `ExpiredJwtException` / `MalformedJwtException` / `SignatureException`; never propagate 500.
8. Add handlers for `BadCredentialsException` (401), `RuntimeException`/generic (500 generic message), `MethodArgumentNotValidException` (400, field errors), `DataIntegrityViolationException` (409).
9. Replace `RuntimeException` in `AuthenticationService` with `UserAlreadyExistsException` (409), `RoleNotFoundException` (500→404/500 semantics), generic login failure (401).

### Phase 3 — Real authorization (High)

10. Add `@EnableMethodSecurity` to `SecurityConfig`.
11. Annotate admin endpoints: `POST /api/role/**` → `@PreAuthorize("hasRole('ADMIN')")`; resource/action creation similarly. Keep `GET` hierarchy endpoints read-authenticated.
12. Seed roles (`ROLE_USER`, `ROLE_ADMIN`) via `data.sql` or `CommandLineRunner`; make `spring.sql.init` meaningful or remove it.

### Phase 4 — Production hardening (Medium)

13. Add CORS `CorsConfigurationSource` (allowlist origins, credentials only if needed).
14. Add refresh-token rotation + logout + denylist (DB/Redis) or short-lived access token + refresh pair.
15. Add issuer/audience claims and validate them in `JwtService`.
16. `ddl-auto: validate` + Flyway; disable `show-sql` in prod; add `application-prod.yaml`; restrict actuator exposure; drop devtools from prod build.
17. Add audit logging (login success/failure, token refresh, account deactivation).
18. Fix `GlobalExceptionHandler.handleResourceNameAlreadyExistsException` → 409; fix `UserServiceImpl.updateProfile` condition.
19. Add password complexity policy; align `UpdateProfileDTO`/`UserDTO` into a single `RegisterRequest` + `UpdateProfileRequest`.
20. Add security integration tests (register → login → protected call; invalid/expired token → 401; role-denied → 403).
21. Move `JwtService` → `service` package, `JwtFilter` → `security` package, introduce `SecurityConstants`.

---

## Appendix — Quick Reference: Endpoint Inventory

| Method | Path | Controller | Currently | Should be |
|---|---|---|---|---|
| POST | `/auth/register` | AuthController | public | public |
| POST | `/auth/login` | AuthController | public | public |
| POST | `/app/v1/user/signup` | UserController | **protected** | remove (duplicate of register) |
| POST | `/app/v1/user/update` | UserController | dead code (no mapping) | remove |
| POST | `/api/role/create` | RoleController | authenticated (any user) | ADMIN only |
| GET | `/api/role/{id}` | RoleController | authenticated (any user) | ADMIN only (or logged-in with permission) |
| POST | `/api/actions` | ActionController | authenticated (any user) | ADMIN only |
| POST | `/api/v1/resources` | ResourceController | authenticated (any user) | ADMIN only |
| POST | `/api/v1/resources/bulk` | ResourceController | authenticated (any user) | ADMIN only |
| GET | `/api/v1/resources/hierarchy` | ResourceController | authenticated (any user) | authenticated (any) |
| GET | `/swagger-ui/**`, `/v3/api-docs/**` | — | public (no dependency present) | remove or add springdoc |
