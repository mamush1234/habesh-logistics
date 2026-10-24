ALTER TABLE delivery_requests ALTER COLUMN sender_id DROP NOT NULL;
ALTER TABLE delivery_requests ADD COLUMN guest_name varchar(120);
ALTER TABLE delivery_requests ADD COLUMN guest_email varchar(254);
ALTER TABLE delivery_requests ADD COLUMN guest_phone varchar(40);
ALTER TABLE delivery_requests ADD COLUMN product_photo text;
ALTER TABLE delivery_requests ADD CONSTRAINT ck_delivery_request_sender CHECK (
    sender_id IS NOT NULL OR
    (guest_name IS NOT NULL AND guest_email IS NOT NULL AND guest_phone IS NOT NULL AND product_photo IS NOT NULL)
);
CREATE TABLE guest_request_links (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE,
    label varchar(120) NOT NULL,
    created_by_id uuid NOT NULL REFERENCES users(id),
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    request_id uuid UNIQUE REFERENCES delivery_requests(id),
    CHECK (expires_at > created_at)
);
