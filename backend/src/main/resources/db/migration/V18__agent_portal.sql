-- Agent portal. Agent identity and commission rate stay in agent_profiles.
CREATE TABLE agent_customers (
    id uuid PRIMARY KEY,
    agent_id uuid NOT NULL REFERENCES users(id),
    customer_id uuid NOT NULL UNIQUE REFERENCES users(id),
    customer_type varchar(16) NOT NULL CHECK (customer_type IN ('SENDER', 'TRAVELER')),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_agent_customers_agent ON agent_customers(agent_id, created_at);

CREATE TABLE agent_authorizations (
    id uuid PRIMARY KEY,
    agent_id uuid NOT NULL REFERENCES users(id),
    customer_id uuid NOT NULL REFERENCES users(id),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    method varchar(16) NOT NULL CHECK (method IN ('IN_PERSON', 'PHONE', 'WRITTEN', 'DIGITAL')),
    consent_note varchar(500),
    granted_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT ck_agent_authorization_window CHECK (expires_at > granted_at)
);
CREATE INDEX idx_agent_authorizations_lookup ON agent_authorizations(agent_id, customer_id, status);

CREATE TABLE agent_commissions (
    id uuid PRIMARY KEY,
    agent_id uuid NOT NULL REFERENCES users(id),
    customer_id uuid REFERENCES users(id),
    request_id uuid NOT NULL UNIQUE REFERENCES delivery_requests(id),
    rate numeric(5,4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    base_amount numeric(10,2) NOT NULL CHECK (base_amount >= 0),
    amount numeric(10,2) NOT NULL CHECK (amount >= 0),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    created_at timestamp with time zone NOT NULL,
    paid_at timestamp with time zone
);
CREATE INDEX idx_agent_commissions_agent ON agent_commissions(agent_id, created_at);

CREATE TABLE agent_audit_log (
    id uuid PRIMARY KEY,
    agent_id uuid NOT NULL REFERENCES users(id),
    customer_id uuid REFERENCES users(id),
    authorization_id uuid REFERENCES agent_authorizations(id),
    action varchar(48) NOT NULL,
    target_type varchar(32),
    target_id uuid,
    details varchar(500),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_agent_audit_log_agent ON agent_audit_log(agent_id, created_at);

ALTER TABLE delivery_requests ADD COLUMN created_by_agent_id uuid REFERENCES users(id);
ALTER TABLE market_trips ADD COLUMN created_by_agent_id uuid REFERENCES users(id);
CREATE INDEX idx_delivery_requests_created_by_agent ON delivery_requests(created_by_agent_id);
CREATE INDEX idx_market_trips_created_by_agent ON market_trips(created_by_agent_id);
