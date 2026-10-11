/** SUPER_ADMIN has every ADMIN right (server role hierarchy). */
export const isAdminRole = (role) => role === 'ADMIN' || role === 'SUPER_ADMIN'

/** Only SUPER_ADMIN may trigger destructive/ops actions such as Drive force sync. */
export const isSuperAdmin = (role) => role === 'SUPER_ADMIN'
