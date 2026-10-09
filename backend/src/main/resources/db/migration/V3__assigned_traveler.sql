ALTER TABLE shipments ADD COLUMN assigned_traveler_id uuid REFERENCES users(id);
ALTER TABLE luggage ADD COLUMN assigned_traveler_id uuid REFERENCES users(id);
