# LeaveSync - Leave Management API

A backend REST API for employee leave management, built with Spring Boot. Multi-role approval workflows, Redis caching, scheduled escalation jobs, full audit logging, and HR reporting with CSV export.

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-brightgreen?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square)
![JWT](https://img.shields.io/badge/Auth-JWT-yellow?style=flat-square)
![Tests](https://img.shields.io/badge/Tests-147%20passing-success?style=flat-square)
![CI](https://github.com/NMB99/LeaveSync/actions/workflows/ci.yml/badge.svg)

This is my second portfolio project, built as a deliberate step up from [TeamSync](https://github.com/NMB99/TeamSync). TeamSync was a single-approval-level standup tracker. LeaveSync adds multi-role approval chains, caching, scheduled jobs, audit compliance, and pagination - areas TeamSync didn't need to cover.

---

## The Problem

Harlow Digital is a remote-first UK company with about 200 employees. Right now leave requests go through spreadsheets and email threads. There's no single source of truth for who's off. There's no audit trail for HR compliance. Unactioned requests just sit there until someone remembers to chase them. LeaveSync replaces that with a proper API. It handles role-scoped approval routing, automatic escalation for stale requests, and a full audit trail on every status change.

---

## Live Demo

**Live:** [Swagger UI](https://leavesync-api-60lv.onrender.com/swagger-ui.html)

**Visitor credentials (HR role):**
- Email: `visitor@builtmeup.dev`
- Password: `Visitor@1234`

Login via `POST /api/auth/login`, click **Authorize**, paste the token.

> Free-tier hosting spins down after 15 minutes of inactivity. First request can take 30-45 seconds to wake up - subsequent requests are fast.

Prefer to run it locally instead? See **Getting Started** below.

---

## A Day in the Life

An employee submits an annual leave request. LeaveSync calculates the working days, excluding weekends and UK bank holidays. It warns them if it's short notice, or if a teammate is already off those dates. The request routes to their manager. If the manager hasn't actioned it in 3 working days, they get a reminder. By day 5 it escalates straight to HR. The manager is bypassed, not nagged again. If the request sits unactioned within 24 hours of the start date, both the manager and HR get an urgent flag. Every status change gets written to an immutable audit log, with a timestamp and who actioned it. Submitted, approved, rejected, escalated: all of it logged, because that's non-negotiable for HR compliance. Once approved, the balance is deducted automatically, respecting carry-over and year-end expiry rules.

---

## Tech Stack

| Layer         | Technology                                                        |
|---------------|-------------------------------------------------------------------|
| Language      | Java 21                                                           |
| Framework     | Spring Boot 3.5.14                                                |
| Security      | Spring Security, JWT (JJWT 0.12.6)                                |
| Persistence   | Spring Data JPA, Hibernate                                        |
| Database      | PostgreSQL 16                                                     |
| Caching       | Redis 7                                                           |
| Migrations    | Flyway                                                            |
| Email         | JavaMailSender (Mailpit locally, Resend in production)            |
| Testing       | JUnit 5, Mockito                                                  |
| Documentation | Swagger UI (springdoc-openapi 2.8.9)                              |
| Build Tool    | Maven                                                             |
| Deployment    | Render (Docker), Neon (Postgres), Upstash (Redis), Resend (email) |

---

## Features

- **Role-based access control** - four roles (Employee, Manager, HR, Admin) with fine-grained, service-level permission checks
- **Multi-role approval routing** - Employee → Manager → HR → Admin, with automatic HR rerouting when no valid manager exists
- **Working day calculation** - excludes weekends and UK bank holidays, feeds notice period and escalation logic
- **Leave balance tracking** - carry-over cap, year-end expiry, pro-rated allocation for new joiners
- **Escalation scheduler** - Day 3 reminder, Day 5 HR escalation, 24-hour urgent flag
- **Public holiday management** - bulk create/update/delete with all-or-nothing batch validation, Redis-cached lookups
- **Full audit trail** - every status change logged with timestamp and actioning user, immutable
- **HR reporting** - who's off, balance summary, leave history, absence patterns, all with CSV export
- **Pagination** - on every list endpoint
- **Redis caching** - leave types and public holiday lookups
- **Scheduled year-end job** - automatic rollover, carry-over, and expiry warnings
- **Swagger UI** - interactive docs with a pre-seeded visitor login
- **CI/CD** - GitHub Actions runs the test suite on every push; merges to `main` trigger an automatic deploy to Render via webhook
- **147 unit tests** - service layer, Mockito + JUnit 5, CI-enforced on every push

---

## Architecture

````
┌────────────────────────────────────────────┐
│              REST Controllers              │
└─────────────────────┬──────────────────────┘
                      │
┌─────────────────────▼──────────────────────┐
│               Service Layer                │
│  RBAC checks, approval routing, audit log  │
└───────┬─────────────┬─────────────┬────────┘
        │             │             │
  ┌─────▼─────┐   ┌───▼───┐   ┌─────▼─────┐
  │   Email   │   │ Redis │   │ Scheduled │
  │  Service  │   │ Cache │   │   Jobs    │
  └─────┬─────┘   └───┬───┘   └─────┬─────┘
        │             │             │
        └─────────────┼─────────────┘
┌─────────────────────▼──────────────────────┐
│              Repository Layer              │
└─────────────────────┬──────────────────────┘
                      │
┌─────────────────────▼──────────────────────┐
│             PostgreSQL Database            │
└────────────────────────────────────────────┘
````

Security: JWT Auth Filter intercepts every request before reaching controllers.

Spring Boot fits the ecosystem well here. Spring Security's method-level `@PreAuthorize` maps cleanly onto a four-role permission model that changes per endpoint. PostgreSQL handles the relational integrity between leave requests, balances, and audit logs, where referential correctness genuinely matters. Redis caches two lookups that are read constantly but change rarely: leave types and public holidays. A stale cache there is a performance question, not a correctness bug.

---

## Role Permissions

| Action                      | EMPLOYEE | MANAGER                          | HR             |  ADMIN             |
|-----------------------------|----------|----------------------------------|----------------|--------------------|
| Submit leave request        | ✅        | ✅                                | ✅              | ❌                  |
| Approve / reject leave      | ❌        | ✅ own team, non-HR-approval only | ✅ any          | ✅ HR fallback only |
| View own balance            | ✅        | ✅                                | ✅              | ❌ no balance       |
| View team / company balance | ❌        | ✅ own teams                      | ✅ company-wide | ✅ any              |
| Create / manage users       | ❌        | ❌                                | ✅              | ✅                  |
| Deactivate users            | ❌        | ❌                                | ❌              | ✅                  |
| Create / manage teams       | ❌        | ❌ view own only                  | ✅              | ✅                  |
| Configure leave types       | ❌        | ❌                                | ✅              | ✅                  |
| Manage public holidays      | ❌        | ❌                                | ✅              | ✅                  |
| View HR reports             | ❌        | ✅ who's-off, own team only       | ✅ all reports  | ✅ all reports      |

---

## Design Decisions

- **`LeaveType.code` vs editable `name`.** Business logic (notice periods, sick leave validation, reporting) originally matched on `LeaveType.name`. Since HR can rename leave types at runtime, that meant a rename could silently break approval routing. Added an immutable `code` column, seed-only and never exposed via the API, and moved all business-logic matching to it.
- **Public holidays via bulk API, not annual Flyway migrations.** Seeding bank holidays through a new migration every year doesn't scale. First instinct was storing just month and day, to make holidays year-independent. That was rejected because UK bank holidays like Easter Monday and the late-May bank holiday aren't fixed calendar dates, so they can't be derived from month and day alone. Built a bulk `POST /api/public-holidays` endpoint instead, with all-or-nothing batch validation.
- **CSV export as separate typed endpoints, not `?format=csv`.** A single endpoint handling both JSON and CSV would need to return `ResponseEntity<Object>`, losing compile-time type safety. Each report gets a dedicated `/report-name/export-csv` sibling endpoint instead. It stays fully typed, and a future PDF export can slot in the same way with zero changes to existing code.
- **Escalation clock normalised to the next working day.** A leave request submitted on a Saturday would never match a "3 working days later" query, since working-day arithmetic never lands on a weekend. Normalising the escalation clock's start date to the next working day at submission time fixed a bug that would have silently broken the Day 3 and Day 5 escalation jobs.
- **Redis caches the public holiday lookup, not the date-range method.** The natural place to cache looked like the working-day calculation itself, but its methods take `LocalDate` ranges as parameters. Caching on that would key on ranges that rarely repeat, growing the cache indefinitely with a near-zero hit rate. Caching the underlying public holiday lookup instead, keyed on region and year, is a genuinely stable key. It gets the same performance win without that problem.
- **HR leave routes to other HR first, falls back to Admin.** HR submitting leave originally routed straight to Admin. Changed so it first checks for other active HR users, for peer review and to keep Admin out of leave approvals where possible. It only falls back to Admin if the submitter is the sole active HR user. That guarantees the request is never unroutable.

---

## API Endpoints

### Auth
| Method | Endpoint           | Description              | Access        |
|--------|--------------------|--------------------------|---------------|
| POST   | `/api/auth/login`  | Login, returns JWT       | Public        |
| POST   | `/api/auth/logout` | Invalidate current token | Authenticated |

### Users
| Method | Endpoint                     | Description                        |  Access                         |
|--------|------------------------------|------------------------------------|---------------------------------|
| POST   | `/api/users`                 | Create user, sends invite email    | ADMIN, HR                       |
| POST   | `/api/users/accept-invite`   | Set password via invite token      | Public                          |
| POST   | `/api/users/forgot-password` | Request password reset             | Public                          |
| POST   | `/api/users/reset-password`  | Reset password via token           | Public                          |
| GET    | `/api/users`                 | List users, paginated, role-scoped | ADMIN, HR, MANAGER              |
| GET    | `/api/users/{id}`            | Get user by ID, scoped             | ADMIN, HR, MANAGER, own profile |
| PUT    | `/api/users/{id}`            | Update user details                | ADMIN, HR                       |
| PATCH  | `/api/users/me/mobile`       | Update own mobile number           | Authenticated                   |
| PATCH  | `/api/users/{id}/deactivate` | Deactivate user                    | ADMIN                           |

### Teams
| Method | Endpoint          | Description                            | Access             |
|--------|-------------------|----------------------------------------|--------------------|
| POST   | `/api/teams`      | Create team                            | ADMIN, HR          |
| GET    | `/api/teams`      | List teams, paginated, scoped          | ADMIN, HR, MANAGER |
| GET    | `/api/teams/{id}` | Get team by ID, scoped                 | ADMIN, HR, MANAGER |
| PUT    | `/api/teams/{id}` | Update team                            | ADMIN, HR          |
| DELETE | `/api/teams/{id}` | Delete team (blocked if members exist) | ADMIN, HR          |

### Leave Requests
| Method | Endpoint                           | Description                            | Access                |
|--------|------------------------------------|----------------------------------------|-----------------------|
| POST   | `/api/leave-requests`              | Submit leave request                   | EMPLOYEE, MANAGER, HR |
| GET    | `/api/leave-requests/my`           | Own leave requests, paginated          | Authenticated         |
| GET    | `/api/leave-requests`              | Leave requests, role-scoped, paginated | Authenticated         |
| GET    | `/api/leave-requests/{id}`         | Get by ID, scoped                      | Authenticated         |
| POST   | `/api/leave-requests/{id}/cancel`  | Cancel own request                     | Authenticated (owner) |
| POST   | `/api/leave-requests/{id}/approve` | Approve request                        | MANAGER, HR, ADMIN    |
| POST   | `/api/leave-requests/{id}/reject`  | Reject request with reason             | MANAGER, HR, ADMIN    |

### Leave Balances
| Method | Endpoint                       | Description                  | Access                |
|--------|--------------------------------|------------------------------|-----------------------|
| GET    | `/api/leave-balances/me`       | Own balance for a given year | EMPLOYEE, MANAGER, HR |
| GET    | `/api/leave-balances/{userId}` | Balance by user ID, scoped   | MANAGER, HR, ADMIN    |
| GET    | `/api/leave-balances`          | Team balances, paginated     | MANAGER               |

### Leave Types
| Method | Endpoint                | Description              | Access        |
|--------|-------------------------|--------------------------|---------------|
| GET    | `/api/leave-types`      | List leave types, cached | Authenticated |
| PUT    | `/api/leave-types/{id}` | Update leave type config | ADMIN, HR     |

### Public Holidays
| Method | Endpoint                    | Description                            | Access        |
|--------|-----------------------------|----------------------------------------|---------------|
| POST   | `/api/public-holidays`      | Bulk create, all-or-nothing validation | ADMIN, HR     |
| GET    | `/api/public-holidays`      | List, optional region/year filter      | Authenticated |
| GET    | `/api/public-holidays/{id}` | Get by ID                              | Authenticated |
| PUT    | `/api/public-holidays/{id}` | Update                                 | ADMIN, HR     |
| DELETE | `/api/public-holidays/{id}` | Delete                                 | ADMIN, HR     |

### Reports
| Method | Endpoint                                   | Description                      | Access             |
|--------|--------------------------------------------|----------------------------------|--------------------|
| GET    | `/api/reports/whos-off`                    | Who's off on a given date        | ADMIN, HR, MANAGER |
| GET    | `/api/reports/balance-summary`             | Company-wide balance summary     | ADMIN, HR          |
| GET    | `/api/reports/leave-history`               | Full audit trail                 | ADMIN, HR          |
| GET    | `/api/reports/absence-patterns`            | Sick leave frequency by employee | ADMIN, HR          |
| GET    | `/api/reports/whos-off/export-csv`         | CSV export                       | ADMIN, HR, MANAGER |
| GET    | `/api/reports/balance-summary/export-csv`  | CSV export                       | ADMIN, HR          |
| GET    | `/api/reports/leave-history/export-csv`    | CSV export                       | ADMIN, HR          |
| GET    | `/api/reports/absence-patterns/export-csv` | CSV export                       | ADMIN, HR          |

### Working Days
| Method | Endpoint                  | Description                          | Access        |
|--------|---------------------------|--------------------------------------|---------------|
| GET    | `/api/working-days/count` | Count working days between two dates | Authenticated |

---

## Getting Started

### Prerequisites
- Java 21
- Docker

### Setup

**1. Clone the repo**
```bash
git clone https://github.com/NMB99/LeaveSync.git
cd LeaveSync
```

**2. Create a `.env` file in the project root**
```env
POSTGRES_DB=leavesync
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

**3. Start Docker containers** (PostgreSQL, Redis, MailPit)
```bash
docker compose up -d
```

**4. Run the application**
```bash
./mvnw spring-boot:run
```

The app defaults to the `local` Spring profile, which points at the local containers above, no further config needed for local development.

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- MailPit (view sent emails): `http://localhost:8025`

---

## Running Tests

```bash
./mvnw test
```

147 unit tests covering the service layer - happy paths, role-based access rules, and edge cases, using JUnit 5 and Mockito. Runs automatically on every push and pull request via GitHub Actions.

---

## Project Structure

````
src/main/java/com/leavesync/
├── auth/               # Login, logout
├── user/               # User CRUD, invite flow, password management
├── team/               # Team CRUD
├── leaverequest/       # Leave request submission, approval, rejection
├── leavebalance/       # Leave balance enquiry
├── leavetype/          # Leave type configuration
├── publicholiday/      # Public holiday management, Redis-cached lookups
├── report/             # HR reports, CSV export
├── workingday/         # Working day calculation
├── escalation/         # Scheduled escalation jobs
├── yearend/            # Scheduled year-end rollover job
├── email/              # Email service (shared infrastructure)
├── security/           # JWT filter, JWT service, security config
├── exception/          # Custom exceptions, global exception handler
├── entity/             # JPA entities
├── enums/              # Role, LeaveStatus
├── config/             # OpenAPI, Redis configuration
└── common/             # Shared response wrappers (pagination)
````

---

## Roadmap

- [ ] Testcontainers-based integration tests (`LeaveSyncApplicationTests` currently disabled)
- Code quality backlog (DRY passes, method extraction) - deliberately not scheduled for now

---

## Author

**Nilay Bhaisare**

MSc Computer Science, University of Bristol

[LinkedIn](https://www.linkedin.com/in/nilay-bhaisare) · [GitHub](https://github.com/NMB99)