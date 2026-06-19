-- Initial schema for the transfer service.
-- Matches the JPA entities; the Postgres profile runs this via Flyway and Hibernate validates against it.

CREATE TABLE transfers (
    id              uuid          NOT NULL,
    idempotency_key varchar(255)  NOT NULL,
    from_account_id uuid          NOT NULL,
    to_account_id   uuid          NOT NULL,
    amount          numeric(19, 4) NOT NULL,
    currency        varchar(3)    NOT NULL,
    status          varchar(16)   NOT NULL,
    failure_reason  varchar(500),
    created_at      timestamp(6) with time zone NOT NULL,
    updated_at      timestamp(6) with time zone NOT NULL,
    CONSTRAINT transfers_pkey PRIMARY KEY (id),
    CONSTRAINT ux_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT transfers_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'BLOCKED', 'EXPIRED'))
);

CREATE TABLE outbox_events (
    id           uuid          NOT NULL,
    aggregate_id uuid          NOT NULL,
    type         varchar(64)   NOT NULL,
    payload      varchar(4000) NOT NULL,
    status       varchar(16)   NOT NULL,
    created_at   timestamp(6) with time zone NOT NULL,
    published_at timestamp(6) with time zone,
    CONSTRAINT outbox_events_pkey PRIMARY KEY (id),
    CONSTRAINT outbox_events_status_check
        CHECK (status IN ('PENDING', 'PUBLISHED'))
);

-- Drives the outbox relay's "oldest pending first" scan.
CREATE INDEX ix_outbox_status_created ON outbox_events (status, created_at);
