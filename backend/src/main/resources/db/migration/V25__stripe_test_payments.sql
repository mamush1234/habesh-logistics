CREATE TABLE stripe_payments (
    id uuid PRIMARY KEY,
    package_id uuid NOT NULL UNIQUE REFERENCES delivery_requests(id),
    shipment_request_id uuid NOT NULL UNIQUE REFERENCES shipment_requests(id),
    intent_id varchar(255) UNIQUE,
    status varchar(32) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'CANCELLED', 'REFUNDED')),
    amount_cents bigint NOT NULL CHECK (amount_cents >= 50 AND amount_cents <= 99999999),
    quote_json text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
