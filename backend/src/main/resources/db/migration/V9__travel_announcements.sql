CREATE TABLE travel_announcements (
    id uuid PRIMARY KEY,
    posted_by_id uuid NOT NULL REFERENCES users(id),
    origin varchar(100) NOT NULL,
    destination varchar(100) NOT NULL,
    departure_date date NOT NULL,
    expected_arrival_date date,
    notes varchar(1000),
    created_at timestamptz NOT NULL,
    CHECK (expected_arrival_date IS NULL OR expected_arrival_date >= departure_date)
);

CREATE INDEX idx_travel_announcements_departure ON travel_announcements(departure_date, created_at);
