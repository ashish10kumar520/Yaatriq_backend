CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(320) UNIQUE,
    password_hash VARCHAR(100),
    provider VARCHAR(30),
    provider_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_provider_identity UNIQUE (provider, provider_id)
);

CREATE TABLE travel_plans (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prompt TEXT NOT NULL,
    destination VARCHAR(160),
    days INTEGER,
    budget NUMERIC(12, 2),
    currency VARCHAR(3),
    itinerary JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_travel_plans_days CHECK (days IS NULL OR days > 0),
    CONSTRAINT ck_travel_plans_budget CHECK (budget IS NULL OR budget >= 0)
);

CREATE INDEX idx_travel_plans_user_created ON travel_plans(user_id, created_at DESC);
