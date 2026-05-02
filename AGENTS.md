# CallDesk

AI phone receptionist: Spring Boot backend (`backend/`) + Next.js dashboard (`dashboard/`).

- Backend spec: `docs/BACKEND_SPEC.md`. Follow it exactly.
- Write all code from scratch. Never copy from other repositories.
- Never commit secrets. Mock providers must keep everything runnable with zero API keys.
- Java 21, Spring Boot 3.5.x, Maven, package `com.calldesk`. Constructor injection, records for DTOs, no Lombok.
- Every non-trivial class gets a unit test. Turn-taking logic in `conversation` must be deterministic under a fake clock.
