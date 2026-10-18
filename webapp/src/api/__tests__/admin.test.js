import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getDriveSyncFiles } from '../admin.js'
import { api } from '../client.js'

vi.mock('../client.js', () => ({ api: { get: vi.fn() } }))

beforeEach(() => vi.clearAllMocks())

describe('getDriveSyncFiles', () => {
  it('calls the bare path without params', async () => {
    await getDriveSyncFiles()
    await getDriveSyncFiles({})
    expect(api.get).toHaveBeenNthCalledWith(1, '/admin/drive-sync/files')
    expect(api.get).toHaveBeenNthCalledWith(2, '/admin/drive-sync/files')
  })

  it('sends from and to as an encoded query string', async () => {
    await getDriveSyncFiles({ from: '2026-10-18T00:00:00.000Z' })
    expect(api.get).toHaveBeenLastCalledWith('/admin/drive-sync/files?from=2026-10-18T00%3A00%3A00.000Z')
    await getDriveSyncFiles({ from: '2026-10-18T00:00:00.000Z', to: '2026-10-18T12:01:00.000Z' })
    expect(api.get).toHaveBeenLastCalledWith(
      '/admin/drive-sync/files?from=2026-10-18T00%3A00%3A00.000Z&to=2026-10-18T12%3A01%3A00.000Z')
  })

  it('drops empty values', async () => {
    await getDriveSyncFiles({ from: '', to: null })
    expect(api.get).toHaveBeenLastCalledWith('/admin/drive-sync/files')
  })
})
