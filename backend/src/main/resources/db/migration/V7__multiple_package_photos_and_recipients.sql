CREATE TABLE shipment_additional_recipients (
    shipment_id uuid NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    recipient_order integer NOT NULL,
    recipient_id uuid NOT NULL REFERENCES recipients(id),
    PRIMARY KEY (shipment_id, recipient_order),
    UNIQUE (shipment_id, recipient_id)
);

CREATE TABLE shipment_additional_photos (
    shipment_id uuid NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    photo_order integer NOT NULL CHECK (photo_order BETWEEN 0 AND 3),
    photo_data text NOT NULL,
    PRIMARY KEY (shipment_id, photo_order)
);
