# LeaveSync

A backend REST API for employee leave management, built with Spring Boot.

LeaveSync replaces manual spreadsheet and email-based leave processes for
mid-size UK companies. It handles leave requests, approval workflows, balance
tracking, notifications, and compliance audit trails.

## Tech Stack

- Java 21
- Spring Boot 3.5.14
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Redis 7
- Flyway
- Docker

## Features

- Role-based access control - Employee, Manager, HR, Admin
- Leave request and approval workflow
- Annual leave balance tracking with carry over and year-end expiry
- Working day calculation excluding weekends and UK bank holidays
- Overlap detection and notice period warnings
- Escalation workflow for unactioned requests
- Email notifications for all key events
- Full audit trail for HR compliance
- CSV export for leave reports

## Running Locally

**Prerequisites:** Docker, Java 21

**1. Clone the repo**
```
git clone https://github.com/NMB99/LeaveSync.git
cd LeaveSync
```

**2. Start Docker containers**
```
docker compose up -d
```

**3. Run the application**
```
./mvnw spring-boot:run
```

The API will be available at http://localhost:8080

## Project Status

In active development — started June 2026.

API documentation via Swagger will be available once the first endpoints are live.