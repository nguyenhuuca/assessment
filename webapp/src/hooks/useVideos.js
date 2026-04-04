import { useQuery } from '@tanstack/react-query'
import { videosApi } from '../api/videos.js'
import { mapApiVideo } from '../utils/videoModel.js'

export function useVideos(category) {
  return useQuery({
    queryKey: ['videos', category],
    queryFn: async () => {
      const res = await videosApi.list(category)
      const items = res.data || res
      return (Array.isArray(items) ? items : [])
        .map(mapApiVideo)
        .sort((a, b) => b.upvotes - a.upvotes)
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function usePrivateVideos() {
  return useQuery({
    queryKey: ['videos', 'private'],
    queryFn: async () => {
      const res = await videosApi.privateList()
      const items = res.data || res
      return (Array.isArray(items) ? items : []).map(mapApiVideo)
    },
    staleTime: 2 * 60 * 1000,
  })
}
