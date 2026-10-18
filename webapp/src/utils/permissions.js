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
