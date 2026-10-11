ALTER TABLE luggage ALTER COLUMN trip_id DROP NOT NULL;

ALTER TABLE shipments ADD COLUMN trip_id uuid REFERENCES trips(id);
CREATE INDEX idx_shipments_trip ON shipments(trip_id);
ALTER TABLE shipments ADD CONSTRAINT ck_shipments_transport
    CHECK (luggage_id IS NULL OR trip_id IS NULL);
