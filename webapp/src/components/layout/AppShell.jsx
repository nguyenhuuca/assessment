import React, { useState } from 'react'
import { Button, Container, Navbar } from 'react-bootstrap'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faMoon, faSun, faShareAlt, faUser, faSignOutAlt } from '@fortawesome/free-solid-svg-icons'
import { useAuth } from '../../hooks/useAuth.js'
import { useTheme } from '../../hooks/useTheme.js'
import LoginForm from '../auth/LoginForm.jsx'
import MagicLinkHandler from '../auth/MagicLinkHandler.jsx'
import MFALoginModal from '../auth/MFALoginModal.jsx'
import ProfileModal from '../auth/ProfileModal.jsx'
import ShareModal from '../modals/ShareModal.jsx'
import DeleteConfirmModal from '../modals/DeleteConfirmModal.jsx'
import { CommentPanel } from '../comments/index.js'
import VideoTabs from './VideoTabs.jsx'

export default function AppShell() {
  const { isLoggedIn, user, logout } = useAuth()
  const { theme, toggle: toggleTheme } = useTheme()
  const [message, setMessage] = useState(null) // { text, type }
  const [mfaModal, setMfaModal] = useState({ show: false, pendingData: null })
  const [profileOpen, setProfileOpen] = useState(false)
  const [shareOpen, setShareOpen] = useState(false)
  const [deleteModal, setDeleteModal] = useState({ show: false, video: null })
  const [commentVideo, setCommentVideo] = useState(null)

  function showMessage(text, type = 'error') {
    setMessage({ text, type })
    setTimeout(() => setMessage(null), 5000)
  }

  function handleMfaRequired(data) {
    setMfaModal({ show: true, pendingData: data })
  }

  function handleDeleteVideo(video) {
    setDeleteModal({ show: true, video })
  }

  return (
    <div data-theme={theme === 'light' ? 'light' : undefined} style={{ minHeight: '100vh' }}>
      {/* Magic link handler - renders nothing, handles ?token= */}
      <MagicLinkHandler
        onMfaRequired={handleMfaRequired}
        onError={text => showMessage(text, 'error')}
      />

      {/* Header */}
      <Navbar expand={false} className="px-3 py-2" style={{ background: 'var(--bg-header, #111)', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
        <Navbar.Brand style={{ color: 'white', fontWeight: 'bold', fontSize: '1.1rem' }}>
          🎬 Funny Movies
        </Navbar.Brand>

        <div className="ms-auto d-flex align-items-center gap-2 flex-wrap">
          {!isLoggedIn ? (
            <LoginForm onMfaRequired={handleMfaRequired} />
          ) : (
            <>
              <span style={{ color: '#aaa', fontSize: '0.85rem' }}>Welcome {user?.email}</span>
              <Button size="sm" variant="outline-light" onClick={() => setShareOpen(true)}>
                <FontAwesomeIcon icon={faShareAlt} className="me-1" />Share
              </Button>
              <Button size="sm" variant="outline-light" onClick={() => setProfileOpen(true)}>
                <FontAwesomeIcon icon={faUser} />
              </Button>
              <Button size="sm" variant="outline-danger" onClick={logout}>
                <FontAwesomeIcon icon={faSignOutAlt} />
              </Button>
            </>
          )}
          <Button size="sm" variant="outline-secondary" onClick={toggleTheme} title="Toggle theme">
            <FontAwesomeIcon icon={theme === 'dark' ? faMoon : faSun} />
          </Button>
        </div>
      </Navbar>

      {/* Global message banner */}
      {message && (
        <div style={{
          background: message.type === 'error' ? '#c0392b' : '#27ae60',
          color: 'white', padding: '8px 16px', textAlign: 'center', fontSize: '0.9rem',
        }}>
          {message.text}
        </div>
      )}

      {/* Main content */}
      <Container fluid className="py-3" style={{ maxWidth: 600 }}>
        <VideoTabs
          onShowComments={setCommentVideo}
          onDeleteVideo={handleDeleteVideo}
        />
      </Container>

      {/* Modals */}
      <MFALoginModal
        show={mfaModal.show}
        pendingData={mfaModal.pendingData}
        onHide={() => setMfaModal({ show: false, pendingData: null })}
      />
      <ProfileModal show={profileOpen} onHide={() => setProfileOpen(false)} />
      <ShareModal show={shareOpen} onHide={() => setShareOpen(false)} />
      <DeleteConfirmModal
        show={deleteModal.show}
        video={deleteModal.video}
        onHide={() => setDeleteModal({ show: false, video: null })}
      />

      {/* Comment panel (slide-in) */}
      {commentVideo && (
        <CommentPanel video={commentVideo} onClose={() => setCommentVideo(null)} />
      )}
    </div>
  )
}
