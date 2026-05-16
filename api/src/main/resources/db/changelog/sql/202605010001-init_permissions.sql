-- =============================
-- Create permissions table
-- =============================
CREATE TABLE permissions
(
    id          INT PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    bit_value   INT          NOT NULL UNIQUE,
    description TEXT,
    group_name  VARCHAR(50),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

-- =============================
-- Seed permissions (aligned with Permission enum bit values)
-- =============================
INSERT INTO permissions (id, code, name, bit_value, description, group_name)
VALUES (1, 'READ',   'Read',   1,  'Read data',         'GENERAL'),
       (2, 'WRITE',  'Write',  2,  'Modify data',       'GENERAL'),
       (3, 'EXEC',   'Exec',   4,  'Execute actions',   'GENERAL'),
       (4, 'DELETE', 'Delete', 8,  'Delete data',       'GENERAL'),
       (5, 'ADMIN',  'Admin',  16, 'Full admin access', 'ADMIN');

ALTER TABLE users
    ADD COLUMN permissions INT NOT NULL DEFAULT 0;