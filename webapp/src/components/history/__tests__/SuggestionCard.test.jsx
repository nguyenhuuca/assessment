import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n/index.js'
import SuggestionCard from '../SuggestionCard.jsx'
import { suggestionsApi } from '../../../api/suggestions.js'

vi.mock('../../../api/suggestions.js', () => ({ suggestionsApi: { requestRereview: vi.fn() } }))

const rejected = (over = {}) => ({
  id: 7, sourceUrl: 'https://youtu.be/abc', title: 'Cat', note: null, status: 'REJECTED', live: false,
  createdAt: '2026-10-10T10:00:00Z', rereviewCount: 0, canRequestRereview: true, ...over,
})

function renderCard(s) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const spy = vi.spyOn(qc, 'invalidateQueries')
  render(<QueryClientProvider client={qc}><ul><SuggestionCard suggestion={s} /></ul></QueryClientProvider>)
  return spy
}

const openDialog = () => {
  fireEvent.click(screen.getByRole('button', { name: 'Request re-review' }))
  return screen.getByRole('dialog')
}

beforeEach(async () => {
  vi.clearAllMocks()
  await i18n.changeLanguage('en')
  suggestionsApi.requestRereview.mockResolvedValue({ data: {} })
})

describe('SuggestionCard re-review', () => {
  it('shows the action only when canRequestRereview', () => {
    renderCard(rejected({ canRequestRereview: false, rereviewCount: 2 }))
    expect(screen.queryByRole('button', { name: 'Request re-review' })).toBeNull()
  })

  it('shows the requested counter when count > 0', () => {
    renderCard(rejected({ rereviewCount: 1 }))
    expect(screen.getByText('Re-review requested 1/2')).toBeTruthy()
  })

  it('hides the counter when count is 0', () => {
    renderCard(rejected())
    expect(screen.queryByText(/Re-review requested/)).toBeNull()
  })

  it('sends the trimmed message and invalidates suggestions', async () => {
    const spy = renderCard(rejected())
    const dialog = openDialog()
    const box = within(dialog).getByLabelText('Message (optional)')
    expect(box).toHaveAttribute('maxLength', '300')
    fireEvent.change(box, { target: { value: '  please look again  ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Send request' }))
    await waitFor(() => expect(suggestionsApi.requestRereview).toHaveBeenCalledWith(7, 'please look again'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(spy).toHaveBeenCalledWith({ queryKey: ['suggestions'] })
  })

  it('sends an empty message when left blank', async () => {
    renderCard(rejected())
    fireEvent.click(within(openDialog()).getByRole('button', { name: 'Send request' }))
    await waitFor(() => expect(suggestionsApi.requestRereview).toHaveBeenCalledWith(7, ''))
  })

  it('shows a translated error and keeps the dialog open', async () => {
    suggestionsApi.requestRereview.mockRejectedValue({ status: 409, code: 'error.suggestion.rereviewLimit', message: 'x' })
    renderCard(rejected())
    fireEvent.click(within(openDialog()).getByRole('button', { name: 'Send request' }))
    expect(await screen.findByText('You have used all re-review requests for this suggestion')).toBeTruthy()
    expect(screen.getByRole('dialog')).toBeTruthy()
  })

  it('falls back to the API message for unknown errors', async () => {
    suggestionsApi.requestRereview.mockRejectedValue({ status: 500, message: 'boom' })
    renderCard(rejected())
    fireEvent.click(within(openDialog()).getByRole('button', { name: 'Send request' }))
    expect(await screen.findByText('boom')).toBeTruthy()
  })

  it('renders markup in the note and typed message as literal text', () => {
    const markup = '<img src=x onerror=alert(1)>'
    renderCard(rejected({ note: markup }))
    expect(screen.getByText(markup)).toBeTruthy()
    expect(document.querySelector('img[src="x"]')).toBeNull()
    const box = within(openDialog()).getByLabelText('Message (optional)')
    fireEvent.change(box, { target: { value: markup } })
    expect(box).toHaveValue(markup)
    expect(document.querySelector('img[src="x"]')).toBeNull()
  })

  it('cancel closes without calling the API', () => {
    renderCard(rejected())
    fireEvent.click(within(openDialog()).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(suggestionsApi.requestRereview).not.toHaveBeenCalled()
  })
})
