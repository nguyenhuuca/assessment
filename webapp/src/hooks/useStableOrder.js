import { useState } from 'react'
import { stableOrder, sortByOrder } from '../components/library/collectionUtils.js'

/**
 * `items` in the order they first arrived in: a refetch that re-sorts the list on the server
 * (e.g. by updatedAt) does not move rows that are already shown; new rows are appended.
 * The order is adjusted during render when `items` changes (no effect, no extra paint).
 */
export function useStableOrder(items, getId) {
  const [state, setState] = useState({ source: undefined, order: null })
  let { order } = state
  if (items && items !== state.source) {
    order = stableOrder(state.order, items, getId)
    setState({ source: items, order })
  }
  return sortByOrder(items ?? [], order, getId)
}
