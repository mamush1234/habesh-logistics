CREATE TABLE shipment_requests (
    id uuid PRIMARY KEY,
    package_id uuid NOT NULL REFERENCES delivery_requests(id),
    trip_id uuid NOT NULL REFERENCES market_trips(id),
    sender_id uuid NOT NULL REFERENCES users(id),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    pending_package_id uuid UNIQUE REFERENCES delivery_requests(id),
    quote_json text NOT NULL,
    message varchar(500),
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CHECK (expires_at > created_at),
    CHECK ((status = 'PENDING' AND pending_package_id IS NOT NULL AND pending_package_id = package_id)
        OR (status <> 'PENDING' AND pending_package_id IS NULL))
);
CREATE INDEX ix_shipment_request_package ON shipment_requests(package_id, created_at);
CREATE INDEX ix_shipment_request_trip ON shipment_requests(trip_id);
CREATE INDEX ix_shipment_request_expiry ON shipment_requests(status, expires_at);
