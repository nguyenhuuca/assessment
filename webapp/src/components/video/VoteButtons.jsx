import React, { useEffect, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faThumbsUp, faThumbsDown } from '@fortawesome/free-solid-svg-icons'
import { faThumbsUp as faThumbsUpReg, faThumbsDown as faThumbsDownReg } from '@fortawesome/free-regular-svg-icons'
import { videosApi } from '../../api/videos.js'

export default function VoteButtons({ video }) {
  const [voteState, setVoteState] = useState('none') // 'up' | 'down' | 'none'
  const [upCount, setUpCount] = useState(video?.upvotes || 0)
  const [downCount, setDownCount] = useState(video?.downvotes || 0)

  // Reset when video changes
  useEffect(() => {
    setVoteState('none')
    setUpCount(video?.upvotes || 0)
    setDownCount(video?.downvotes || 0)
  }, [video?.id])

  async function handleUp() {
    if (voteState === 'down') setDownCount(c => Math.max(0, c - 1))

    if (voteState === 'up') {
      setUpCount(c => Math.max(0, c - 1))
      setVoteState('none')
    } else {
      setUpCount(c => c + 1)
      setVoteState('up')
      try { await videosApi.like(video?.id) } catch {}
    }
  }

  async function handleDown() {
    if (voteState === 'up') setUpCount(c => Math.max(0, c - 1))

    if (voteState === 'down') {
      setDownCount(c => Math.max(0, c - 1))
      setVoteState('none')
    } else {
      setDownCount(c => c + 1)
      setVoteState('down')
      try { await videosApi.unlike(video?.id) } catch {}
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
      <div className="vote-action-group" style={{ textAlign: 'center' }}>
        <button
          onClick={handleUp}
          style={voteBtnStyle(voteState === 'up')}
        >
          <FontAwesomeIcon icon={voteState === 'up' ? faThumbsUp : faThumbsUpReg} />
        </button>
        <div style={{ color: 'white', fontSize: '0.75rem' }}>{upCount}</div>
      </div>
      <div className="vote-action-group" style={{ textAlign: 'center' }}>
        <button
          onClick={handleDown}
          style={voteBtnStyle(voteState === 'down')}
        >
          <FontAwesomeIcon icon={voteState === 'down' ? faThumbsDown : faThumbsDownReg} />
        </button>
        <div style={{ color: 'white', fontSize: '0.75rem' }}>{downCount}</div>
      </div>
    </div>
  )
}

function voteBtnStyle(active) {
  return {
    background: active ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.5)',
    border: 'none',
    borderRadius: 8,
    color: 'white',
    padding: '8px 10px',
    cursor: 'pointer',
    fontSize: '1.1rem',
  }
}
