CREATE TABLE delivery_reviews (
    id uuid PRIMARY KEY,
    package_id uuid NOT NULL REFERENCES delivery_requests(id),
    author_id uuid NOT NULL REFERENCES users(id),
    recipient_id uuid NOT NULL REFERENCES users(id),
    direction varchar(32) NOT NULL CHECK (direction IN ('SENDER_TO_CARRIER', 'CARRIER_TO_SENDER')),
    rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment varchar(2000),
    legacy boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_delivery_review_direction UNIQUE (package_id, direction),
    CONSTRAINT uk_delivery_review_author UNIQUE (package_id, author_id),
    CHECK (author_id <> recipient_id)
);

CREATE TABLE delivery_review_scores (
    review_id uuid NOT NULL REFERENCES delivery_reviews(id) ON DELETE CASCADE,
    criterion varchar(32) NOT NULL CHECK (criterion IN ('COMMUNICATION', 'ON_TIME_DELIVERY',
        'PACKAGE_HANDLING', 'RELIABILITY', 'ACCURATE_DESCRIPTION', 'HANDOFF_TIMELINESS', 'PACKAGING_QUALITY')),
    score integer NOT NULL CHECK (score BETWEEN 1 AND 5),
    PRIMARY KEY (review_id, criterion)
);

CREATE INDEX idx_delivery_reviews_recipient ON delivery_reviews(recipient_id, created_at);

-- Preserve existing one-way ratings without inventing category scores or comments.
INSERT INTO delivery_reviews (id, package_id, author_id, recipient_id, direction, rating, legacy, created_at)
SELECT r.id, r.id, r.sender_id, o.traveler_id, 'SENDER_TO_CARRIER', r.rating, true, r.updated_at
FROM delivery_requests r JOIN delivery_offers o ON o.id = r.accepted_offer_id
WHERE r.rating IS NOT NULL AND r.sender_id IS NOT NULL AND r.sender_id <> o.traveler_id;
