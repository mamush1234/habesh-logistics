ALTER TABLE shipment_requests ADD COLUMN proposed_quote_json text;
ALTER TABLE shipment_requests ADD COLUMN accepted_offer_id uuid REFERENCES delivery_offers(id);
ALTER TABLE shipment_requests ADD COLUMN decided_at timestamp with time zone;
CREATE INDEX ix_shipment_request_accepted_offer ON shipment_requests(accepted_offer_id);
