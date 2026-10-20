import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createReport, listReports, retryReport } from '../reports.js'
import { api } from '../client.js'

vi.mock('../client.js', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

beforeEach(() => vi.clearAllMocks())

describe('reports api', () => {
  it('createReport posts the body to /admin/reports', async () => {
    const body = { type: 'BUG', title: 't', description: 'd', autoLabel: null, context: { page: 'admin' } }
    await createReport(body)
    expect(api.post).toHaveBeenCalledWith('/admin/reports', body)
  })

  it('listReports defaults to page 0 size 20', async () => {
    await listReports()
    expect(api.get).toHaveBeenCalledWith('/admin/reports?page=0&size=20')
  })

  it('listReports sends the given page and size', async () => {
    await listReports({ page: 3, size: 50 })
    expect(api.get).toHaveBeenCalledWith('/admin/reports?page=3&size=50')
  })

  it('retryReport posts to the retry path', async () => {
    await retryReport('6f1c2b0e')
    expect(api.post).toHaveBeenCalledWith('/admin/reports/6f1c2b0e/retry')
  })
})
