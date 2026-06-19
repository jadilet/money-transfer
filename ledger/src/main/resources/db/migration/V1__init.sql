-- Initial schema for the ledger service (double-entry).
-- Matches the JPA entities; the Postgres profile runs this via Flyway and Hibernate validates against it.

CREATE TABLE ledger_accounts (
    id          uuid        NOT NULL,
    account_ref uuid        NOT NULL,
    currency    varchar(3)  NOT NULL,
    created_at  timestamp(6) with time zone NOT NULL,
    CONSTRAINT ledger_accounts_pkey PRIMARY KEY (id),
    CONSTRAINT ux_ledger_accounts_account_ref UNIQUE (account_ref)
);

CREATE TABLE journal_entries (
    id          uuid         NOT NULL,
    transfer_id uuid         NOT NULL,
    description varchar(200) NOT NULL,
    created_at  timestamp(6) with time zone NOT NULL,
    CONSTRAINT journal_entries_pkey PRIMARY KEY (id),
    CONSTRAINT ux_journal_entries_transfer_id UNIQUE (transfer_id)
);

CREATE TABLE postings (
    id                uuid           NOT NULL,
    journal_entry_id  uuid           NOT NULL,
    ledger_account_id uuid           NOT NULL,
    direction         varchar(8)     NOT NULL,
    amount            numeric(19, 4) NOT NULL,
    currency          varchar(3)     NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL,
    CONSTRAINT postings_pkey PRIMARY KEY (id),
    CONSTRAINT postings_direction_check CHECK (direction IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX ix_postings_journal_entry ON postings (journal_entry_id);
CREATE INDEX ix_postings_ledger_account ON postings (ledger_account_id);
