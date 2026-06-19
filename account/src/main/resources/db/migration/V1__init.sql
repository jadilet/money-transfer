-- Initial schema for the account service.
-- Matches the JPA entities; the Postgres profile runs this via Flyway and Hibernate validates against it.

CREATE TABLE clients (
    id         uuid          NOT NULL,
    name       varchar(255)  NOT NULL,
    email      varchar(255),
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT clients_pkey PRIMARY KEY (id)
);

CREATE TABLE accounts (
    id         uuid           NOT NULL,
    client_id  uuid           NOT NULL,
    currency   varchar(3)     NOT NULL,
    balance    numeric(19, 4) NOT NULL,
    status     varchar(16)    NOT NULL,
    version    bigint         NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT accounts_pkey PRIMARY KEY (id),
    CONSTRAINT accounts_status_check CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX ix_accounts_client_id ON accounts (client_id);

CREATE TABLE processed_transfers (
    transfer_id uuid           NOT NULL,
    result      varchar(16)    NOT NULL,
    amount      numeric(19, 4) NOT NULL,
    applied_at  timestamp(6) with time zone NOT NULL,
    CONSTRAINT processed_transfers_pkey PRIMARY KEY (transfer_id),
    CONSTRAINT processed_transfers_result_check CHECK (result IN ('APPLIED', 'REJECTED'))
);
