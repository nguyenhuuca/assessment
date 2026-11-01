import { useState } from 'react'

/**
 * Per-page bulk selection. The selection is dropped during render when `resetKey` changes
 * (page / filter), so no effect and no extra paint.
 */
export function useBulkSelection({ pageIds, resetKey, max = 100, isSelectable = () => true }) {
  const [state, setState] = useState({ key: resetKey, set: new Set() })
  let selected = state.set
  if (state.key !== resetKey) {
    selected = new Set()
    setState({ key: resetKey, set: selected })
  }

  const selectableIds = pageIds.filter(isSelectable)
  const allSelected = selectableIds.length > 0 && selectableIds.every(id => selected.has(id))
  const someSelected = pageIds.some(id => selected.has(id))

  const update = fn => setState(prev => ({ key: resetKey, set: fn(prev.key === resetKey ? prev.set : new Set()) }))

  return {
    selected,
    count: selected.size,
    selectedIds: [...selected],
    isSelected: id => selected.has(id),
    toggle: id => update(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    }),
    toggleAll: () => update(() => (allSelected ? new Set() : new Set(selectableIds.slice(0, max)))),
    clear: () => update(() => new Set()),
    allSelected,
    someSelected,
  }
}
