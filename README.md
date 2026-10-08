# Smart Library Management System

A microservices-based library circulation application built using Spring Boot and React. The system manages members, books, borrowings, returns, fines, notifications, authentication, audit events, and recommendations. PostgreSQL is used for persistent data storage, while RabbitMQ enables asynchronous communication between services.

> **Status:** Local development / demonstration project
> The services and frontend are designed to run on a single machine. Review the security and production considerations before deploying the application or using real member data.

---

## Table of Contents

* [Features](#features)
* [Technology Stack](#technology-stack)
* [Architecture](#architecture)
* [Event-Driven Architecture](#event-driven-architecture)
* [Prerequisites](#prerequisites)
* [Configuration](#configuration)
* [Running the Application](#running-the-application)
* [Build and Test](#build-and-test)
* [API Endpoints](#api-endpoints)
* [Postman Collection](#postman-collection)
* [Project Structure](#project-structure)
* [Troubleshooting](#troubleshooting)
* [Security and Production Considerations](#security-and-production-considerations)

---

## Features

* Register and manage library members
* Add and browse books with available copy counts
* Borrow and return books
* Validate member and book availability before creating borrowings
* Calculate, view, and pay overdue fines
* Generate and retrieve member notifications
* Publish and consume library events asynchronously using RabbitMQ
* Record and retrieve audit events
* Retrieve popular-book recommendations
* Retrieve member-specific recommendations
* Provide authentication and token-validation endpoints
* Provide a React-based dashboard for major library workflows

---

## Technology Stack

| Component     | Technology                 |
| ------------- | -------------------------- |
| Backend       | Java 21, Spring Boot 4.1.1 |
| Build Tool    | Maven 3.9+                 |
| Frontend      | React 18, Vite 5           |
| Database      | PostgreSQL                 |
| Messaging     | RabbitMQ                   |
| API Testing   | Postman                    |
| Architecture  | Microservices              |
| Communication | REST APIs and RabbitMQ     |

---

## Architecture

The backend consists of nine independently runnable Spring Boot services.

| Service                | Responsibility                                    | Port |
| ---------------------- | ------------------------------------------------- | ---: |
| Member Service         | Manages member records                            | 8081 |
| Catalog Service        | Manages books and available copies                | 8082 |
| Borrowing Service      | Manages borrowings and returns                    | 8083 |
| Fine Service           | Manages fines and payments                        | 8084 |
| Notification Service   | Generates notifications from library events       | 8085 |
| Authentication Service | Provides login and token validation               | 8086 |
| API Gateway Service    | Provides gateway information and health endpoints | 8087 |
| Audit Service          | Manages audit events                              | 8088 |
| Recommendation Service | Provides book and member recommendations          | 8089 |

### Service Communication

The application uses both synchronous and asynchronous communication.

* **Synchronous communication:** Borrowing Service communicates with Member Service and Catalog Service through REST APIs before creating a borrowing.
* **Asynchronous communication:** Library events are published through RabbitMQ and consumed by the relevant services.
* **Database:** Member, Catalog, Borrowing, Fine, and Notification Services use the local `library_db` PostgreSQL database.
* **Frontend:** The React application currently communicates directly with the Member, Catalog, Borrowing, Fine, and Notification Services.

### Infrastructure Ports

| Component              |  Port | Purpose                 |
| ---------------------- | ----: | ----------------------- |
| PostgreSQL             |  5432 | Application database    |
| RabbitMQ               |  5672 | AMQP messaging          |
| RabbitMQ Management UI | 15672 | RabbitMQ administration |
| React/Vite             |  5500 | Frontend application    |

---

## Event-Driven Architecture

RabbitMQ is used for asynchronous communication between services.

The primary topic exchange is:

```text
library.events
```

### Events

| Event            | Published By      | Consumed By                                         |
| ---------------- | ----------------- | --------------------------------------------------- |
| `book.borrowed`  | Borrowing Service | Catalog Service, Notification Service               |
| `book.returned`  | Borrowing Service | Catalog Service, Fine Service, Notification Service |
| `book.overdue`   | Borrowing Service | Fine Service                                        |
| `fine.generated` | Fine Service      | Notification Service                                |
| `fine.paid`      | Fine Service      | Notification Service                                |
| `book.available` | Catalog Service   | Notification Service                                |

### Event Flow

```text
React Frontend
      |
      v
Borrowing Service
      |
      +--------------------+
      |                    |
      v                    v
Member Service       Catalog Service
      |                    |
      +----------+---------+
                 |
                 v
            RabbitMQ
        library.events
                 |
       +---------+---------+
       |         |         |
       v         v         v
    Catalog   Notification  Fine
    Service     Service    Service
```

Notification Service implements bounded retries and dead-letter queues for its event consumers. Consumers use event IDs to prevent duplicate processing of the same event.

The project does not currently implement a transactional outbox pattern. Therefore, the messaging architecture should not be considered a production-grade exactly-once event-processing solution.

---

## Prerequisites

Install the following software before running the application:

* JDK 21
* Maven 3.9 or later
* Node.js and npm
* PostgreSQL
* RabbitMQ
* Erlang/OTP compatible with the installed RabbitMQ version
* Postman (optional)

---

## Configuration

The default local configuration expects PostgreSQL to run with the following settings:

```text
Host: localhost
Port: 5432
Database: library_db
User: postgres
```

RabbitMQ is expected to run at:

```text
Host: localhost
AMQP Port: 5672
Management UI: 15672
User: guest
```

### Create the Database

Using `createdb`:

```bash
createdb -U postgres library_db
```

Alternatively, connect to PostgreSQL as an administrator and execute:

```sql
CREATE DATABASE library_db;
```

An optional SQL schema is available at:

```text
database/library_db.sql
```

### Database Password

Catalog, Borrowing, Fine, and Notification Services read the PostgreSQL password from the following environment variable:

```text
SPRING_DATASOURCE_PASSWORD
```

For PowerShell:

```powershell
$env:SPRING_DATASOURCE_PASSWORD = Read-Host "PostgreSQL password"
```

Set this variable separately in each service terminal.

Member Service should also use an environment-variable placeholder in its `application.properties`:

```properties
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

Do not commit real credentials to the repository.

---

# Running the Application

## 1. Start PostgreSQL and RabbitMQ

Ensure that PostgreSQL and RabbitMQ are running before starting the backend services.

Required ports:

```text
PostgreSQL: 5432
RabbitMQ:   5672
```

The RabbitMQ management interface is available at:

```text
http://localhost:15672
```

when the management plugin is enabled.

---

## 2. Start the Backend Services

Open a separate terminal for each service.

Replace:

```text
C:\path\to\smart-library-management
```

with the path where the repository was cloned.

### Member Service

```powershell
cd C:\path\to\smart-library-management
$env:SPRING_DATASOURCE_PASSWORD = Read-Host "PostgreSQL password"
mvn -pl member-service spring-boot:run
```

### Catalog Service

```powershell
cd C:\path\to\smart-library-management
$env:SPRING_DATASOURCE_PASSWORD = Read-Host "PostgreSQL password"
mvn -pl catalog-service spring-boot:run
```

### Borrowing Service

Member Service and Catalog Service should be started before Borrowing Service because Borrowing Service communicates with both services synchronously.

```powershell
cd C:\path\to\smart-library-management
$env:SPRING_DATASOURCE_PASSWORD = Read-Host "PostgreSQL password"
mvn -pl borrowing-service spring-boot:run
```

### Fine Service

```powershell
cd C:\path\to\smart-library-management
$env:SPRING_DATASOURCE_PASSWORD = Read-Host "PostgreSQL password"
mvn -pl fine-service spring-boot:run
```

### Notification Service

```powershell
cd C:\path\to\smart-library-management
$env:SPRING_DATASOURCE_PASSWORD = Read-Host "PostgreSQL password"
mvn -pl notification-service spring-boot:run
```

### Authentication Service

```powershell
cd C:\path\to\smart-library-management
mvn -pl authentication-service spring-boot:run
```

### API Gateway Service

```powershell
cd C:\path\to\smart-library-management
mvn -pl api-gateway-service spring-boot:run
```

### Audit Service

```powershell
cd C:\path\to\smart-library-management
mvn -pl audit-service spring-boot:run
```

### Recommendation Service

```powershell
cd C:\path\to\smart-library-management
mvn -pl recommendation-service spring-boot:run
```

Keep the service terminals running while using the application.

---

## 3. Start the React Frontend

Open another terminal:

```powershell
cd C:\path\to\smart-library-management\frontend
npm ci
npm run dev
```

The frontend is available at:

```text
http://localhost:5500
```

Stop a running service using:

```text
Ctrl + C
```

---

# Build and Test

## Build All Maven Modules

From the repository root:

```bash
mvn clean package
```

## Run Tests for a Specific Service

For example:

```bash
mvn -pl member-service test
```

Replace `member-service` with the required service.

## Build the Frontend

```bash
cd frontend
npm ci
npm run build
```

## Preview the Production Build

```bash
npm run preview
```

## Start the Frontend Development Server

```bash
npm run dev
```

---

# API Endpoints

| Service        | Method | Endpoint                             |
| -------------- | ------ | ------------------------------------ |
| Member         | GET    | `/members`                           |
| Member         | POST   | `/members`                           |
| Member         | GET    | `/members/{id}`                      |
| Catalog        | GET    | `/books`                             |
| Catalog        | POST   | `/books`                             |
| Catalog        | GET    | `/books/{id}`                        |
| Borrowing      | GET    | `/borrowings`                        |
| Borrowing      | POST   | `/borrowings`                        |
| Borrowing      | GET    | `/borrowings/{id}`                   |
| Borrowing      | PUT    | `/borrowings/{id}/return`            |
| Fine           | GET    | `/fines`                             |
| Fine           | GET    | `/members/{id}/fines`                |
| Fine           | PUT    | `/fines/{id}/pay`                    |
| Notification   | GET    | `/notifications`                     |
| Notification   | GET    | `/notifications/member/{memberId}`   |
| Authentication | POST   | `/auth/login`                        |
| Authentication | GET    | `/auth/validate`                     |
| API Gateway    | GET    | `/gateway/health`                    |
| API Gateway    | GET    | `/gateway/services`                  |
| API Gateway    | GET    | `/gateway/route/{serviceName}`       |
| Audit          | GET    | `/audit/events`                      |
| Audit          | POST   | `/audit/events`                      |
| Recommendation | GET    | `/recommendations/popular`           |
| Recommendation | GET    | `/recommendations/member/{memberId}` |

### Example Request

Member Service:

```text
GET http://localhost:8081/members
```

---

# Postman Collection

The project includes a Postman collection:

```text
postman/Smart-Library.postman_collection.json
```

Import the collection into Postman and start the required services before executing requests.

The collection can be used to test the application's REST APIs, including:

* Member management
* Book management
* Borrowing and returns
* Fine management
* Notifications
* Authentication
* Audit events
* Recommendations

---

# Project Structure

```text
smart-library-management/
│
├── api-gateway-service/
├── audit-service/
├── authentication-service/
├── borrowing-service/
├── catalog-service/
├── fine-service/
├── member-service/
├── notification-service/
├── recommendation-service/
│
├── frontend/
│   └── React + Vite application
│
├── database/
│   └── library_db.sql
│
├── postman/
│   └── Smart-Library.postman_collection.json
│
├── pom.xml
└── README.md
```

---

# Troubleshooting

### Database authentication failed

Verify that:

* PostgreSQL is running.
* The `library_db` database exists.
* The PostgreSQL username is correct.
* `SPRING_DATASOURCE_PASSWORD` is set correctly.
* The environment variable is set in the same terminal used to start the service.

### PostgreSQL connection refused

Ensure that PostgreSQL is running and listening on:

```text
localhost:5432
```

### RabbitMQ connection refused

Ensure that RabbitMQ is running and listening on:

```text
localhost:5672
```

The management UI at port `15672` is separate from the AMQP listener.

### Port already in use

Stop the process using the required port or change the service's port configuration:

```properties
server.port=<new-port>
```

### Borrowing Service cannot reach Member or Catalog

Start the following services first:

```text
Member Service   → 8081
Catalog Service  → 8082
Borrowing Service → 8083
```

### Catalog Service has compilation or stale-class issues

From the repository root:

```bash
mvn -pl catalog-service clean spring-boot:run
```

### Frontend cannot reach a backend service

Verify that the corresponding backend service is running on the expected port. Check the browser developer console for the failing request.

### Maven cannot resolve dependencies

Check the network connection and run the Maven command again. The initial build may take longer because Maven needs to download project dependencies.

---

# Security and Production Considerations

This project is configured for local development and demonstration purposes.

Before deploying the application to a production environment:

* Do not commit database passwords or other credentials.
* Rotate credentials that have been exposed or committed.
* Use a dedicated secrets-management solution.
* Implement authentication and authorization for protected APIs.
* Validate and sanitize user input.
* Configure appropriate CORS policies.
* Enable HTTPS/TLS.
* Review database permissions.
* Configure RabbitMQ authentication and permissions.
* Avoid logging sensitive member information.
* Review service-to-service authentication.
* Implement appropriate audit and access controls.
* Back up the database before destructive operations or schema changes.
* Consider implementing a transactional outbox pattern for reliable event publishing.

> Although Authentication Service and API Gateway Service are included in the project, the current React application communicates directly with several backend services. Therefore, the presence of these services does not imply that all backend APIs are currently protected.

---

# About

Smart Library Management System is a microservices-based library circulation application designed to demonstrate distributed application development using Spring Boot, PostgreSQL, RabbitMQ, and React.

The project demonstrates:

* Microservice architecture
* REST-based service communication
* Event-driven architecture
* Asynchronous messaging with RabbitMQ
* PostgreSQL persistence
* React frontend development
* Service-to-service communication
* Fine and notification workflows
* Authentication and audit services
* Recommendation APIs
* API testing with Postman

## License

This project is intended for educational and demonstration purposes.
