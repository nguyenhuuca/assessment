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

  // Only ids still on the page count: a refetch (e.g. after a delete) must not leave invisible ids selected.
  const onPage = new Set(pageIds)
  const visibleIds = [...selected].filter(id => onPage.has(id))
  const selectableIds = pageIds.filter(isSelectable)
  const allSelected = selectableIds.length > 0 && selectableIds.every(id => selected.has(id))
  const someSelected = pageIds.some(id => selected.has(id))

  const update = fn => setState(prev => ({ key: resetKey, set: fn(prev.key === resetKey ? prev.set : new Set()) }))

  return {
    selected,
    count: visibleIds.length,
    selectedIds: visibleIds,
    isSelected: id => selected.has(id),
    toggle: id => update(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    }),
    toggleAll: () => update(() => (allSelected ? new Set() : new Set(selectableIds.slice(0, max)))),
    removeMany: ids => update(prev => {
      const next = new Set(prev)
      ids.forEach(id => next.delete(id))
      return next
    }),
    clear: () => update(() => new Set()),
    allSelected,
    someSelected,
  }
}
