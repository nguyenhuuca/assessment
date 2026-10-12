/**
 * True while `mutation` is running for this row. `match` picks the id out of the
 * mutation variables (defaults to the variables themselves, e.g. mutate(id)).
 */
export function isBusyFor(mutation, id, match = v => v) {
  if (!mutation?.isPending) return false
  const vars = mutation.variables
  const value = match(vars)
  return Array.isArray(value) ? value.includes(id) : value === id
}
