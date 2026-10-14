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
