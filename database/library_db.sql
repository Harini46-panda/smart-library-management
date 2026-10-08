-- =====================================================================
-- Smart Library Management System - library_db
-- =====================================================================
--
-- IMPORTANT - read this before running anything below.
--
-- Every service in this project runs with:
--   spring.jpa.hibernate.ddl-auto=update
--
-- That means each service creates/updates its OWN tables automatically
-- the first time it starts, based on its JPA entities. You do NOT need
-- to run the CREATE TABLE statements below for the system to work -
-- just create the empty database (Step 1) and start the services.
--
-- The CREATE TABLE statements in this file exist so that:
--   (a) you have a single reference for the full schema without having
--       to read five services' worth of entity classes, and
--   (b) if you want the tables to exist before any service has started
--       (e.g. to poke around in pgAdmin first), you can run this file.
--
-- Every statement uses IF NOT EXISTS / ON CONFLICT DO NOTHING so it is
-- always safe to (re)run, whether or not the services have already
-- created these tables themselves - the column definitions here match
-- exactly what Hibernate generates from the entities, so there is no
-- conflicting second definition of any table.
--
-- Ownership: each table is created/modified only by the service listed
-- next to it. No other service should ever be given write access to a
-- table it doesn't own.
--
--   members                       -> member-service        (pre-existing, untouched)
--   books                         -> catalog-service
--   borrowings                    -> borrowing-service
--   fines                         -> fine-service
--   notifications                 -> notification-service
--   catalog_processed_events      -> catalog-service        (idempotency ledger)
--   fine_processed_events         -> fine-service            (idempotency ledger)
--   notification_processed_events -> notification-service   (idempotency ledger)
--
-- =====================================================================
-- Step 1: create the database (run this once, connected to the default
-- "postgres" database - see README.md for the exact PowerShell/psql
-- commands). This statement is NOT inside this file's normal run
-- because CREATE DATABASE cannot run inside a script that is itself
-- connected to a different database or inside a transaction block.
--
--   CREATE DATABASE library_db;
--
-- Everything below this point should be run AFTER connecting to
-- library_db itself (e.g. \c library_db in psql).
-- =====================================================================


-- ---------------------------------------------------------------------
-- members (owned by member-service - already implemented and tested;
-- included here ONLY as a reference so this file documents the full
-- schema in one place. Do not run this if member-service has already
-- created the table with data in it that matters - IF NOT EXISTS makes
-- this a no-op in that case anyway.)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS members (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    email            VARCHAR(150) NOT NULL UNIQUE,
    phone            VARCHAR(20),
    membership_date  DATE NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- books (owned by catalog-service)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS books (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    author            VARCHAR(150) NOT NULL,
    isbn              VARCHAR(20) NOT NULL UNIQUE,
    total_copies      INTEGER NOT NULL,
    available_copies  INTEGER NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_books_copies CHECK (available_copies >= 0 AND available_copies <= total_copies)
);

-- ---------------------------------------------------------------------
-- borrowings (owned by borrowing-service)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS borrowings (
    id                        BIGSERIAL PRIMARY KEY,
    member_id                 BIGINT NOT NULL,
    book_id                   BIGINT NOT NULL,
    borrow_date               DATE NOT NULL,
    due_date                  DATE NOT NULL,
    return_date               DATE,
    status                    VARCHAR(20) NOT NULL,
    overdue_event_published   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_borrowings_member_id ON borrowings (member_id);
CREATE INDEX IF NOT EXISTS idx_borrowings_book_id ON borrowings (book_id);
CREATE INDEX IF NOT EXISTS idx_borrowings_status_due_date ON borrowings (status, due_date);

-- ---------------------------------------------------------------------
-- fines (owned by fine-service)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fines (
    id             BIGSERIAL PRIMARY KEY,
    borrowing_id   BIGINT NOT NULL UNIQUE,
    member_id      BIGINT NOT NULL,
    amount         NUMERIC(10, 2) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    due_date       DATE NOT NULL,
    return_date    DATE,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fines_member_id ON fines (member_id);

-- ---------------------------------------------------------------------
-- notifications (owned by notification-service)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT,          -- nullable: NULL means a general/broadcast notification (e.g. BOOK_AVAILABLE)
    type          VARCHAR(30) NOT NULL,
    message       VARCHAR(500) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    read_status   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_notifications_member_id ON notifications (member_id);

-- ---------------------------------------------------------------------
-- Idempotency ledgers - one per consuming service. Each is a simple
-- "have I seen this event ID before" table used to make RabbitMQ's
-- at-least-once delivery safe to process (see each service's README
-- section on event handling for details).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog_processed_events (
    event_id      VARCHAR(36) PRIMARY KEY,
    event_type    VARCHAR(50) NOT NULL,
    processed_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fine_processed_events (
    event_id      VARCHAR(36) PRIMARY KEY,
    event_type    VARCHAR(50) NOT NULL,
    processed_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notification_processed_events (
    event_id      VARCHAR(36) PRIMARY KEY,
    event_type    VARCHAR(50) NOT NULL,
    processed_at  TIMESTAMP NOT NULL DEFAULT now()
);


-- =====================================================================
-- Sample data (optional). Safe to rerun - ON CONFLICT DO NOTHING skips
-- rows that already exist rather than erroring or duplicating them.
-- Feel free to delete this whole section if you'd rather start empty.
-- =====================================================================

INSERT INTO members (name, email, phone, membership_date, active) VALUES
    ('Ananya Raghavan', 'ananya.raghavan@example.com', '9840012345', '2024-01-15', TRUE),
    ('Karthik Subramaniam', 'karthik.s@example.com', '9840023456', '2024-03-22', TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO books (title, author, isbn, total_copies, available_copies) VALUES
    ('Clean Code', 'Robert C. Martin', '9780132350884', 3, 3),
    ('Designing Data-Intensive Applications', 'Martin Kleppmann', '9781449373320', 2, 2),
    ('Effective Java', 'Joshua Bloch', '9780134685991', 4, 4)
ON CONFLICT (isbn) DO NOTHING;
