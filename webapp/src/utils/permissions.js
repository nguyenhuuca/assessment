// Mirrors api enums/Permission.java: one bit per permission
export const PERMISSIONS = [
  { key: 'READ', bit: 1 },
  { key: 'WRITE', bit: 2 },
  { key: 'EXEC', bit: 4 },
  { key: 'DELETE', bit: 8 },
  { key: 'ADMIN', bit: 16 },
]

export const hasPermission = (mask, bit) => ((mask ?? 0) & bit) !== 0

export const togglePermission = (mask, bit) => (mask ?? 0) ^ bit

// Mirrors the autopilot bits of api enums/Permission.java (CONTRIBUTOR/ADMIN accounts only)
export const AUTOPILOT_PERMISSIONS = [
  { key: 'AUTOPILOT_AUTO_MERGE', bit: 32 },
  { key: 'AUTOPILOT_AUTO', bit: 64 },
]

/** Auto labels a reporter may pick (spec BR-8): SUPER_ADMIN both, others by their autopilot bits. */
export const allowedAutoLabels = (role, mask) => {
  if (role === 'SUPER_ADMIN' || hasPermission(mask, 64)) return ['AUTO_MERGE', 'AUTO']
  if (hasPermission(mask, 32)) return ['AUTO_MERGE']
  return []
}
