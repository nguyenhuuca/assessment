import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import VoteButtons from '../VoteButtons.jsx'
import { reactionsApi } from '../../../api/reactions.js'

let mockLoggedIn = true
vi.mock('../../../hooks/useAuth.js', () => ({ useAuth: () => ({ isLoggedIn: mockLoggedIn }) }))
vi.mock('../../../api/reactions.js', () => ({
  reactionsApi: { get: vi.fn(), set: vi.fn(), remove: vi.fn() },
}))

const HINT = 'Đăng nhập để thích video'

const summary = (likeCount, dislikeCount, myReaction) => ({
  data: { videoId: 'v1', likeCount, dislikeCount, myReaction },
})

function renderButtons(video) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const ui = (v) => (
    <QueryClientProvider client={client}>
      <VoteButtons video={v} />
    </QueryClientProvider>
  )
  const result = render(ui(video))
  return { ...result, rerenderVideo: (v) => result.rerender(ui(v)) }
}

const likeBtn = () => screen.getByTitle('Like')
const dislikeBtn = () => screen.getByTitle('Dislike')

describe('VoteButtons', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLoggedIn = true
    reactionsApi.get.mockResolvedValue(summary(5, 2, null))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the like count from the server', async () => {
    renderButtons({ id: 'v1' })
    expect(await screen.findByText('5')).toBeInTheDocument()
    expect(dislikeBtn()).toBeInTheDocument()
  })

  it('likes a video', async () => {
    reactionsApi.set.mockResolvedValue(summary(6, 2, 'LIKE'))
    renderButtons({ id: 'v1' })
    await screen.findByText('5')
    fireEvent.click(likeBtn())
    await waitFor(() => expect(likeBtn()).toHaveClass('voted'))
    expect(reactionsApi.set).toHaveBeenCalledWith('v1', 'LIKE')
    expect(screen.getByText('6')).toBeInTheDocument()
  })

  it('unlikes when clicking an active like', async () => {
    reactionsApi.get.mockResolvedValue(summary(5, 2, 'LIKE'))
    reactionsApi.remove.mockResolvedValue(summary(4, 2, null))
    renderButtons({ id: 'v1' })
    await waitFor(() => expect(likeBtn()).toHaveClass('voted'))
    fireEvent.click(likeBtn())
    await waitFor(() => expect(likeBtn()).not.toHaveClass('voted'))
    expect(reactionsApi.remove).toHaveBeenCalledWith('v1')
    expect(screen.getByText('4')).toBeInTheDocument()
  })

  it('dislikes a video', async () => {
    reactionsApi.set.mockResolvedValue(summary(5, 3, 'DISLIKE'))
    renderButtons({ id: 'v1' })
    await screen.findByText('5')
    fireEvent.click(dislikeBtn())
    await waitFor(() => expect(dislikeBtn()).toHaveClass('voted-down'))
    expect(reactionsApi.set).toHaveBeenCalledWith('v1', 'DISLIKE')
  })

  it('removes a dislike when clicking an active dislike', async () => {
    reactionsApi.get.mockResolvedValue(summary(5, 3, 'DISLIKE'))
    reactionsApi.remove.mockResolvedValue(summary(5, 2, null))
    renderButtons({ id: 'v1' })
    await waitFor(() => expect(dislikeBtn()).toHaveClass('voted-down'))
    fireEvent.click(dislikeBtn())
    await waitFor(() => expect(dislikeBtn()).not.toHaveClass('voted-down'))
    expect(reactionsApi.remove).toHaveBeenCalledWith('v1')
  })

  it('switches from like to dislike', async () => {
    reactionsApi.get.mockResolvedValue(summary(5, 2, 'LIKE'))
    reactionsApi.set.mockResolvedValue(summary(4, 3, 'DISLIKE'))
    renderButtons({ id: 'v1' })
    await waitFor(() => expect(likeBtn()).toHaveClass('voted'))
    fireEvent.click(dislikeBtn())
    await waitFor(() => expect(dislikeBtn()).toHaveClass('voted-down'))
    expect(likeBtn()).not.toHaveClass('voted')
    expect(reactionsApi.set).toHaveBeenCalledWith('v1', 'DISLIKE')
    expect(screen.getByText('4')).toBeInTheDocument()
  })

  it('switches from dislike to like', async () => {
    reactionsApi.get.mockResolvedValue(summary(5, 2, 'DISLIKE'))
    reactionsApi.set.mockResolvedValue(summary(6, 1, 'LIKE'))
    renderButtons({ id: 'v1' })
    await waitFor(() => expect(dislikeBtn()).toHaveClass('voted-down'))
    fireEvent.click(likeBtn())
    await waitFor(() => expect(likeBtn()).toHaveClass('voted'))
    expect(reactionsApi.set).toHaveBeenCalledWith('v1', 'LIKE')
  })

  it('guest click shows a hint and makes no write call', async () => {
    mockLoggedIn = false
    renderButtons({ id: 'v1' })
    await screen.findByText('5')
    fireEvent.click(likeBtn())
    expect(screen.getByText(HINT)).toBeInTheDocument()
    fireEvent.click(dislikeBtn())
    expect(reactionsApi.set).not.toHaveBeenCalled()
    expect(reactionsApi.remove).not.toHaveBeenCalled()
  })

  it('guest hint auto-hides', async () => {
    mockLoggedIn = false
    renderButtons({ id: 'v1' })
    await screen.findByText('5')
    vi.useFakeTimers()
    fireEvent.click(likeBtn())
    expect(screen.getByText(HINT)).toBeInTheDocument()
    act(() => { vi.advanceTimersByTime(3100) })
    expect(screen.queryByText(HINT)).not.toBeInTheDocument()
  })

  it('disables buttons while the mutation is pending', async () => {
    let resolve
    reactionsApi.set.mockReturnValue(new Promise((r) => { resolve = r }))
    renderButtons({ id: 'v1' })
    await screen.findByText('5')
    fireEvent.click(likeBtn())
    await waitFor(() => expect(likeBtn()).toBeDisabled())
    expect(dislikeBtn()).toBeDisabled()
    await act(async () => resolve(summary(6, 2, 'LIKE')))
    await waitFor(() => expect(likeBtn()).not.toBeDisabled())
  })

  it('rolls back when the API fails', async () => {
    reactionsApi.set.mockRejectedValue({ message: 'boom', status: 500 })
    renderButtons({ id: 'v1' })
    await screen.findByText('5')
    fireEvent.click(likeBtn())
    await waitFor(() => expect(reactionsApi.set).toHaveBeenCalled())
    await waitFor(() => expect(likeBtn()).not.toBeDisabled())
    expect(likeBtn()).not.toHaveClass('voted')
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Không lưu được, thử lại sau')
  })

  it('resets when the video changes', async () => {
    reactionsApi.get.mockImplementation(async (id) => (id === 'v1' ? summary(5, 2, 'LIKE') : summary(10, 3, null)))
    const { rerenderVideo } = renderButtons({ id: 'v1' })
    await waitFor(() => expect(likeBtn()).toHaveClass('voted'))
    rerenderVideo({ id: 'v2' })
    expect(await screen.findByText('10')).toBeInTheDocument()
    expect(likeBtn()).not.toHaveClass('voted')
  })
})
