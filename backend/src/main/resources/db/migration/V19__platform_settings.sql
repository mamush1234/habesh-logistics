-- Single-row platform settings managed from the admin console.
CREATE TABLE platform_settings (
    id smallint PRIMARY KEY CHECK (id = 1),
    platform_fee_percent numeric(5,2) NOT NULL CHECK (platform_fee_percent >= 0 AND platform_fee_percent <= 100),
    agent_commission_percent numeric(5,2) NOT NULL CHECK (agent_commission_percent >= 0 AND agent_commission_percent <= 100),
    fee_payer varchar(16) NOT NULL CHECK (fee_payer IN ('SENDER', 'TRAVELER', 'SPLIT')),
    updated_at timestamp with time zone NOT NULL,
    updated_by uuid REFERENCES users(id)
);

INSERT INTO platform_settings (id, platform_fee_percent, agent_commission_percent, fee_payer, updated_at)
VALUES (1, 10.00, 10.00, 'SENDER', CURRENT_TIMESTAMP);
