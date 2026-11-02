ALTER TABLE traveler_profiles ADD COLUMN terms_accepted_at timestamp with time zone;
ALTER TABLE traveler_profiles ADD COLUMN terms_version varchar(32);
