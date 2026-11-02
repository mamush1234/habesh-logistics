ALTER TABLE delivery_requests ADD COLUMN delivery_code_required boolean NOT NULL DEFAULT false;
ALTER TABLE delivery_requests ALTER COLUMN delivery_code_required SET DEFAULT true;

CREATE TABLE delivery_confirmations (
    package_id uuid PRIMARY KEY REFERENCES delivery_requests(id),
    code_hash varchar(255) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    failed_attempts integer NOT NULL CHECK (failed_attempts BETWEEN 0 AND 5),
    issue_window_at timestamp with time zone NOT NULL,
    issue_count integer NOT NULL CHECK (issue_count BETWEEN 1 AND 5),
    confirmed_at timestamp with time zone,
    confirmed_by uuid REFERENCES users(id),
    CHECK (expires_at > issued_at),
    CHECK ((confirmed_at IS NULL AND confirmed_by IS NULL) OR (confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL))
);
