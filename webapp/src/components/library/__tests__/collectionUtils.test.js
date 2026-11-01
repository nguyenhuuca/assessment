import { describe, it, expect } from 'vitest'
import { moveAfterId, moveAfter, stableOrder, sortByOrder } from '../collectionUtils.js'

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

describe('stableOrder / sortByOrder', () => {
  const items = ids => ids.map(id => ({ id }))

  it('takes the server order the first time', () => {
    expect(stableOrder(null, items([1, 2, 3]))).toEqual(['1', '2', '3'])
  })

  it('keeps known ids in place, appends new ones and drops removed ones', () => {
    expect(stableOrder(['1', '2', '3'], items([4, 3, 1]))).toEqual(['1', '3', '4'])
  })

  it('sorts by the kept order, unknown ids last', () => {
    expect(sortByOrder(items([3, 9, 1, 2]), ['1', '2', '3']).map(x => x.id)).toEqual([1, 2, 3, 9])
    expect(sortByOrder(items([2, 1]), null).map(x => x.id)).toEqual([2, 1])
  })
})
