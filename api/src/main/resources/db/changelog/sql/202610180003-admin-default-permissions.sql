-- Role changes now set permissions to UserRole.defaultPermissions(): admins get every bit (31), users none.
-- Backfill admins that still have no bits; accounts with hand-set bits are left alone.
UPDATE users SET permissions = 31 WHERE role IN ('ADMIN', 'SUPER_ADMIN') AND permissions = 0;
