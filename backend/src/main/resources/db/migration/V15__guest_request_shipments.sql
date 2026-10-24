ALTER TABLE shipments ALTER COLUMN sender_id DROP NOT NULL;
ALTER TABLE shipments ADD COLUMN source_request_id uuid UNIQUE REFERENCES delivery_requests(id);
ALTER TABLE shipments ADD CONSTRAINT ck_shipment_sender CHECK (sender_id IS NOT NULL OR source_request_id IS NOT NULL);
