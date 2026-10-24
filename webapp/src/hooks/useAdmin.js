import { useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import * as adminApi from '../api/admin.js'
import { hasActive } from '../utils/importUtils.js'
import { DEFAULT_TIME_FILTER, toRangeParams } from '../utils/timeRange.js'

const HLS_POLL_MS = 5000

/** Poll while any HLS conversion is queued or running, so the badge moves to READY / ERROR on its own. */
function hlsInFlight(data) {
  const rows = (data?.data ?? data)?.content ?? []
  return rows.some(v => v.hlsStatus === 'PENDING' || v.hlsStatus === 'PROCESSING')
}

export function useAdminVideos(page = 0, status = null) {
  return useQuery({
    queryKey: ['admin', 'videos', page, status],
    queryFn: () => adminApi.getVideos({ page, size: 20, ...(status ? { status } : {}) }),
    refetchInterval: query => (hlsInFlight(query.state.data) ? HLS_POLL_MS : false),
  })
}

export function useToggleVideoHls() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enable }) => (enable ? adminApi.enableVideoHls(id) : adminApi.disableVideoHls(id)),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'videos'] }),
  })
}

export function useAdminAccounts(page = 0) {
  return useQuery({
    queryKey: ['admin', 'accounts', page],
    queryFn: () => adminApi.getAccounts({ page, size: 20 }),
  })
}

export function useAdminStats() {
  return useQuery({
    queryKey: ['admin', 'stats'],
    queryFn: () => adminApi.getStats(),
  })
}

export function useUpdateVideoStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }) => adminApi.updateVideoStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'videos'] }),
  })
}

export function useUpdateVideoPriority() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, priority }) => adminApi.updateVideoPriority(id, priority),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'videos'] }),
  })
}

export function useUpdateVideoVisibility() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, hidden }) => adminApi.updateVideoVisibility(id, hidden),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'videos'] }),
  })
}

export function useDeleteVideo() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...opts }) => adminApi.deleteVideo(id, opts),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'videos'] }),
  })
}

export function useUpdateAccountRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, role }) => adminApi.updateAccountRole(id, role),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'accounts'] }),
  })
}

export function useUpdateAccountPermissions() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, permissions }) => adminApi.updateAccountPermissions(id, permissions),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'accounts'] }),
  })
}

export function useDeleteAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id) => adminApi.deleteAccount(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'accounts'] }),
  })
}

export function useAdminComments({ page = 0, status = null, q = '', videoId = null } = {}) {
  return useQuery({
    queryKey: ['admin', 'comments', page, status, q, videoId],
    queryFn: () => adminApi.getComments({ page, size: 20, status, q, videoId }),
  })
}

export function useModerateComment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }) => adminApi.moderateComment(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'comments'] })
      qc.invalidateQueries({ queryKey: ['admin', 'stats'] })
    },
  })
}

export function useHardDeleteComments() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids) => adminApi.hardDeleteComments(ids),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'comments'] })
      qc.invalidateQueries({ queryKey: ['admin', 'stats'] })
    },
  })
}

export function useBulkModerateComments() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => adminApi.bulkModerateComments(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'comments'] })
      qc.invalidateQueries({ queryKey: ['admin', 'stats'] })
    },
  })
}

export function useVideoImports(page = 0, status = null) {
  return useQuery({
    queryKey: ['admin', 'imports', page, status],
    queryFn: () => adminApi.listImports({ page, size: 20, status }),
    refetchInterval: (query) => (hasActive(query.state.data) ? 2000 : false),
  })
}

function useImportMutation(fn) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'imports'] }),
  })
}

export const useCreateImports  = () => useImportMutation((body) => adminApi.createImports(body))
export const useRunImportNow   = () => useImportMutation((id) => adminApi.runImportNow(id))
export const useCancelImport   = () => useImportMutation((id) => adminApi.cancelImport(id))
export const useRetryImport    = () => useImportMutation((id) => adminApi.retryImport(id))
export const usePreviewImport  = () => useMutation({ mutationFn: (url) => adminApi.previewImport(url) })

/** Relative presets resolve to a fresh window at fetch time, so the key holds the filter, not a timestamp. */
export function useDriveSyncFiles(filter = DEFAULT_TIME_FILTER) {
  const { params } = toRangeParams(filter)
  return useQuery({
    queryKey: ['admin', 'drive-sync', 'files', filter],
    queryFn: () => adminApi.getDriveSyncFiles(toRangeParams(filter).params),
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

/** Polls the current run every 2 s while active; refreshes the files list when it finishes. */
export function useDriveSyncRun() {
  const qc = useQueryClient()
  const query = useQuery({
    queryKey: ['admin', 'drive-sync', 'run'],
    queryFn: () => adminApi.getDriveSyncRun(),
    refetchInterval: (q) => (q.state.data?.data?.active ? 2000 : false),
  })
  const run = query.data?.data ?? null
  const active = !!run?.active
  const wasActive = useRef(false)
  useEffect(() => {
    if (wasActive.current && !active) {
      qc.invalidateQueries({ queryKey: ['admin', 'drive-sync', 'files'] })
    }
    wasActive.current = active
  }, [active, qc])
  return { ...query, run, active }
}

export function useStartDriveSyncRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (fileIds) => adminApi.startDriveSyncRun(fileIds),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'drive-sync', 'run'] }),
  })
}

/** Scheduler table; polls every 5 s while a job is running so the Run now button re-enables. */
export function useAdminJobs() {
  return useQuery({
    queryKey: ['admin', 'jobs'],
    queryFn: () => adminApi.getJobs(),
    refetchInterval: (q) => ((q.state.data?.data ?? []).some(j => j.running) ? 5000 : false),
  })
}

function useJobMutation(fn) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'jobs'] }),
  })
}

/** Next runs of the cron being edited (server validates); own key so list invalidation leaves it alone. */
export function useCronPreview(cron, zone, enabled) {
  return useQuery({
    queryKey: ['admin', 'job-preview', cron, zone ?? null],
    queryFn: () => adminApi.previewJobCron(cron, zone),
    enabled,
    retry: false,
    staleTime: 30_000,
  })
}

export const useUpdateJob = () => useJobMutation(({ name, body }) => adminApi.updateJob(name, body))
export const useRunJob    = () => useJobMutation((name) => adminApi.runJob(name))
