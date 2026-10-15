ALTER TABLE travel_announcements ADD COLUMN total_capacity_kg numeric(10,2);
ALTER TABLE travel_announcements ADD COLUMN number_of_luggage integer;
ALTER TABLE travel_announcements ADD CONSTRAINT chk_travel_capacity CHECK (total_capacity_kg >= 0.1);
ALTER TABLE travel_announcements ADD CONSTRAINT chk_travel_luggage CHECK (number_of_luggage >= 0);

ALTER TABLE shipments ADD COLUMN travel_announcement_id uuid REFERENCES travel_announcements(id);
ALTER TABLE shipments ADD COLUMN travel_booking_status varchar(32);
ALTER TABLE shipments ADD CONSTRAINT chk_travel_booking CHECK (
    (travel_announcement_id IS NULL AND travel_booking_status IS NULL)
    OR (travel_announcement_id IS NOT NULL AND travel_booking_status IS NOT NULL
        AND travel_booking_status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX idx_shipments_travel_booking ON shipments(travel_announcement_id, travel_booking_status);
