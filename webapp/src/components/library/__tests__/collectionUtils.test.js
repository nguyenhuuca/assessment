import { describe, it, expect } from 'vitest'
import { moveAfterId, moveAfter } from '../collectionUtils.js'

describe('moveAfterId', () => {
  const ids = [10, 20, 30, 40]
  it('first to last: after the old last item', () => expect(moveAfterId(ids, 0, 3)).toBe(40))
  it('last to first: null (top)', () => expect(moveAfterId(ids, 3, 0)).toBeNull())
  it('middle move names the new predecessor', () => {
    expect(moveAfterId(ids, 1, 2)).toBe(30)
    expect(moveAfterId(ids, 3, 1)).toBe(10)
  })
  it('no-op keeps the current predecessor', () => {
    expect(moveAfterId(ids, 2, 2)).toBe(20)
    expect(moveAfterId(ids, 0, 0)).toBeNull()
  })
})

describe('moveAfter', () => {
  const items = [{ id: 1 }, { id: 2 }, { id: 3 }]
  const ids = r => r.map(x => x.id)
  it('moves after a neighbour or to the top', () => {
    expect(ids(moveAfter(items, 1, 3, x => x.id))).toEqual([2, 3, 1])
    expect(ids(moveAfter(items, 3, null, x => x.id))).toEqual([3, 1, 2])
  })
  it('ignores unknown ids', () => {
    expect(moveAfter(items, 9, null, x => x.id)).toBe(items)
    expect(moveAfter(items, 1, 9, x => x.id)).toBe(items)
  })
})
