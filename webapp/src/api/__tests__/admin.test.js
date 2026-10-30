import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getDriveSyncFiles, updateVideoStatus, bulkModerateComments, getJobs, updateJob, runJob, previewJobCron, deleteVideo, updateVideoVisibility, createImports } from '../admin.js'
import { api } from '../client.js'

vi.mock('../client.js', () => ({ api: { get: vi.fn(), patch: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

beforeEach(() => vi.clearAllMocks())

describe('updateVideoVisibility', () => {
  it('sends the visibility value, not the legacy hidden flag', async () => {
    await updateVideoVisibility(5, 'COLLECTION_ONLY')
    expect(api.patch).toHaveBeenLastCalledWith('/admin/videos/5/visibility', { visibility: 'COLLECTION_ONLY' })
  })
})

describe('createImports', () => {
  it('omits visibility unless set', async () => {
    await createImports({ items: [{ url: 'u' }] })
    expect(api.post).toHaveBeenLastCalledWith('/admin/video-imports', { items: [{ url: 'u' }], scheduledAt: null })
    await createImports({ items: [{ url: 'u' }], visibility: 'COLLECTION_ONLY' })
    expect(api.post).toHaveBeenLastCalledWith('/admin/video-imports', { items: [{ url: 'u' }], scheduledAt: null, visibility: 'COLLECTION_ONLY' })
  })
})

describe('deleteVideo', () => {
  it('sends no query by default', async () => {
    await deleteVideo(5)
    expect(api.delete).toHaveBeenLastCalledWith('/admin/videos/5')
  })

  it('sends only the checked file flags', async () => {
    await deleteVideo(5, { deleteLocalFile: true })
    expect(api.delete).toHaveBeenLastCalledWith('/admin/videos/5?deleteLocalFile=true')
    await deleteVideo(5, { deleteLocalFile: true, deleteDriveFile: true })
    expect(api.delete).toHaveBeenLastCalledWith('/admin/videos/5?deleteLocalFile=true&deleteDriveFile=true')
  })
})

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

describe('PATCH calls', () => {
  // Regression: admin.js had its own fetch, which skipped Accept-Language and the TOKEN_REVOKED logout
  it('go through the shared client', async () => {
    await updateVideoStatus(7, 'ACTIVE')
    expect(api.patch).toHaveBeenCalledWith('/admin/videos/7/status', { status: 'ACTIVE' })
    await bulkModerateComments({ ids: ['a'], action: 'HIDE' })
    expect(api.patch).toHaveBeenLastCalledWith('/admin/comments/moderation', { ids: ['a'], action: 'HIDE' })
  })
})

describe("scheduled jobs", () => {
  it("uses the right URL, method and body", async () => {
    await getJobs()
    expect(api.get).toHaveBeenCalledWith("/admin/jobs")
    await updateJob("logStats", { cron: "0 0 * * * *", enabled: false })
    expect(api.patch).toHaveBeenCalledWith("/admin/jobs/logStats", { cron: "0 0 * * * *", enabled: false })
    await runJob("logStats")
    expect(api.post).toHaveBeenCalledWith("/admin/jobs/logStats/runs")
  })

  it('previews a cron with an encoded query and skips an empty zone', async () => {
    await previewJobCron('0 */5 * * * *', 'Asia/Ho_Chi_Minh')
    expect(api.get).toHaveBeenCalledWith('/admin/jobs/preview?cron=0%20*%2F5%20*%20*%20*%20*&zone=Asia%2FHo_Chi_Minh&count=5')
    await previewJobCron('0 0 3 * * *', null)
    expect(api.get).toHaveBeenLastCalledWith('/admin/jobs/preview?cron=0%200%203%20*%20*%20*&count=5')
  })
})
