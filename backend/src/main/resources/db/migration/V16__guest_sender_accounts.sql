ALTER TABLE users ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
ALTER TABLE delivery_requests ADD COLUMN guest_account_id uuid REFERENCES users(id);
CREATE INDEX idx_delivery_requests_guest_account ON delivery_requests(guest_account_id);
