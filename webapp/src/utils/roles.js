/** SUPER_ADMIN has every ADMIN right (server role hierarchy). */
export const isAdminRole = (role) => role === 'ADMIN' || role === 'SUPER_ADMIN'
