# IssueFlow — Setup & Run Guide

A RESTful ticket-management backend. Java 21, Spring Boot 3.4, Spring Data JPA.

## Prerequisites
- Java 21 (JDK)
- Maven (the project includes the Maven wrapper, so a local Maven install isn't required)
- Docker + Docker Compose (for PostgreSQL in non-test runs)

## Build
```bash
./mvnw clean package
```
This compiles, runs the full test suite, and produces the runnable jar at
`target/issueflow-0.0.1-SNAPSHOT.jar`.

## Run the tests only
```bash
./mvnw test
```
Tests run against an in-memory H2 database (the `test` profile), so no external
database is needed to run them.

## Run the application
The application uses PostgreSQL when run normally. Start the database with:
```bash
docker compose up -d
```
Then run the app:
```bash
./mvnw spring-boot:run
```
Or run the built jar directly:
```bash
java -jar target/issueflow-0.0.1-SNAPSHOT.jar
```
The API is served at `http://localhost:8080`.

## Database profiles
- **Production / normal run:** PostgreSQL, configured via `compose.yml` and `application.yaml`.
  Datasource credentials are currently hardcoded in `application.yaml` (`issueflow` / `issueflow`)
  for the purposes of this assignment; in a production deployment these would be externalized to
  environment variables.
- **Tests:** H2 in-memory, schema auto-created and dropped per run (`test` profile).

## Authentication
- Authentication is JWT-based. Obtain a token via `POST /auth/login`, then send it as
  `Authorization: Bearer <token>` on subsequent requests.
- `POST /auth/logout` invalidates a token (added to a server-side deny-list).

## Bootstrapping the first user
- `POST /users` is **intentionally unauthenticated**. This is the bootstrap path: with no
  users in the system, there is no way to authenticate, so user creation must be open to
  allow the first account to be created. The request body includes a `password` field.
  All other endpoints require authentication.

## Tests
The suite covers, among other areas: wiring/contract checks for the core endpoints; authorization
(both the allowed and the forbidden paths for ADMIN-only endpoints); the resolved-blocker rule
(open / DONE / soft-deleted blockers); audit granularity for status-only vs non-status ticket
updates; token cleanup; and the escalation scheduler, including its once-per-hour idempotency
cooldown. Tests run against in-memory H2; service/repository integration tests use transactional
rollback for isolation.

## Assumptions & Deliberate Design Decisions

The following are choices I made deliberately; documenting them so reviewers can see they
were intentional, not oversights.

- **HTTP success status:** Every successful endpoint returns `200 OK` (including creates and
  no-body responses), per the README contract. Error statuses (400/401/404/409/500) are
  produced by a global exception handler.
- **Admin bootstrap:** `POST /users` is public so the system can create its first account, but
  ADMIN creation is guarded: an ADMIN may be created only by an authenticated ADMIN, or when the
  users table is empty for first-user bootstrap. Unauthenticated callers may create DEVELOPER
  users; non-admin authenticated callers may not create ADMIN users.
- **Soft delete (projects & tickets):** Deletion is a soft delete (a `deleted` flag), never a
  physical delete. Soft-deleting a project does **not** cascade to its tickets; tickets simply
  become unreachable through project-scoped queries and reappear if the project is restored.
  Restore is therefore symmetric and requires no bulk writes.
- **Ticket status transitions:** Strictly one step forward — TODO → IN_PROGRESS → IN_REVIEW →
  DONE. Skipping steps is rejected (400). A DONE ticket is frozen (further edits rejected, 409).
- **Resolved-blocker rule:** A ticket may move to DONE only when every blocker is "resolved,"
  where resolved means the blocker is DONE **or** soft-deleted. A soft-deleted blocker's
  dependency row is intentionally kept (for audit) but no longer gates the transition.
- **Dependency cycle detection:** Only direct 2-cycles (A↔B) are rejected. Deeper transitive
  cycles are **not** detected — a documented limitation given scope.
- **Auto-assignment:** When a ticket is created without an explicit assignee, it is assigned to
  the least-loaded DEVELOPER member of the project (tie-break: earliest-registered user by
  `User.createdAt`, then lowest user id); if no developer exists, the ticket is left unassigned.
  An explicitly provided assignee must exist (404 if not) and must be a project member (400 if not).
- **CSV import:** Partial import — valid rows are committed, invalid rows are reported with row
  numbers; the import is intentionally **not** wrapped in a single method-level transaction, so
  each row commits independently and one bad row does not roll back the others. Import writes
  **one** summary audit row (`CSV_IMPORT`), not a per-ticket audit row, by design.
- **Attachments:** Validated by declared `Content-Type` against a whitelist (png, jpeg, pdf,
  plain text) and a 10MB size limit. Declared content type is spoofable; byte-level
  (magic-number) sniffing was considered out of scope — a documented limitation.
- **Audit log:** Every state-changing action writes one audit row in the **caller's transaction**
  (so the log reflects only committed actions and never rolls back the business operation).
  System-initiated actions (auto-assignment, auto-escalation) are recorded with a SYSTEM actor.
  Mention changes are **not** separately audited — they are a side effect of comment writes,
  which are themselves audited. `GET /audit-logs` is ADMIN-only.
- **`isOverdue` semantics:** `isOverdue` is a *terminal-escalation* marker — it becomes true only
  when a ticket has reached CRITICAL and can be escalated no further. Being past the due date is
  the *entry condition* for escalation (it makes a ticket a candidate), not the meaning of the
  flag itself.
- **Escalation idempotency:** The escalation scheduler runs every 60 seconds but escalates a given
  ticket at most once per hour (cooldown via `lastAutoEscalatedAt`), so an overdue ticket climbs
  one priority level per hour rather than racing to CRITICAL. Status is never changed by escalation.
- **Additional endpoints:** ProjectMember management endpoints (add/remove members) are not in the
  original README but were required to support membership-based features.

## Project structure
- `controller/` — REST controllers
- `service/` — business logic
- `repository/` — Spring Data JPA repositories
- `entity/` — JPA entities and enums
- `dto/` — request/response DTOs
- `security/` — JWT filter, security config
- `scheduler/` — background jobs (escalation, token cleanup)
- `exception/` — custom exceptions + global handler
