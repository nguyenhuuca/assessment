import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reactionsApi } from '../api/reactions.js'
import { useAuth } from './useAuth.js'

const EMPTY = { likeCount: 0, dislikeCount: 0, myReaction: null }

function applyReaction(summary, next) {
  const prev = summary?.myReaction ?? null
  let likeCount = summary?.likeCount ?? 0
  let dislikeCount = summary?.dislikeCount ?? 0
  if (prev === 'LIKE') likeCount = Math.max(0, likeCount - 1)
  if (prev === 'DISLIKE') dislikeCount = Math.max(0, dislikeCount - 1)
  if (next === 'LIKE') likeCount += 1
  if (next === 'DISLIKE') dislikeCount += 1
  return { ...summary, likeCount, dislikeCount, myReaction: next }
}

/**
 * Reaction summary for a video plus a mutation to set (LIKE/DISLIKE) or clear (null) the reaction.
 */
export function useVideoReaction(videoId) {
  const { isLoggedIn } = useAuth()
  const queryClient = useQueryClient()
  const queryKey = ['reaction', videoId, isLoggedIn]

  const query = useQuery({
    queryKey,
    enabled: !!videoId,
    queryFn: async () => {
      const res = await reactionsApi.get(videoId)
      return res.data || res
    },
  })

  const mutation = useMutation({
    mutationFn: (reaction) => (reaction ? reactionsApi.set(videoId, reaction) : reactionsApi.remove(videoId)),
    onMutate: async (reaction) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData(queryKey)
      queryClient.setQueryData(queryKey, applyReaction(previous ?? { videoId, ...EMPTY }, reaction))
      return { previous }
    },
    onError: (_err, _reaction, context) => {
      queryClient.setQueryData(queryKey, context?.previous)
    },
    onSuccess: (res) => {
      queryClient.setQueryData(queryKey, res.data || res)
    },
  })

  const summary = query.data ?? EMPTY

  return {
    likeCount: summary.likeCount ?? 0,
    dislikeCount: summary.dislikeCount ?? 0,
    myReaction: summary.myReaction ?? null,
    isPending: mutation.isPending,
    error: mutation.error,
    react: mutation.mutate,
  }
}
