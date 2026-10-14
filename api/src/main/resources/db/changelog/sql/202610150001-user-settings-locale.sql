ALTER TABLE user_settings ADD COLUMN locale VARCHAR(5) NULL CHECK (locale IN ('vi','en'));
