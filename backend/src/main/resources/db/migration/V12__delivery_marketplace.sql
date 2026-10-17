ALTER TABLE traveler_profiles ADD COLUMN marketplace_verification varchar(32) NOT NULL DEFAULT 'UNVERIFIED';

CREATE TABLE market_trips (
    id uuid PRIMARY KEY,
    traveler_id uuid NOT NULL REFERENCES users(id),
    origin varchar(100) NOT NULL,
    destination varchar(100) NOT NULL,
    departure_date date NOT NULL,
    expected_arrival_date date,
    capacity_kg numeric(10,2) NOT NULL CHECK (capacity_kg > 0),
    reserved_kg numeric(10,2) NOT NULL DEFAULT 0 CHECK (reserved_kg >= 0 AND reserved_kg <= capacity_kg),
    created_at timestamp with time zone NOT NULL
);
CREATE TABLE market_trip_allowed (
    trip_id uuid NOT NULL REFERENCES market_trips(id),
    item_type varchar(32) NOT NULL,
    PRIMARY KEY (trip_id, item_type)
);
CREATE TABLE market_trip_blocked (
    trip_id uuid NOT NULL REFERENCES market_trips(id),
    item_type varchar(32) NOT NULL,
    PRIMARY KEY (trip_id, item_type)
);
CREATE TABLE delivery_requests (
    id uuid PRIMARY KEY,
    sender_id uuid NOT NULL REFERENCES users(id),
    pickup_city varchar(100) NOT NULL,
    destination_city varchar(100) NOT NULL,
    available_from date NOT NULL,
    deadline date NOT NULL,
    item_type varchar(32) NOT NULL,
    description varchar(1000) NOT NULL,
    weight_kg numeric(10,2) NOT NULL CHECK (weight_kg > 0),
    offered_price numeric(10,2) NOT NULL CHECK (offered_price > 0),
    status varchar(32) NOT NULL,
    accepted_offer_id uuid UNIQUE,
    escrow_status varchar(32) NOT NULL,
    escrow_amount numeric(10,2),
    rating integer CHECK (rating BETWEEN 1 AND 5),
    dispute_reason varchar(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE delivery_offers (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES delivery_requests(id),
    trip_id uuid NOT NULL REFERENCES market_trips(id),
    traveler_id uuid NOT NULL REFERENCES users(id),
    price numeric(10,2) NOT NULL CHECK (price > 0),
    message varchar(500),
    status varchar(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_delivery_offer_traveler UNIQUE (request_id, traveler_id)
);
ALTER TABLE delivery_requests ADD CONSTRAINT fk_accepted_offer FOREIGN KEY (accepted_offer_id) REFERENCES delivery_offers(id);
CREATE TABLE delivery_events (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES delivery_requests(id),
    status varchar(32) NOT NULL,
    actor_id uuid NOT NULL REFERENCES users(id),
    description varchar(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    sequence_number bigint NOT NULL,
    UNIQUE (request_id, sequence_number)
);
CREATE INDEX idx_delivery_requests_sender ON delivery_requests(sender_id, created_at);
CREATE INDEX idx_delivery_requests_status ON delivery_requests(status);
CREATE INDEX idx_market_trips_route ON market_trips(origin, destination, departure_date);
CREATE INDEX idx_delivery_events_request ON delivery_events(request_id, created_at);
ALTER TABLE package_notifications ALTER COLUMN shipment_id DROP NOT NULL;
ALTER TABLE package_notifications ADD COLUMN request_id uuid REFERENCES delivery_requests(id) ON DELETE CASCADE;
