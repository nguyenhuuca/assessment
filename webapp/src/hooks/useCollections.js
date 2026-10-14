import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { collectionsApi } from '../api/collections.js'
import { mapApiVideo } from '../utils/videoModel.js'

export const COLLECTION_PAGE_SIZE = 24

const unwrap = (res) => res?.data ?? res
const asList = (res) => {
  const items = unwrap(res)
  return Array.isArray(items) ? items : []
}

/** Logged-in user's collections ("Xem sau" pinned first); admins also get the public ones they can manage. */
export function useMyCollections(enabled = true) {
  return useQuery({
    queryKey: ['collections', 'mine'],
    enabled,
    queryFn: async () => asList(await collectionsApi.mine()),
  })
}

export function usePublicCollections() {
  return useQuery({
    queryKey: ['collections', 'public'],
    queryFn: async () => asList(await collectionsApi.publicList()),
  })
}

/** One page of visible videos in a collection (mapped to the feed video shape). */
export function useCollectionVideos(id, page = 0) {
  return useQuery({
    queryKey: ['collections', id, 'videos', page],
    enabled: !!id,
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const data = unwrap(await collectionsApi.videos(id, page, COLLECTION_PAGE_SIZE)) || {}
      return {
        videos: (Array.isArray(data.content) ? data.content : []).map(mapApiVideo),
        totalPages: data.totalPages ?? 0,
        totalElements: data.totalElements ?? 0,
      }
    },
  })
}

/** Every video id in a collection the caller manages (shares the 'videos' key prefix, so toggles refresh it). */
export function useCollectionVideoIds(id, enabled = true) {
  return useQuery({
    queryKey: ['collections', id, 'videos', 'ids'],
    enabled: enabled && !!id,
    queryFn: async () => asList(await collectionsApi.videoIds(id)),
  })
}

/** Ids of the manageable collections that already contain the video. */
export function useCollectionMembership(videoId, enabled = true) {
  return useQuery({
    queryKey: ['collections', 'membership', videoId],
    enabled: enabled && !!videoId,
    queryFn: async () => asList(await collectionsApi.membership(videoId)),
  })
}

/** The public list is only refetched when the change can show up there (unknown visibility counts as public). */
function useInvalidateLists() {
  const qc = useQueryClient()
  return ({ includePublic = true } = {}) => Promise.all([
    qc.invalidateQueries({ queryKey: ['collections', 'mine'] }),
    includePublic && qc.invalidateQueries({ queryKey: ['collections', 'public'] }),
  ])
}

/** Create a collection; when `videoId` is given the video is added to it right away. */
export function useCreateCollection() {
  const qc = useQueryClient()
  const invalidateLists = useInvalidateLists()
  return useMutation({
    mutationFn: async ({ name, visibility, videoId }) => {
      const res = await collectionsApi.create({ name, ...(visibility ? { visibility } : {}) })
      const created = unwrap(res)
      if (videoId && created?.id) await collectionsApi.addVideo(created.id, videoId)
      return created
    },
    onSuccess: (_created, vars) => {
      if (vars.videoId) qc.invalidateQueries({ queryKey: ['collections', 'membership', vars.videoId] })
      return invalidateLists({ includePublic: vars.visibility === 'PUBLIC' })
    },
  })
}

/** Rename and/or change visibility. Visibility changes move a collection between lists, so both are refreshed. */
export function useUpdateCollection() {
  const invalidateLists = useInvalidateLists()
  return useMutation({
    mutationFn: ({ id, name, visibility }) =>
      collectionsApi.update(id, {
        ...(name !== undefined ? { name } : {}),
        ...(visibility !== undefined ? { visibility } : {}),
      }),
    onSuccess: () => invalidateLists(),
  })
}

export function useDeleteCollection() {
  const qc = useQueryClient()
  const invalidateLists = useInvalidateLists()
  return useMutation({
    mutationFn: (id) => collectionsApi.remove(id),
    onSuccess: (_res, id) => {
      qc.removeQueries({ queryKey: ['collections', id] })
      return invalidateLists()
    },
  })
}

/**
 * Add (`inCollection` false) or remove (true) a video from a collection.
 * Variables: { id, videoId, inCollection, visibility? } — pass the collection's visibility so private toggles skip the public list.
 */
export function useToggleCollectionItem() {
  const qc = useQueryClient()
  const invalidateLists = useInvalidateLists()
  return useMutation({
    mutationFn: ({ id, videoId, inCollection }) =>
      (inCollection ? collectionsApi.removeVideo(id, videoId) : collectionsApi.addVideo(id, videoId)),
    onSuccess: (_res, { id, videoId, visibility }) => Promise.all([
      invalidateLists({ includePublic: visibility !== 'PRIVATE' }),
      qc.invalidateQueries({ queryKey: ['collections', id, 'videos'] }),
      qc.invalidateQueries({ queryKey: ['collections', 'membership', videoId] }),
    ]),
  })
}
