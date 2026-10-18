CREATE TABLE delivery_messages (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES delivery_requests(id),
    author_id uuid NOT NULL REFERENCES users(id),
    body varchar(2000) NOT NULL CHECK (length(trim(body)) > 0),
    created_at timestamp with time zone NOT NULL,
    sequence_number bigint NOT NULL CHECK (sequence_number > 0),
    CONSTRAINT uk_delivery_message_sequence UNIQUE (request_id, sequence_number)
);
