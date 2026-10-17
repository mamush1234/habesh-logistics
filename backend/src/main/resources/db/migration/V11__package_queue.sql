ALTER TABLE shipments ADD COLUMN queue_status varchar(32);
ALTER TABLE shipments ADD COLUMN queue_claimant_id uuid REFERENCES users(id);
ALTER TABLE shipments ADD COLUMN queue_expires_at timestamp with time zone;
ALTER TABLE shipments ADD COLUMN service_fee numeric(10,2);
ALTER TABLE shipments ADD COLUMN service_payment_proof text;
ALTER TABLE shipments ADD COLUMN service_payment_uploaded_at timestamp with time zone;
CREATE INDEX idx_shipments_queue ON shipments(queue_status, queue_expires_at);

CREATE TABLE package_notifications (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES users(id),
    shipment_id uuid NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    message varchar(500) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    read_at timestamp with time zone
);
CREATE INDEX idx_package_notifications_recipient ON package_notifications(recipient_id, created_at);
