ALTER TABLE delivery_requests ADD COLUMN receiver_id uuid REFERENCES recipients(id);

CREATE TABLE package_items (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES delivery_requests(id) ON DELETE CASCADE,
    item_position integer,
    category varchar(32) NOT NULL,
    weight_kg numeric(10,2) NOT NULL CHECK (weight_kg >= 0.1),
    description varchar(1000) NOT NULL
);
CREATE INDEX idx_package_items_request ON package_items(request_id);

INSERT INTO package_items (id, request_id, item_position, category, weight_kg, description)
SELECT id, id, 0, item_type, weight_kg, description FROM delivery_requests;
