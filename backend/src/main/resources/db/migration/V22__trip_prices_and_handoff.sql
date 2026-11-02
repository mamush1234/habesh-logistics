ALTER TABLE market_trips ADD COLUMN handoff_location varchar(500);
ALTER TABLE market_trips ADD COLUMN notes varchar(1000);

CREATE TABLE trip_item_prices (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES market_trips(id) ON DELETE CASCADE,
    category varchar(32) NOT NULL,
    price numeric(10,2) NOT NULL CHECK (price >= 0.01),
    unit varchar(16) NOT NULL CHECK (unit IN ('PER_KG', 'PER_ITEM')),
    CONSTRAINT uq_trip_item_price UNIQUE (trip_id, category)
);
