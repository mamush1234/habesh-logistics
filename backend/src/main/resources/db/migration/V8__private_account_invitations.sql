ALTER TABLE users ADD COLUMN credential_version integer NOT NULL DEFAULT 0;

CREATE TABLE invitations (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE,
    email varchar(255) NOT NULL,
    role varchar(32) NOT NULL CHECK (role IN ('SENDER', 'TRAVELER', 'AGENT')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz
);
