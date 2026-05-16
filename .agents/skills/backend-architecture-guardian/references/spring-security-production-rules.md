# Spring Security Production Rules and Anti-Patterns

## Core Architectural Rules

- Spring Security must be treated as a centralized security architecture, not as scattered annotations or isolated checks.
- All security logic must flow through `SecurityFilterChain`.
- Authentication, authorization, validation and business logic must remain strictly separated.
- Authorization must follow the deny-by-default principle.
- Every endpoint must be explicitly classified as public or protected.
- Security rules must be deterministic and reviewable.
- Business controllers must never perform manual authentication checks.
- Security decisions must never depend on frontend behavior.
- Every request must pass through the same predictable security pipeline.
- Security configuration must be explicit, minimal and readable.

---

## Authentication Rules

- Authentication only proves identity.
- Authorization only determines permissions.
- These responsibilities must never be mixed.
- JWT presence alone does not mean the user is trusted.
- A token is trusted only after full validation.
- Authentication must be stateless when JWT architecture is selected.
- HTTP session must not silently become a hidden source of truth in JWT systems.
- SecurityContext must only contain validated identities.
- Anonymous users must never be treated as partially authenticated users.
- Login success does not equal authorization success.

---

## JWT Validation Rules

- JWT must always be cryptographically verified.
- Token signature verification must be mandatory.
- Allowed signing algorithms must be explicitly restricted.
- The server must never trust the algorithm declared by the client token.
- Tokens with unsecured or unknown algorithms must always be rejected.
- Issuer validation must be mandatory.
- Expiration validation must be mandatory.
- Not-before validation must be mandatory.
- Audience validation must be used whenever the system has multiple consumers.
- Claims must be treated as untrusted until validation completes.
- JWT parsing must never be implemented manually when Spring Security already provides validated infrastructure.
- JWT validation must be delegated to Spring Security resource server components.
- Expired tokens must never be accepted even with "small tolerance hacks".
- JWT must never be treated as encrypted unless encryption is explicitly implemented.
- Sensitive business information must not be stored inside token payloads.

---

## Authorization Rules

- Authorization must be role-based or authority-based, never string-comparison chaos.
- Authorities must be mapped explicitly from claims.
- Claims-to-authorities mapping must be deterministic.
- Method security must supplement HTTP security, not replace it.
- Unannotated methods must never be assumed secure.
- Catch-all authorization rules must always exist.
- Access control must be enforced server-side only.
- UI visibility must never be treated as real authorization.
- Ownership checks must exist separately from role checks.
- Administrative authorities must be minimized.
- Privilege escalation paths must be analyzed explicitly.

---

## Security Filter Chain Rules

- Custom filters must only exist when absolutely necessary.
- Manual JWT extraction inside controllers is forbidden.
- Security filters must remain ordered and predictable.
- Authentication filters must run before authorization filters.
- CSRF protection must execute before authentication processing.
- Filters must never contain business logic.
- Filters must fail securely.
- Filter exceptions must never leak internal implementation details.
- Security filter behavior must remain observable through logs and tracing.

---

## Password Rules

- Passwords must only be stored as adaptive cryptographic hashes.
- Plaintext passwords are forbidden.
- Weak hashing algorithms are forbidden.
- Password encoders must support future migration.
- Password verification must be delegated to Spring Security infrastructure.
- Password reset flows must invalidate old credentials.
- Password storage strategy must support algorithm rotation.

---

## Session and State Rules

- JWT architecture must explicitly define whether the system is stateful or stateless.
- Stateless systems must not secretly depend on server sessions.
- Logout behavior must be explicitly designed.
- JWT logout requires revoke strategy or token expiration strategy.
- Session fixation protection must remain enabled.
- Authentication state transitions must be controlled and observable.
- SecurityContext must never leak across requests.
- Session invalidation must remove all authentication state.

---

## CSRF Rules

- CSRF protection must not be disabled blindly.
- Browser-based authentication flows require CSRF analysis.
- Cookie-based authentication requires CSRF protection.
- Safe HTTP methods must remain read-only.
- Unsafe methods must require protection mechanisms.
- CSRF decisions must be based on threat model, not convenience.
- JWT alone does not automatically eliminate CSRF risk.

---

## CORS Rules

- CORS configuration must be explicit.
- Allowed origins must be restricted.
- Wildcard origins in production are forbidden unless absolutely justified.
- Allowed methods and headers must be explicitly controlled.
- Credentialed requests require stricter origin validation.
- CORS must be treated as security configuration, not frontend configuration.

---

## HTTP Security Header Rules

- Security headers must remain enabled by default.
- HSTS must be enabled in HTTPS environments.
- Content Security Policy must be explicitly designed.
- Frame embedding restrictions must be defined intentionally.
- Deprecated browser security mechanisms must not be relied upon.
- Security headers must be reviewed as part of deployment architecture.

---

## Token Lifecycle Rules

- Access tokens must remain short-lived.
- Refresh token strategy must be explicitly designed.
- Token revocation strategy must exist for production systems.
- Compromised tokens must be revocable.
- Token rotation must be supported.
- Refresh tokens require stronger protection than access tokens.
- Token identifiers should support blacklist or denylist strategies where required.
- Token lifetime must align with business risk.

---

## Secret and Key Management Rules

- Cryptographic keys must never be hardcoded.
- Secrets must never be committed to source control.
- Secrets must be externally managed.
- Key rotation must be planned from the beginning.
- Shared secrets must be sufficiently strong and random.
- Production keys must differ from development keys.
- Cryptographic material access must be minimized.
- Public and private key responsibilities must remain separated.

---

## Logging and Observability Rules

- Authentication failures must be logged.
- Authorization denials must be logged.
- Sensitive data must never appear in logs.
- JWT payloads must not be dumped into logs.
- Passwords and secrets must never be logged.
- Security events must remain traceable.
- Monitoring must detect suspicious authentication behavior.
- Logs must support forensic investigation.

---

## Testing Rules

- Security configuration must be tested explicitly.
- Tests must execute through Spring Security infrastructure.
- Security tests must verify both success and denial scenarios.
- CSRF behavior must be tested.
- Authorization rules must be tested per role and authority.
- Anonymous access must be tested explicitly.
- Token validation edge cases must be tested.
- Expiration and revoke behavior must be tested.
- Tests bypassing security filters are forbidden for production validation.

---

## Architecture Anti-Patterns

- Manual JWT parsing inside controllers.
- Disabling CSRF "because JWT exists".
- Using localStorage blindly without threat analysis.
- Treating frontend restrictions as security.
- Mixing authentication with business logic.
- Using wildcard CORS in production.
- Hardcoding secrets.
- Storing plaintext passwords.
- Accepting arbitrary JWT algorithms.
- Using insecure demo configurations in production.
- Trusting unvalidated claims.
- Creating giant security configurations with hidden behavior.
- Ignoring logout and revoke strategy.
- Treating JWT as encrypted storage.
- Relying on security through obscurity.
- Building authorization around string comparisons scattered across codebase.
- Using custom cryptography when framework support exists.
- Ignoring method-level authorization.
- Allowing implicit fallback behavior in security configuration.
- Treating Spring Security defaults as fully sufficient production architecture.
