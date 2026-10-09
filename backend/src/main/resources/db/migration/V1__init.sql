-- V1 initial schema for Habesh Logistics
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id uuid PRIMARY KEY,
    first_name varchar(255) NOT NULL,
    last_name varchar(255) NOT NULL,
    email varchar(255) NOT NULL UNIQUE,
    phone varchar(50),
    password_hash varchar(255) NOT NULL,
    role varchar(32) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz,
    updated_at timestamptz
);

CREATE TABLE sender_profiles (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    preferred_contact_method varchar(64),
    address varchar(500)
);

CREATE TABLE traveler_profiles (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    verification_status varchar(64),
    notes varchar(1000)
);

CREATE TABLE agent_profiles (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    country varchar(128),
    city varchar(128),
    referral_code varchar(32) UNIQUE,
    commission_rate numeric(5,4),
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE recipients (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    phone varchar(64) NOT NULL,
    city varchar(128) NOT NULL,
    address varchar(500),
    notes varchar(1000)
);

CREATE TABLE trips (
    id uuid PRIMARY KEY,
    trip_number varchar(32) NOT NULL UNIQUE,
    traveler_id uuid NOT NULL REFERENCES users(id),
    departure_airport varchar(8) NOT NULL,
    arrival_airport varchar(8) NOT NULL,
    departure_date date NOT NULL,
    expected_arrival_date date,
    total_luggage_capacity numeric(10,2),
    number_of_luggage integer,
    status varchar(32) NOT NULL,
    notes varchar(1000),
    created_at timestamptz,
    updated_at timestamptz
);

CREATE TABLE luggage (
    id uuid PRIMARY KEY,
    luggage_number varchar(32) NOT NULL UNIQUE,
    trip_id uuid NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    luggage_type varchar(64),
    capacity_kg numeric(10,2) NOT NULL,
    actual_weight_kg numeric(10,2),
    status varchar(32) NOT NULL,
    qr_code varchar(255),
    seal_number varchar(64),
    notes varchar(1000),
    created_at timestamptz,
    updated_at timestamptz
);

CREATE TABLE shipments (
    id uuid PRIMARY KEY,
    tracking_number varchar(32) NOT NULL UNIQUE,
    tracking_token varchar(64) NOT NULL UNIQUE,
    sender_id uuid NOT NULL REFERENCES users(id),
    recipient_id uuid NOT NULL REFERENCES recipients(id),
    destination_country varchar(128) NOT NULL,
    destination_city varchar(128) NOT NULL,
    description varchar(1000),
    declared_weight_kg numeric(10,2),
    actual_weight_kg numeric(10,2),
    rate_per_kg numeric(10,2),
    shipping_fee numeric(10,2),
    pickup_fee numeric(10,2),
    total_amount numeric(10,2),
    payment_status varchar(32) NOT NULL,
    pickup_method varchar(32) NOT NULL,
    pickup_address varchar(500),
    pickup_requested_date date,
    status varchar(32) NOT NULL,
    referring_agent_id uuid REFERENCES users(id),
    luggage_id uuid REFERENCES luggage(id),
    created_at timestamptz,
    updated_at timestamptz
);
CREATE INDEX idx_shipments_sender ON shipments(sender_id);
CREATE INDEX idx_shipments_luggage ON shipments(luggage_id);

CREATE TABLE tracking_events (
    id uuid PRIMARY KEY,
    shipment_id uuid REFERENCES shipments(id) ON DELETE CASCADE,
    luggage_id uuid REFERENCES luggage(id) ON DELETE SET NULL,
    trip_id uuid REFERENCES trips(id) ON DELETE SET NULL,
    event_type varchar(64) NOT NULL,
    status varchar(32),
    description varchar(500),
    location varchar(255),
    performed_by_user_id uuid REFERENCES users(id),
    created_at timestamptz
);
CREATE INDEX idx_events_shipment ON tracking_events(shipment_id);

CREATE TABLE pickups (
    id uuid PRIMARY KEY,
    shipment_id uuid NOT NULL UNIQUE REFERENCES shipments(id) ON DELETE CASCADE,
    address varchar(500) NOT NULL,
    date date,
    time_window varchar(64),
    assigned_driver_id uuid REFERENCES users(id),
    status varchar(32) NOT NULL,
    notes varchar(1000),
    completed_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz
);

CREATE TABLE commissions (
    id uuid PRIMARY KEY,
    agent_id uuid NOT NULL REFERENCES users(id),
    shipment_id uuid NOT NULL UNIQUE REFERENCES shipments(id) ON DELETE CASCADE,
    amount numeric(10,2) NOT NULL,
    rate numeric(5,4) NOT NULL,
    status varchar(32) NOT NULL,
    created_at timestamptz
);
CREATE INDEX idx_commission_agent ON commissions(agent_id);

CREATE TABLE scan_audit_log (
    id uuid PRIMARY KEY,
    entity_type varchar(32) NOT NULL,
    entity_identifier varchar(64) NOT NULL,
    action varchar(500),
    performed_by_user_id uuid REFERENCES users(id),
    created_at timestamptz
);
