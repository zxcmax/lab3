CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS access_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tokens_expiry ON access_tokens(expires_at);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES accounts(id),
    name VARCHAR(120) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('MEDIA', 'EDUCATION', 'SOFTWARE', 'OTHER')),
    amount DECIMAL(11,2) NOT NULL CHECK (amount > 0),
    billing_period VARCHAR(10) NOT NULL CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    next_charge_date DATE NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (id, owner_id)
);

CREATE INDEX IF NOT EXISTS idx_sub_owner ON subscriptions(owner_id, deleted);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    subscription_name VARCHAR(120) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('MEDIA', 'EDUCATION', 'SOFTWARE', 'OTHER')),
    amount DECIMAL(11,2) NOT NULL CHECK (amount > 0),
    paid_on DATE NOT NULL,
    FOREIGN KEY (subscription_id, owner_id) REFERENCES subscriptions(id, owner_id)
);

CREATE INDEX IF NOT EXISTS idx_payments_owner_date ON payments(owner_id, paid_on);
