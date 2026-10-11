-- SUPER_ADMIN: a single owner account. The partial unique index makes a second one impossible.
UPDATE users
SET role = 'SUPER_ADMIN'
WHERE lower(user_name) = 'nguyenhuuca@gmail.com';

CREATE UNIQUE INDEX ux_users_single_super_admin ON users (role) WHERE role = 'SUPER_ADMIN';
