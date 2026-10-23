/** SUPER_ADMIN has every ADMIN right (server role hierarchy). */
export const isAdminRole = (role) => role === 'ADMIN' || role === 'SUPER_ADMIN'

/** Only SUPER_ADMIN may trigger destructive/ops actions such as Drive force sync. */
export const isSuperAdmin = (role) => role === 'SUPER_ADMIN'

/** CONTRIBUTOR and above may file in-app reports (Report page, Admin notification tab). */
export const canReport = (role) => role === 'CONTRIBUTOR' || isAdminRole(role)

/** Same rule for a stored user object; older payloads without a role fall back to the isAdmin flag. */
export const userCanReport = (user) => canReport(user?.role) || !!user?.isAdmin
