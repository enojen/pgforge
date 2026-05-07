CREATE TABLE accounts (
    id          BIGSERIAL       PRIMARY KEY,
    name        TEXT            NOT NULL UNIQUE,
    balance     NUMERIC(20, 2)  NOT NULL DEFAULT 0,
    version     BIGINT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE entries (
    id              BIGSERIAL       PRIMARY KEY,
    from_account_id BIGINT          NOT NULL REFERENCES accounts (id),
    to_account_id   BIGINT          NOT NULL REFERENCES accounts (id),
    amount          NUMERIC(20, 2)  NOT NULL CHECK (amount > 0),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
