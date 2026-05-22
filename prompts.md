# AI Usage & Prompts

This project was built with AI assistance. This document is an honest account of how I used
AI tools, what they did, what I did, and why I can be accountable for every line regardless.

## Tools used

- **Claude (Opus 4.7)** — used through the Claude chat interface as a design reviewer, mentor,
  and code reviewer. It did not write production code; it explained tradeoffs, reviewed my code
  line-by-line, caught bugs, and quizzed me on the decisions I'd need to defend.
- **Codex** — used as a coding agent (in VS Code) to generate
  boilerplate and to implement tests whose *cases* I had already designed.

## Why I worked this way

The assignment requires me to be fully accountable for the code. So I structured the work so that
the parts requiring judgment stayed with me, and only mechanical work was delegated:

1. **I made every design decision.** For each non-trivial choice I reasoned through the tradeoffs
   (using Claude as a sounding board that argued both sides and pushed back on me) and chose the
   approach myself.
2. **I wrote the logic-bearing code** — the method bodies that encode business rules.
3. **The agent wrote boilerplate** — controllers, DTOs, repository interfaces, config — and **test
   implementations for cases I specified**.
4. **Everything was reviewed** line-by-line before I accepted it, and review caught real bugs (listed below).

## Design decisions 

These are choices I made deliberately. For each I can explain the alternatives and why I chose this one:

- **Status transitions as a `Map`, not enum ordinals.** A `Map<TicketStatus, Set<TicketStatus>>`
  of allowed transitions, chosen over `ordinal()` arithmetic because it doesn't couple correctness
  to enum declaration order and survives future non-linear transitions.
- **`nextPriority` for escalation uses a `switch`** (returning null at the CRITICAL ceiling) —
  contrast with the status map: priority is an inherent ranking, so an explicit switch reads clearly
  and the null-at-ceiling gives me the "can't promote further" signal directly.
- **Soft delete with no cascade.** Soft-deleting a project doesn't touch its tickets; they become
  unreachable via project-scoped queries and reappear on restore. Restore is symmetric, no bulk writes.
- **Resolved-blocker rule.** A ticket can go DONE only when every blocker is DONE *or* soft-deleted.
  The predicate is `status != DONE && !deleted`. A soft-deleted blocker's dependency row is kept for
  audit but no longer gates the transition.
- **Audit logging in the caller's transaction (`REQUIRED`, not `REQUIRES_NEW`).** The audit log
  mirrors committed state, so it should commit with the action it logs and never roll back the
  business operation. (`record` therefore has no throwing path on the audit side.)
- **Escalation idempotency via a one-hour cooldown** on `lastAutoEscalatedAt`, so an overdue ticket
  climbs at most one priority level per hour instead of racing to CRITICAL; status is never changed.
- **`isOverdue` is a terminal marker** (reached CRITICAL), not a "past due date" flag — past-due is
  the entry condition for escalation, `isOverdue` marks the top.
- **CSV import is intentionally NOT method-transactional** — partial import requires each row to
  commit independently; one shared transaction would mark itself rollback-only on a single failure
  and lose every valid row. This is the deliberate opposite of `createTicket`, which is atomic.
- **Method-level authorization** (`@PreAuthorize` + `@EnableMethodSecurity`) for ADMIN-only endpoints,
  chosen over URL-based config so the guard lives next to the logic.
- **Project creation** - The creator of a project is it's first owner in codition to the fact that the owner needs to be a project member. Updates can change this.

## Bugs that line-by-line review caught

I include these because they show the review was real and that I understood the fixes, not just
accepted them:

- A blocker check that used `dependencies.size() > 0` — would have blocked a DONE transition even
  when every blocker was already resolved. Fixed to check each blocker's status/deleted state.
- An unconditional `UPDATE` audit row in `updateTicket` — emitted even on a status-only change.
  Fixed to emit `UPDATE` only when a non-status field actually changed.
- A missing cooldown guard in the escalation scheduler — would have promoted a ticket LOW→CRITICAL
  in three minutes. Fixed with the `lastAutoEscalatedAt` one-hour check.
- An `orElseThrow` in the audit `record` method — could have rolled back a business operation if an
  actor lookup failed. Fixed to never throw on the audit path.
- `isOverdue` not set when a ticket was *promoted* to CRITICAL (only when already CRITICAL) — left a
  window where a CRITICAL ticket wasn't flagged. Fixed so promotion to CRITICAL flags it immediately.
- a review flagged that the open registration endpoint let anyone self-register as ADMIN; I scoped it so anonymous callers can only create DEVELOPERs, with a first-user bootstrap exception, and proved it with tests


## Representative prompts

Below are real prompts I gave the coding agent, showing the division of labor — I specified *what*
and the constraints; the agent produced the *how*; I reviewed the output.

### Example 1 — boilerplate generation (agent does mechanical work)
You are working in my Java/Spring Boot project called IssueFlow.

Important:
You do NOT have access to my implementation plan, so this prompt contains all the Phase 3 requirements.
The Phase 3 folders/files already exist as empty placeholders.
Implement ONLY the placeholder files listed below.
Do not create or modify anything outside those files.

Project context:
- Java 21
- Spring Boot 3.4.x
- Maven
- Spring Data JPA / Hibernate
- PostgreSQL
- Lombok is available
- Existing packages:
  - com.att.tdp.issueflow.entity
  - com.att.tdp.issueflow.entity.enums
  - com.att.tdp.issueflow.repository

Phase 3 goal:
Implement DTOs, validation, custom exceptions, and a global exception handler.

Do NOT implement:
- controllers
- services
- repositories
- entities
- security/JWT classes
- schedulers
- tests
- business logic
- database logic
- mapping logic
- CSV logic

Only modify files under:

src/main/java/com/att/tdp/issueflow/dto/request/
src/main/java/com/att/tdp/issueflow/dto/response/
src/main/java/com/att/tdp/issueflow/exception/

Use these packages:

com.att.tdp.issueflow.dto.request
com.att.tdp.issueflow.dto.response
com.att.tdp.issueflow.exception

Use:
- jakarta.validation annotations for request validation
- java.time.Instant for timestamps and dueDate
- existing enum types directly:
  - UserRole
  - TicketStatus
  - TicketPriority
  - TicketType
  - AuditActorType
  - AuditAction
  - EntityType
- Java records for response DTOs where practical
- Lombok @Getter, @Setter, @NoArgsConstructor for request DTO classes

Request DTOs to implement:

1. CreateUserRequest
Fields:
- username: String, @NotBlank, @Size(max = 50)
- email: String, @NotBlank, @Email
- fullName: String, @NotBlank, @Size(max = 100)
- password: String, @NotBlank, @Size(min = 6)
- role: UserRole, @NotNull

2. UpdateUserRequest
Fields:
- username: String, optional, @Size(max = 50)
- email: String, optional, @Email
- fullName: String, optional, @Size(max = 100)
- password: String, optional, @Size(min = 6)
- role: UserRole, optional

3. LoginRequest
Fields:
- username: String, @NotBlank
- password: String, @NotBlank

4. CreateProjectRequest
Fields:
- name: String, @NotBlank, @Size(max = 100)
- description: String, optional
- ownerId: Long, @NotNull

5. UpdateProjectRequest
Fields:
- name: String, optional, @Size(max = 100)
- description: String, optional

6. AddProjectMemberRequest
Fields:
- userId: Long, @NotNull

7. CreateTicketRequest
Fields:
- projectId: Long, @NotNull
- title: String, @NotBlank, @Size(max = 200)
- description: String, optional
- status: TicketStatus, @NotNull
- priority: TicketPriority, @NotNull
- type: TicketType, @NotNull
- assigneeId: Long, optional
- dueDate: Instant, optional

8. UpdateTicketRequest
Fields:
- title: String, optional, @Size(max = 200)
- description: String, optional
- status: TicketStatus, optional
- priority: TicketPriority, optional
- type: TicketType, optional
- assigneeId: Long, optional
- dueDate: Instant, optional

9. CreateCommentRequest
Fields:
- authorId: Long, @NotNull
- content: String, @NotBlank

10. UpdateCommentRequest
Fields:
- content: String, @NotBlank

11. AddDependencyRequest
Fields:
- blockedBy: Long, @NotNull

Response DTOs to implement:

1. UserResponse
Record fields:
- Long id
- String username
- String email
- String fullName
- UserRole role
- Instant createdAt
- Instant updatedAt

Do NOT include passwordHash.

2. AuthResponse
Record fields:
- String accessToken
- String tokenType
- long expiresIn

3. ProjectResponse
Record fields:
- Long id
- String name
- String description
- Long ownerId
- String ownerUsername
- boolean deleted
- Instant deletedAt
- Long deletedById
- Long version
- Instant createdAt
- Instant updatedAt

4. TicketResponse
Record fields:
- Long id
- Long projectId
- String title
- String description
- TicketStatus status
- TicketPriority priority
- TicketType type
- Long assigneeId
- String assigneeUsername
- Instant dueDate
- boolean overdue
- Instant lastAutoEscalatedAt
- boolean deleted
- Instant deletedAt
- Long deletedById
- Long version
- Instant createdAt
- Instant updatedAt

Critical JSON requirement:
The boolean field overdue must serialize as JSON key exactly "isOverdue", not "overdue".
Use @JsonProperty("isOverdue") on the record component or accessor.

5. MentionedUserResponse
Record fields:
- Long id
- String username
- String fullName

6. CommentResponse
Record fields:
- Long id
- Long ticketId
- Long authorId
- String authorUsername
- String content
- List<MentionedUserResponse> mentionedUsers
- Long version
- Instant createdAt
- Instant updatedAt

7. AttachmentResponse
Record fields:
- Long id
- Long ticketId
- String filename
- String contentType
- Long sizeBytes
- Long uploadedById
- Instant uploadedAt

8. AuditLogResponse
Record fields:
- Long id
- AuditActorType actorType
- Long actorUserId
- AuditAction action
- EntityType entityType
- Long entityId
- Long projectId
- Long ticketId
- String oldValue
- String newValue
- Instant createdAt

Critical JSON requirements:
- createdAt must serialize as JSON key exactly "timestamp"
- action and entityType must be separate fields
- do not combine action/entityType into values like TICKET_CREATED

9. WorkloadEntryResponse
Record fields:
- Long userId
- String username
- long openTicketCount

10. ImportRowErrorResponse
Record fields:
- int row
- String reason

11. ImportSummaryResponse
Record fields:
- int created
- int failed
- List<ImportRowErrorResponse> errors

12. ErrorResponse
Record fields:
- int status
- String error
- String message
- String path
- Instant timestamp
- List<ValidationErrorDetail> details

Also create ValidationErrorDetail as a nested public static record inside ErrorResponse:
- String field
- String reason

Exception classes:
Create simple RuntimeException subclasses:
- NotFoundException
- BadRequestException
- ConflictException
- ForbiddenException
- UnauthorizedException

Each exception class should have:
- public constructor(String message) { super(message); }

GlobalExceptionHandler:
Implement in:
src/main/java/com/att/tdp/issueflow/exception/GlobalExceptionHandler.java

Requirements:
- Use @RestControllerAdvice
- Return ResponseEntity<ErrorResponse>
- Use HttpServletRequest to get the path
- Use Instant.now() for timestamp
- Use simple helper methods to avoid repeated code

Handle:

1. MethodArgumentNotValidException
- HTTP 400 Bad Request
- message: "Validation failed"
- details populated from field errors:
  - field = field name
  - reason = default message

2. HttpMessageNotReadableException
- HTTP 400 Bad Request
- message: "Malformed JSON request"

3. OptimisticLockingFailureException
- HTTP 409 Conflict
- message: "Resource was modified by another transaction"

4. NotFoundException
- HTTP 404 Not Found

5. BadRequestException
- HTTP 400 Bad Request

6. ConflictException
- HTTP 409 Conflict

7. ForbiddenException
- HTTP 403 Forbidden

8. UnauthorizedException
- HTTP 401 Unauthorized

9. Generic Exception
- HTTP 500 Internal Server Error
- message: "Internal server error"

Imports to use:
- jakarta.servlet.http.HttpServletRequest
- jakarta.validation annotations
- org.springframework.http.HttpStatus
- org.springframework.http.ResponseEntity
- org.springframework.web.bind.MethodArgumentNotValidException
- org.springframework.http.converter.HttpMessageNotReadableException
- org.springframework.dao.OptimisticLockingFailureException
- org.springframework.web.bind.annotation.ExceptionHandler
- org.springframework.web.bind.annotation.RestControllerAdvice

Important constraints:
- No database calls.
- No service calls.
- No controller endpoints.
- No repository changes.
- No entity changes.
- No business rules.
- Do not commit automatically.

After implementation:
1. Run .\mvnw clean package
2. Show git diff --stat
3. List all files modified
4. Stop and wait for review

### Example 2 — scoped test delegation 
Write integration tests for IssueFlow (Spring Boot 3.4, Java 21, package com.att.tdp.issueflow).
Use @SpringBootTest(webEnvironment = MOCK) + @AutoConfigureMockMvc + @ActiveProfiles("test")
with MockMvc, so the full filter chain (JWT auth) runs. The test profile uses H2 (create-drop).
spring-security-test is on the classpath. Read JSON with jsonPath / ObjectMapper — never string-match.

SCOPE: write ONLY the basic wiring/contract tests listed below. Do NOT write tests for status
transitions, the resolved-blocker rule, auto-assignment logic, mentions, or dependency-guard rules
— those are handled separately. Stay strictly within this list.

Helper you'll need: most endpoints require a JWT. Add a small private helper that creates a user
(POST /users, unauthenticated bootstrap, include a password), logs in (POST /auth/login), and
returns the Bearer token. Reuse it. Where an endpoint needs an ADMIN, create the user with role ADMIN.

Write these tests (each: arrange via real HTTP calls, assert status + key JSON fields):

USER / VALIDATION
1. POST /users with valid body -> 200, response has id/username/email/role.
2. POST /users with a duplicate username -> 409.
3. POST /users with a duplicate email -> 409.
4. POST /users missing a required field (e.g. no email) -> 400.
5. POST /users with an invalid role enum value -> 400.
6. GET /users/{id} for a nonexistent id -> 404.

AUTH / SECURITY
7. GET /auth/me with NO Authorization header -> 401.
8. GET /auth/me with a valid Bearer token -> 200, returns that user's profile.
9. Logout invalidates the token: create+login -> POST /auth/logout (200) -> reuse the same
   token on GET /auth/me -> 401.

PROJECTS (wiring only — not workload/membership logic)
10. POST /projects with a valid owner -> 200; then GET /projects/{id}/members shows the owner
    already listed (owner auto-membership).
11. GET /projects/{id} for a nonexistent id -> 404.
12. Soft delete: DELETE /projects/{id} (as the authenticated user) -> 200; then GET /projects
    no longer includes it, and GET /projects/{id} -> 404.

SOFT-DELETE ADMIN (if those endpoints exist; skip cleanly if not yet implemented)
13. Restore: soft-delete a project, then POST /projects/{id}/restore as an ADMIN -> 200;
    GET /projects/{id} -> 200 again.

DEPENDENCIES (wiring only — the happy path, NOT the guard rules)
14. Create two tickets in the same project, POST /tickets/{a}/dependencies {"blockedBy": b} -> 200;
    then GET /tickets/{a}/dependencies returns a list containing b with its id/title/status.

Constraints:
- Each test independent; rely on create-drop or @Transactional rollback so tables are clean per test.
- Use unique usernames/emails per test (e.g. a UUID or counter suffix) to avoid collisions.
- All success responses are 200 (the project's README contract) — assert 200, not 201/204.
- Do NOT assert on auto-assignment results, transition legality, blocker resolution, mention
  contents, or dependency rejection — those are out of scope here.

Self-check: ./mvnw clean package passes with all new tests green; report how many tests were added
and confirm none of them touch the out-of-scope logic areas.

### Example 3 — test-case design (I designed which cases prove the rule)
Add integration tests for the resolved-blocker rule in TicketService.updateTicket.
Test code only — do NOT modify production code.

The rule under test: a ticket cannot transition to DONE while any of its blockers is unresolved.
A blocker counts as RESOLVED if it is DONE *or* soft-deleted. So a ticket may move to DONE only
when EVERY blocker is (status == DONE) OR (deleted == true). An unresolved blocker causes a
ConflictException (HTTP 409).

Create: src/test/java/com/att/tdp/issueflow/service/BlockerRuleTest.java
Use @SpringBootTest + @ActiveProfiles("test") + @Transactional (rollback isolation).
@Autowire TicketService, TicketRepository, ProjectRepository, UserRepository,
ProjectMemberRepository, TicketDependencyRepository.

Setup helpers (persist via repositories directly, NOT via HTTP):
- a User (DEVELOPER) and a Project (owner = that user), user added as project member.
- a helper to create a persisted Ticket with a given status (and a flag to soft-delete it:
  set deleted=true).
- a helper to create a dependency row: blockedTicket is blocked by blockerTicket
  (persist a TicketDependency with the correct blocked/blocker fields).
- To attempt the transition, call ticketService.updateTicket(blockedId, request) with an
  UpdateTicketRequest whose status = DONE. The blocked ticket should start at IN_REVIEW
  (the legal pre-DONE state, so the transition itself is valid and only the blocker rule is
  under test — NOT the transition machine).

Write these tests:

1. OPEN BLOCKER BLOCKS DONE:
   blocked (IN_REVIEW) blocked by blocker (status IN_PROGRESS, not deleted).
   updateTicket(blocked, status=DONE) -> throws ConflictException.
   Assert via assertThrows(ConflictException.class, ...). Also reload blocked and assert its status
   is STILL IN_REVIEW (the transition did not happen).

2. DONE BLOCKER ALLOWS DONE  (LOAD-BEARING — this fails against a buggy "any dependency blocks"
   implementation):
   blocked (IN_REVIEW) blocked by blocker (status DONE, not deleted).
   updateTicket(blocked, status=DONE) -> succeeds. Reload blocked, assert status == DONE.

3. SOFT-DELETED BLOCKER ALLOWS DONE  (tests the "deleted counts as resolved" clause):
   blocked (IN_REVIEW) blocked by blocker (status IN_PROGRESS BUT deleted=true).
   updateTicket(blocked, status=DONE) -> succeeds. Reload blocked, assert status == DONE.

4. MIXED BLOCKERS, ONE STILL OPEN, STILL BLOCKED:
   blocked (IN_REVIEW) blocked by THREE blockers: one DONE, one soft-deleted, one IN_PROGRESS
   (open, not deleted). updateTicket(blocked, status=DONE) -> throws ConflictException (the single
   open blocker is enough to block). Reload blocked, assert status STILL IN_REVIEW.

5. ALL BLOCKERS RESOLVED, ALLOWED:
   blocked (IN_REVIEW) blocked by two blockers: one DONE, one soft-deleted.
   updateTicket(blocked, status=DONE) -> succeeds. Reload blocked, assert status == DONE.

Notes:
- The blocked ticket must be IN_REVIEW before the DONE attempt, so the status transition
  IN_REVIEW->DONE is itself legal and the ONLY thing being tested is the blocker rule.
- If a blocker is soft-deleted, set deleted=true on it before saving (use the entity setter), and
  ensure the dependency row still references it (the dependency row is intentionally kept for audit).
- Tests independent; rely on @Transactional rollback.

Self-check: ./mvnw clean package. Report build status, total test count, and confirm all 5 pass.
Critically confirm test 2 (DONE blocker allows DONE) passes — if it throws ConflictException, the
blocker predicate is wrong (it's treating a resolved blocker as still blocking).



### Example 4 - diagnosis-fix prompt 
```text
Fix ONE bug in TicketService.updateTicket, then add a test that proves the fix. Make no other
changes. Do NOT touch MentionService — the absence of audit logging there is intentional.

THE BUG:
In TicketService.updateTicket (service/TicketService.java, around lines 149-156), an
AuditAction.UPDATE audit row is emitted UNCONDITIONALLY at the end of the method. This is wrong:
a PATCH that changes ONLY the status currently produces both a STATUS_CHANGE row AND a spurious
UPDATE row, even though no non-status field changed.

The intended granularity:
- A status change emits exactly one AuditAction.STATUS_CHANGE row (this part already works).
- An UPDATE row must be emitted ONLY when at least one NON-status field actually changed
  (title, description, type, dueDate, or priority).
- Assignment changes keep their existing ASSIGN row (do not change that).

Required behavior after the fix:
- status-only PATCH        -> one STATUS_CHANGE row, NO UPDATE row.
- title-only (or any non-status field) PATCH -> one UPDATE row, NO STATUS_CHANGE row.
- status + non-status field PATCH -> one STATUS_CHANGE row AND one UPDATE row.

THE FIX:
Track whether any non-status field was actually modified using a boolean flag (e.g.
nonStatusFieldChanged), set it true inside each of the existing field-update if-blocks
(title, description, type, dueDate, priority), and gate the UPDATE audit row behind that flag:
emit the UPDATE row only if nonStatusFieldChanged is true. Do not change the STATUS_CHANGE logic,
the ASSIGN logic, the DONE-lock, the transition validation, or the blocker check. Keep the actual
field mutations exactly as they are — only the audit row emission becomes conditional.

THE TEST:
Add a test to the existing integration test class
src/test/java/com/att/tdp/issueflow/BasicWiringContractIntegrationTests.java, reusing the existing
helpers (createUserAndLogin, createProject, createTicket, idFrom, unique). Do NOT create a new test
class or new helpers.

Because audit rows are only readable via GET /audit-logs (ADMIN-only), the test should:
- Create an ADMIN, a project, and a ticket (status TODO).
- PATCH the ticket to change ONLY the status (TODO -> IN_PROGRESS), nothing else.
- GET /audit-logs?entityType=TICKET&entityId={ticketId} as the admin.
- Assert that the returned logs for this ticket contain a STATUS_CHANGE entry and contain NO UPDATE
  entry (this is the regression assertion — it would fail against the old unconditional code).
- Add a second case (same setup, new ticket): PATCH changing only the title, assert the logs contain
  an UPDATE entry and NO STATUS_CHANGE entry.
Parse the JSON response by action field; do not string-match.

SELF-CHECK:
Run ./mvnw clean package. Report: build status, total test count, and confirm the new test(s) pass.
Confirm you changed ONLY TicketService.java and the test file, and did NOT modify MentionService or
any other service.
```
## Summary

AI accelerated boilerplate and surfaced tradeoffs. It did not make architectural or business-logic
decisions for me — those, and the responsibility for them, are mine. I can explain and defend every
design decision, business rule, and test in this project.