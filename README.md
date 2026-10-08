# Smart Library Management System

A microservices-based library circulation system: nine independently
deployable Spring Boot services, a React frontend powered by Vite, PostgreSQL
for storage, and RabbitMQ for event-driven communication between services.

**Member Service was already built and tested (via Postman) before this work
started, and has been preserved exactly as provided — nothing in it was
changed.** Catalog, Borrowing, Fine, and Notification Services, the
frontend, the database script, the Postman collection, and this README are
new, built to match Member Service's existing conventions (Java 21, Spring
Boot 4.1.1, Maven, package/groupId style, port range, `ddl-auto=update`).

---

## ⚠️ Read this first: what has and hasn't been verified

This project was built and statically reviewed line-by-line in a sandboxed
environment with **no network access to Maven Central** (outbound access is
restricted to a small allowlist that doesn't include
`repo.maven.apache.org` / `repo1.maven.org`), and **no local PostgreSQL or
RabbitMQ instance**. That means:

- I could **not** run `mvn compile`, `mvn test`, or start any service here.
- Nothing in this project has been compiled or executed by me. Every file
  was written carefully and reviewed by re-reading it, but "reviewed" is not
  the same as "verified by running it."
- **You should treat "run `mvnw clean package` on all five services" as the
  actual first step**, not a formality — that's the first time this code
  will be compiled against real dependencies.

I want to be upfront about this rather than imply a build/test pass that
didn't happen. Section [Known limitations and assumptions](#known-limitations-and-assumptions)
below lists the specific things most likely to need a small fix once you
compile for real (mistyped Spring Boot API calls are the most likely
category of error, since that's exactly what static review can miss).

---

## Table of contents

- [Technology stack](#technology-stack)
- [Folder structure](#folder-structure)
- [Ports](#ports)
- [Architecture and event flow](#architecture-and-event-flow)
- [Prerequisites](#prerequisites)
- [PostgreSQL setup](#postgresql-setup-windows-powershell)
- [RabbitMQ + Erlang setup (Windows, no Docker)](#rabbitmq--erlang-setup-windows-no-docker)
- [Configuring credentials](#configuring-credentials)
- [Building each service](#building-each-service)
- [Running everything](#running-everything)
- [Frontend](#frontend)
- [Postman collection](#postman-collection)
- [Inspecting the Dead Letter Queue](#inspecting-the-dead-letter-queue)
- [Troubleshooting](#troubleshooting)
- [Known limitations and assumptions](#known-limitations-and-assumptions)

---

## Technology stack

- Java 21, Spring Boot 4.1.1 (Web, Data JPA, Validation, AMQP), Maven (wrapper included)
- PostgreSQL (developed against PostgreSQL 18)
- RabbitMQ (topic exchange for pub/sub, per-consumer queues, DLX/DLQ on Notification Service)
- React 18 frontend with Vite
- Postman for API testing

## Folder structure

```
smart-library-management/
├── member-service/        # Pre-existing, preserved exactly as provided
├── catalog-service/        # New
├── borrowing-service/      # New
├── fine-service/            # New
├── notification-service/    # New
├── frontend/
│   ├── src/
│   │   ├── App.jsx
│   │   ├── main.jsx
│   │   └── styles.css
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── database/
│   └── library_db.sql
├── postman/
│   └── Smart-Library.postman_collection.json
├── README.md
└── .gitignore
```

## Ports

| Service              | Port |
|-----------------------|------|
| member-service         | 8081 (pre-existing) |
| catalog-service         | 8082 |
| borrowing-service        | 8083 |
| fine-service              | 8084 |
| notification-service      | 8085 |
| authentication-service    | 8086 |
| api-gateway-service       | 8087 |
| audit-service             | 8088 |
| recommendation-service    | 8089 |

PostgreSQL: `5432` (default). RabbitMQ: `5672` (AMQP), `15672` (Management UI).

---

## Architecture and event flow

**Synchronous calls** (plain REST, via Spring's `RestClient`):

- Borrowing Service → Member Service: `GET /members/{id}` — confirms the member exists before creating a borrowing.
- Borrowing Service → Catalog Service: `GET /books/{id}` — confirms the book exists and has an available copy.

**Asynchronous events** (RabbitMQ, topic exchange `library.events`):

| Routing key | Published by | Consumed by |
|---|---|---|
| `book.borrowed` | Borrowing Service | Catalog Service (decrement copies), Notification Service |
| `book.returned` | Borrowing Service | Catalog Service (increment copies, may trigger `book.available`), Fine Service (charge if late), Notification Service |
| `book.overdue` | Borrowing Service (scheduled job) | Fine Service (start/continue accruing a fine), Notification Service is *not* subscribed to this one directly — it only hears about the fine once Fine Service raises `fine.generated` |
| `fine.generated` | Fine Service | Notification Service |
| `fine.paid` | Fine Service | Notification Service |
| `book.available` | Catalog Service | Notification Service |

This is genuine **publish/subscribe**: several independent queues
(`catalog.book-borrowed.queue`, `notification.book-borrowed.queue`, etc.)
are all bound to the same exchange and routing key, so every interested
service gets its own copy of each event. It is also a **work queue**
whenever you run more than one instance of the same service — e.g. two
`notification-service` processes both consuming from
`notification.book-borrowed.queue` will have RabbitMQ split the messages
between them (competing consumers), rather than each instance getting every
message.

**Reliability model (deliberately simple, no transactional outbox):** a
publisher commits its database write first, then calls `rabbitTemplate.
convertAndSend(...)` right after. In the rare case a service crashes in
that exact gap, the event is lost and downstream services never hear about
it. This is an accepted, documented trade-off rather than something solved
with an outbox table — see `EventPublisher` in Borrowing/Fine/Catalog
Service for the same comment in code. What RabbitMQ **does** guarantee is
at-least-once delivery once a publish succeeds, which is why every
consumer is idempotent:

**Idempotency:** every consuming service has its own
`<service>_processed_events` table (a one-column-that-matters "have I seen
this event ID" ledger). Each listener checks it, does its business-logic
update, and inserts the event ID, all in one transaction — so a
redelivered message (RabbitMQ's at-least-once guarantee, or a manual
requeue) is a safe no-op rather than double-processing.

**Retry + Dead Letter Queue** is implemented on **Notification Service**
specifically (per the original spec): each of its five queues declares
`x-dead-letter-exchange` / `x-dead-letter-routing-key` pointing at a
`library.dlx` topic exchange; a `RetryOperationsInterceptor` retries a
failing listener up to `library.notification.retry.max-attempts` times
(default 3, 2000ms apart), and once exhausted, rejects the message without
requeueing — which RabbitMQ then routes to that queue's own `*.dlq` queue
(e.g. `notification.fine-generated.dlq`) instead of retrying forever or
dropping it silently. See [Inspecting the Dead Letter
Queue](#inspecting-the-dead-letter-queue) below.

Catalog Service and Fine Service consumers are idempotent (safe to
redeliver) but do **not** have this same retry/DLQ wrapping — an uncaught
exception there falls back to RabbitMQ/Spring AMQP's default behavior
(requeue and retry indefinitely). That's a scope decision matching what
the spec asked for section-by-section, not an oversight; see [Known
limitations](#known-limitations-and-assumptions) if you want to extend the
same pattern to those two services.

**Overdue detection:** Borrowing Service runs a `@Scheduled` job
(`library.overdue-check.cron`, default: every hour on the hour) that finds
every `BORROWED` borrowing whose due date has passed and which hasn't
already had an overdue event published for it (a persisted
`overdueEventPublished` flag prevents re-publishing on the next run or
after a restart), and publishes `book.overdue` for each.

**"Exactly once" is not claimed anywhere in this system.** The honest
description is: at-least-once delivery, made safe by idempotent consumers.

---

## Prerequisites

- **Java 21** JDK
- **Maven** — not required globally; every service includes the Maven
  Wrapper (`mvnw` / `mvnw.cmd`), copied from Member Service's own wrapper
  (version 3.9.16), so `.\mvnw.cmd ...` downloads the right Maven version
  itself the first time you run it. You do still need internet access to
  Maven Central for this to work, obviously.
- **PostgreSQL** (18, matching Member Service's existing setup), with the
  service running and `psql.exe` reachable.
- **RabbitMQ**, running locally without Docker, with the management
  plugin enabled.
- A modern browser for the frontend (no install needed — it's static files).

---

## PostgreSQL setup (Windows PowerShell)

Using the same PostgreSQL 18 install Member Service already uses. Adjust
the path below if your install directory differs.

```powershell
# 1. Create the (empty) database - only needs doing once.
& "C:\Program Files\PostgreSQL\18\bin\createdb.exe" -U postgres library_db
# You'll be prompted for the postgres user's password.

# 2. (Optional) Pre-create tables and load sample data.
#    Not required - every service creates/updates its own tables on
#    startup (spring.jpa.hibernate.ddl-auto=update). This step just lets
#    you look around in pgAdmin before starting any service.
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d library_db -f "C:\path\to\smart-library-management\database\library_db.sql"
```

Or interactively in `psql`:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres
```
```sql
CREATE DATABASE library_db;
\c library_db
\i 'C:/path/to/smart-library-management/database/library_db.sql'
\q
```

## RabbitMQ + Erlang setup (Windows, no Docker)

1. **Install Erlang** from https://www.erlang.org/downloads — check
   RabbitMQ's current Erlang compatibility matrix on rabbitmq.com before
   picking a version (I can't verify version compatibility from this
   sandbox). The installer typically sets an `ERLANG_HOME` environment
   variable for you.
2. **Install RabbitMQ Server** from https://www.rabbitmq.com/docs/install-windows
   (the `.exe` installer). It installs as a Windows service named
   `RabbitMQ`.
3. **Enable the management plugin** (PowerShell, adjust the version folder name):
   ```powershell
   & "C:\Program Files\RabbitMQ Server\rabbitmq_server-<version>\sbin\rabbitmq-plugins.bat" enable rabbitmq_management
   ```
4. **Restart the service** so the plugin takes effect:
   ```powershell
   # Run PowerShell as Administrator
   net stop RabbitMQ
   net start RabbitMQ
   ```
5. **Verify**: open http://localhost:15672 — default login is `guest` /
   `guest` (this only works when connecting from `localhost`, which is the
   case here).

All five services default to `spring.rabbitmq.host=localhost`,
`port=5672`, `username=guest`, `password=guest` — matching a fresh
install with no extra configuration.

---

## Configuring credentials

**Member Service's `application.properties` already contains its real,
working PostgreSQL password in plain text** (`Admin@2026`), because it was
provided that way and the instructions were explicit not to touch Member
Service's configuration. It has been left exactly as-is. **If this
repository is ever pushed anywhere shared or public, rotate that password
first** — right now it's sitting in git history the moment you commit.

The four new services instead read `spring.datasource.password=
${DB_PASSWORD:changeme}` — i.e. an environment variable, defaulting to the
placeholder `changeme` if unset (which will simply fail to authenticate
against a real database, safely, rather than silently using a guessed real
password). Before starting them, set the *real* password in each terminal:

```powershell
$env:DB_PASSWORD = "your-actual-postgres-password"
```

(You'll need to set this in every PowerShell window you start a service
from, since environment variables don't persist across windows unless you
set them at the User/System level via `setx`.)

---

## Building each service

From the `smart-library-management` folder:

```powershell
cd member-service
.\mvnw.cmd clean package
cd ..\catalog-service
.\mvnw.cmd clean package
cd ..\borrowing-service
.\mvnw.cmd clean package
cd ..\fine-service
.\mvnw.cmd clean package
cd ..\notification-service
.\mvnw.cmd clean package
cd ..
```

Each should end with `BUILD SUCCESS`. If one doesn't, see
[Troubleshooting](#troubleshooting) — and please treat that as expected
maintenance for a project built without the ability to compile it, not a
sign something is fundamentally wrong.

---

## Running everything

Start PostgreSQL and RabbitMQ first. Then open a separate VS Code terminal
for each service, change to the project root in each terminal, and run its
command. Keep every service terminal open. Start Member and Catalog before
Borrowing because Borrowing calls both synchronously.

For each database-backed service (Member, Catalog, Borrowing, Fine, and
Notification), set the PostgreSQL password in that service's terminal
before running Maven. The variable applies only to that terminal:

```powershell
$secret = Read-Host "PostgreSQL password for postgres" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try { $env:SPRING_DATASOURCE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
```

Run these commands from `C:\Users\ELCOT\Documents\smart-library-management`,
one per terminal:

```powershell
mvn -pl member-service spring-boot:run
mvn -pl catalog-service spring-boot:run
mvn -pl borrowing-service spring-boot:run
mvn -pl fine-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl authentication-service spring-boot:run
mvn -pl api-gateway-service spring-boot:run
mvn -pl audit-service spring-boot:run
mvn -pl recommendation-service spring-boot:run
```

Wait for each service's `Started ...Application` message before moving on.
Leave all service terminals running while using the frontend.

## Frontend

The frontend is a React app served locally by Vite. In a new PowerShell
terminal:

```powershell
cd C:\Users\ELCOT\Documents\smart-library-management\frontend
npm.cmd install
npm.cmd run dev
```

Open **http://localhost:5500** in your browser. Keep this terminal open too.
The React interface provides the dashboard, member and book management,
borrowing and returns, fines, and notifications. Its local backend URLs are
configured in `frontend/src/App.jsx`.

## Postman collection

Import `postman/Smart-Library.postman_collection.json`. It's organized
into five folders (Members, Catalog, Borrowing, Fines, Notifications) meant
to be run **top to bottom** — later requests read collection variables
(`memberId`, `bookId`, `borrowingId`, `fineId`, etc.) that earlier requests'
**Tests** scripts set from the response body.

Either click through requests in order manually, or use Postman's
**Collection Runner** to run the whole thing in one click. The collection
description (visible in Postman) and each request's own description note
important context - in particular: the Fines folder's "Pay Fine" request
will legitimately have nothing to pay if the Borrow → Return flow above it
completed in under a second (nothing was overdue), and skips its assertion
in that case rather than failing.

I was not able to actually run this collection against live services in
this sandbox (no RabbitMQ/Postgres available here) - it's been written and
reviewed, not executed. Running it yourself is the real first test.

## Inspecting the Dead Letter Queue

To see the retry → DLQ path in action:

1. Open http://localhost:15672 → **Queues**.
2. You'll see `notification.book-borrowed.queue`,
   `notification.book-returned.queue`, `notification.fine-generated.queue`,
   `notification.fine-paid.queue`, `notification.book-available.queue` (the
   live queues), and their five `*.dlq` counterparts (currently empty).
3. **To force a message to fail and land in a DLQ**: with
   `notification-service` running, go to any of the live queues (e.g.
   `notification.book-borrowed.queue`) → **Publish message** → paste a body
   that isn't valid JSON for that event, e.g. `not valid json`, and publish
   it directly to that queue (not through the exchange). The listener will
   throw on deserialization, retry 3 times roughly 2 seconds apart (watch
   `notification-service`'s console log), then the message will appear in
   `notification.book-borrowed.dlq`.
4. Click the DLQ queue → **Get messages** to inspect it. The `x-death`
   header on the message records why and when it was dead-lettered.

---

## Troubleshooting

- **A service won't start / port already in use** — something else is
  already listening on that port; stop it, or change `server.port` in that
  service's `application.properties`.
- **`FATAL: password authentication failed for user "postgres"`** — the
  `DB_PASSWORD` environment variable in that terminal doesn't match your
  real Postgres password. Re-set it in that specific window.
- **`Connection refused` talking to RabbitMQ** — RabbitMQ service isn't
  running, or the management plugin isn't enabled yet (that only affects
  the web UI at :15672, not AMQP itself on :5672).
- **CORS errors in the browser console** — double-check the service
  you're calling is actually running on the port `script.js`'s `CONFIG`
  object expects; a CORS error in Chrome/Firefox often actually means "the
  request never got a response at all" (e.g. the port is closed), not a
  genuine CORS policy issue, since every controller here already has
  `@CrossOrigin`.
- **`mvnw.cmd` hangs or fails on first run** — that's it downloading Maven
  itself; it needs a working internet connection to
  `repo.maven.apache.org` and can be slow on a first run per machine.
- **Borrowing always fails with "Member Service is unreachable"** — start
  Member Service first, and confirm it's actually listening on 8081
  (`member-service.url` in `borrowing-service`'s `application.properties`).

---

## Known limitations and assumptions

- **Not compiled or run by me** - see the warning at the top of this file.
  Please run the build commands above as your real first step.
- **No transactional outbox** - accepted per the project's own reliability
  choice; a crash in the narrow window between a DB commit and the
  following RabbitMQ publish loses that one event. Documented in code at
  every `EventPublisher`.
- **Retry + DLQ is Notification-Service-only** - Catalog and Fine Service
  consumers are idempotent but use RabbitMQ/Spring AMQP's default
  requeue-on-exception behavior rather than the same bounded-retry-then-DLQ
  pattern. Straightforward to copy over (see
  `notification-service/.../config/RabbitMQConfig.java`) if you want it
  everywhere.
- **No authentication/authorization** on any endpoint, anywhere - matches
  the original spec, which didn't ask for it, but obviously not
  production-appropriate as-is.
- **No hold/reservation system** - `BookAvailable` notifications are
  general/broadcast (no specific member is "waiting" for a book), since
  there's no concept of a member reserving a book in this project's scope.
- **No update/delete for members or books** - matches what Member Service
  already implements (create + read only) and what the spec asked Catalog
  Service to expose.
- **No pagination** on any list endpoint - fine at demo/coursework scale,
  would need it before this saw a real member base.
- **Member Service's DB password is committed in plain text** - see
  [Configuring credentials](#configuring-credentials). Left as-is because
  Member Service was explicitly not to be modified; rotate it before
  sharing this repository anywhere.
- **groupId vs. package naming** - Member Service's Maven `groupId` is
  `com.library`, but its Java package is `com.example.member_service` (a
  mismatch, presumably from renaming the groupId after generating the
  project from Spring Initializr). Every new service mirrors this exact
  pattern (`groupId: com.library`, package `com.example.<name>_service`)
  for consistency with what already existed, rather than "fixing" it.
- **Fine/loan-period configuration is global**, not per-book or per-member
  (`library.fine.per-day-amount`, `library.borrowing.loan-period-days`) -
  matches the spec's ask for "a configurable fine-per-day amount," not a
  per-book override system.
