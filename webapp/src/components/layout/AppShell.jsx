import React, { useState } from 'react'
import { useAuth } from '../../hooks/useAuth.js'
import { useTheme } from '../../hooks/useTheme.js'
import LoginForm from '../auth/LoginForm.jsx'
import MagicLinkHandler from '../auth/MagicLinkHandler.jsx'
import MFALoginModal from '../auth/MFALoginModal.jsx'
import ProfileModal from '../auth/ProfileModal.jsx'
import ShareModal from '../modals/ShareModal.jsx'
import DeleteConfirmModal from '../modals/DeleteConfirmModal.jsx'
import { CommentPanel } from '../comments/index.js'
import { PublicFeed, PrivateFeed } from '../video/VideoFeed.jsx'
import ExploreView from '../explore/ExploreView.jsx'
import ComingSoon from './ComingSoon.jsx'
import AdminView from '../admin/AdminView.jsx'

const TABS = [
  { key: 'popular', label: 'Popular', icon: 'local_fire_department' },
  { key: 'funny',   label: 'Funny',   icon: 'sentiment_very_satisfied' },
  { key: 'private', label: 'Private', icon: 'lock', requiresAuth: true },
]

const SIDE_NAV = [
  { key: 'home',     icon: 'home',                label: 'Home'    },
  { key: 'explore',  icon: 'explore',             label: 'Explore' },
  { key: 'library',  icon: 'video_library',       label: 'Library' },
  { key: 'history',  icon: 'history',             label: 'History' },
  { key: 'admin',    icon: 'admin_panel_settings', label: 'Admin',  requiresAdmin: true },
]

export default function AppShell() {
  const { isLoggedIn, user, logout } = useAuth()
  const { theme, toggle: toggleTheme } = useTheme()
  const [activeNav,     setActiveNav]     = useState('home')
  const [activeTab,     setActiveTab]     = useState('popular')
  const [privateLoaded, setPrivateLoaded] = useState(false)
  const [message,       setMessage]       = useState(null)
  const [mfaModal,      setMfaModal]      = useState({ show: false, pendingData: null })
  const [profileOpen,   setProfileOpen]   = useState(false)
  const [shareOpen,     setShareOpen]     = useState(false)
  const [deleteModal,   setDeleteModal]   = useState({ show: false, video: null })
  const [commentVideo,    setCommentVideo]    = useState(null)
  const [mobileLoginOpen, setMobileLoginOpen] = useState(false)
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false)
  const [jumpIndex,       setJumpIndex]       = useState(0)
  const [muted,           setMuted]           = useState(true)
  const [deepLinkId,      setDeepLinkId]      = useState(() => {
    const params = new URLSearchParams(window.location.search)
    const v = params.get('v')
    if (v) window.history.replaceState({}, document.title, window.location.pathname)
    return v || null
  })

  function showMsg(text, type = 'error') {
    setMessage({ text, type })
    setTimeout(() => setMessage(null), 5000)
  }

  function handleTabSelect(key) {
    setActiveTab(key)
    if (key === 'private') setPrivateLoaded(true)
  }

  function handleExploreNavigate(index) {
    setJumpIndex(index)
    setActiveTab('popular')
    setActiveNav('home')
  }

  const visibleTabs = TABS.filter(t => !t.requiresAuth || isLoggedIn)

  return (
    <>
      <MagicLinkHandler
        onMfaRequired={data => setMfaModal({ show: true, pendingData: data })}
        onError={txt => showMsg(txt)}
      />

      {/* ── Top Header ── */}
      <header style={{
        position: 'fixed', top: 0, left: 0, right: 0,
        height: 'var(--topnav-h)', zIndex: 50,
        background: 'var(--bg)',
        borderBottom: '1px solid var(--border)',
        display: 'flex', alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px', gap: 16,
      }}>
        {/* Brand (hidden on mobile) */}
        <div className="topnav-brand" style={{
          fontFamily: 'var(--font-brand)',
          fontSize: 26, letterSpacing: 3,
          color: 'var(--text)',
          userSelect: 'none',
          minWidth: 180, whiteSpace: 'nowrap',
        }}>
          FUNNY MOVIES
        </div>

        {/* Mobile title — active tab label centered (desktop hidden via CSS) */}
        <span className="mobile-title">
          {visibleTabs.find(t => t.key === activeTab)?.label ?? 'For You'}
        </span>

        {/* Center — tabs (hidden on mobile) */}
        <nav className="topnav-tabs" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          {visibleTabs.map(tab => (
            <button
              key={tab.key}
              className={`topnav-tab${activeTab === tab.key ? ' active' : ''}`}
              onClick={() => handleTabSelect(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </nav>

        {/* Right — auth controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 180, justifyContent: 'flex-end' }}>
          {activeNav === 'home' && (
            <button
              className={`icon-btn${mobileSearchOpen ? ' active' : ''}`}
              onClick={() => setMobileSearchOpen(v => !v)}
              title="Search videos"
              style={mobileSearchOpen ? { color: 'var(--accent-cyan)', background: 'var(--accent-cyan-dim)' } : undefined}
            >
              <span className="material-symbols-outlined" style={{ fontSize: 20 }}>search</span>
            </button>
          )}
          {!isLoggedIn ? (
            <LoginForm onMfaRequired={data => setMfaModal({ show: true, pendingData: data })} />
          ) : (
            <>
              <span style={{
                color: 'var(--text-muted)', fontSize: 13,
                maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {user?.email?.split('@')[0]}
              </span>
              <button className="icon-btn" onClick={() => setShareOpen(true)} title="Share video">
                <span className="material-symbols-outlined" style={{ fontSize: 20 }}>upload</span>
              </button>
              <button className="icon-btn" onClick={() => setProfileOpen(true)} title="Profile">
                <span className="material-symbols-outlined" style={{ fontSize: 20 }}>person</span>
              </button>
              <button className="icon-btn" onClick={logout} title="Logout" style={{ color: '#ff6b6b' }}>
                <span className="material-symbols-outlined" style={{ fontSize: 20 }}>logout</span>
              </button>
            </>
          )}
          <button className="icon-btn" onClick={toggleTheme} title="Toggle theme">
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>
              {theme === 'dark' ? 'dark_mode' : 'light_mode'}
            </span>
          </button>
        </div>
      </header>

      <div className="mobile-video-overlay-controls">
        <button
          className={`icon-btn mobile-overlay-icon${mobileSearchOpen ? ' active' : ''}`}
          onClick={() => setMobileSearchOpen(v => !v)}
          title="Search"
        >
          <span className="material-symbols-outlined" style={{ fontSize: 20 }}>search</span>
        </button>
        {!isLoggedIn ? (
          <>
            <button
              className={`icon-btn mobile-overlay-icon${mobileLoginOpen ? ' active' : ''}`}
              onClick={() => setMobileLoginOpen(v => !v)}
              title="Login"
            >
              <span className="material-symbols-outlined" style={{ fontSize: 20 }}>person</span>
            </button>
            {mobileLoginOpen && (
              <div className="mobile-login-popover">
                <LoginForm
                  onMfaRequired={data => setMfaModal({ show: true, pendingData: data })}
                />
              </div>
            )}
          </>
        ) : (
            <>
                  <button
                    className="icon-btn mobile-overlay-icon"
                    onClick={() => setProfileOpen(true)}
                    title="Profile"
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: 20 }}>person</span>
                  </button>

                <button className="icon-btn" onClick={logout} title="Logout" style={{ color: '#ff6b6b' }}>
                    <span className="material-symbols-outlined" style={{ fontSize: 20 }}>logout</span>
                </button>
            </>
        )}
      </div>

      {/* ── Side Nav (desktop) ── */}
      <aside className="desktop-sidenav" style={{
        position: 'fixed', left: 0,
        top: 'var(--topnav-h)', bottom: 0,
        width: 'var(--sidenav-w)', zIndex: 40,
        background: 'var(--bg-surface)',
        borderRight: '1px solid var(--border)',
        display: 'flex', flexDirection: 'column',
        alignItems: 'center',
        paddingTop: 8, paddingBottom: 16,
      }}>
        {SIDE_NAV.filter(item => !item.requiresAdmin || user?.isAdmin).map(item => (
          <button
            key={item.key}
            className={`sidenav-item${activeNav === item.key ? ' active' : ''}`}
            onClick={() => setActiveNav(item.key)}
          >
            <span className="material-symbols-outlined" style={{
              fontSize: 22,
              fontVariationSettings: activeNav === item.key
                ? "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24"
                : undefined,
            }}>
              {item.icon}
            </span>
            <span>{item.label}</span>
          </button>
        ))}
        <button
          className={`sidenav-item${activeNav === 'settings' ? ' active' : ''}`}
          style={{ marginTop: 'auto' }}
          onClick={() => setActiveNav('settings')}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 22 }}>settings</span>
          <span>Settings</span>
        </button>
      </aside>

      {/* ── Global message banner ── */}
      {message && (
        <div className="global-message-banner" style={{
          position: 'fixed',
          top: 'var(--topnav-h)',
          left: 'var(--sidenav-w)', right: 0,
          zIndex: 45,
          background: message.type === 'error' ? '#b92902' : '#006970',
          color: 'white', padding: '10px 20px',
          textAlign: 'center', fontSize: 13, fontWeight: 500,
          animation: 'slideUp 0.2s ease',
        }}>
          {message.text}
        </div>
      )}

      {/* ── Main Content ── */}
      <main className="main-content" style={{
        marginLeft: 'var(--sidenav-w)',
        marginTop: 'var(--topnav-h)',
        height: 'calc(100vh - var(--topnav-h))',
        overflow: (activeNav === 'explore' || activeNav === 'admin') ? 'auto' : 'hidden',
        display: (activeNav === 'explore' || activeNav === 'admin') ? 'block' : 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg)',
      }}>
        {activeNav === 'explore' ? (
          <ExploreView onNavigateToVideo={handleExploreNavigate} />
        ) : activeNav === 'admin' && user?.isAdmin ? (
          <AdminView />
        ) : activeNav !== 'home' ? (
          <ComingSoon page={activeNav} />
        ) : (
          <>
            {activeTab === 'popular' && (
              <PublicFeed
                category={null}
                initialIndex={jumpIndex}
                deepLinkId={deepLinkId}
                onDeepLinkResolved={() => setDeepLinkId(null)}
                muted={muted}
                onMutedChange={setMuted}
                mobileSearchOpen={mobileSearchOpen}
                onCloseMobileSearch={() => setMobileSearchOpen(false)}
                onShowComments={setCommentVideo}
                onDeleteVideo={v => setDeleteModal({ show: true, video: v })}
                currentUser={user}
              />
            )}
            {activeTab === 'funny' && (
              <PublicFeed
                category="funny"
                muted={muted}
                onMutedChange={setMuted}
                mobileSearchOpen={mobileSearchOpen}
                onCloseMobileSearch={() => setMobileSearchOpen(false)}
                onShowComments={setCommentVideo}
                onDeleteVideo={v => setDeleteModal({ show: true, video: v })}
                currentUser={user}
              />
            )}
            {activeTab === 'private' && privateLoaded && (
              <PrivateFeed
                muted={muted}
                onMutedChange={setMuted}
                mobileSearchOpen={mobileSearchOpen}
                onCloseMobileSearch={() => setMobileSearchOpen(false)}
                onShowComments={setCommentVideo}
                onDeleteVideo={v => setDeleteModal({ show: true, video: v })}
                currentUser={user}
              />
            )}
          </>
        )}
      </main>

      {/* ── Mobile Bottom Nav ── */}
      <nav className="mobile-nav" style={{
        position: 'fixed', bottom: 0, left: 0, right: 0,
        height: 64, zIndex: 50,
        background: 'rgba(14,14,14,0.88)',
        backdropFilter: 'blur(20px)',
        borderTop: '1px solid var(--border)',
        justifyContent: 'space-around',
        alignItems: 'center', padding: '0 8px',
      }}>
        {/* Left tabs */}
        {(isLoggedIn
          ? visibleTabs.slice(0, Math.ceil(visibleTabs.length / 2))
          : visibleTabs
        ).map(tab => (
          <button key={tab.key} onClick={() => handleTabSelect(tab.key)} className="mobile-nav-tab" style={{
            color: activeTab === tab.key ? 'var(--accent-cyan)' : 'var(--text-muted)',
          }}>
            <span className="material-symbols-outlined" style={{
              fontSize: 22,
              fontVariationSettings: activeTab === tab.key
                ? "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24"
                : undefined,
            }}>
              {tab.icon}
            </span>
            {tab.label}
          </button>
        ))}

        {/* Center FAB — inline flex item (YouTube-style) */}
        {isLoggedIn && (
          <button className="mobile-nav-fab" onClick={() => setShareOpen(true)} title="Share a video">
            <span className="material-symbols-outlined" style={{ fontSize: 24 }}>add</span>
          </button>
        )}

        {/* Right tabs (only when logged in) */}
        {isLoggedIn && visibleTabs.slice(Math.ceil(visibleTabs.length / 2)).map(tab => (
          <button key={tab.key} onClick={() => handleTabSelect(tab.key)} className="mobile-nav-tab" style={{
            color: activeTab === tab.key ? 'var(--accent-cyan)' : 'var(--text-muted)',
          }}>
            <span className="material-symbols-outlined" style={{
              fontSize: 22,
              fontVariationSettings: activeTab === tab.key
                ? "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24"
                : undefined,
            }}>
              {tab.icon}
            </span>
            {tab.label}
          </button>
        ))}

        {/* Explore — 5th item to center the FAB */}
        {(
          <button
            className="mobile-nav-tab"
            onClick={() => setActiveNav(v => v === 'explore' ? 'home' : 'explore')}
            style={{ color: activeNav === 'explore' ? 'var(--accent-cyan)' : 'var(--text-muted)' }}
          >
            <span className="material-symbols-outlined" style={{
              fontSize: 22,
              fontVariationSettings: activeNav === 'explore'
                ? "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24"
                : undefined,
            }}>
              explore
            </span>
            Explore
          </button>
        )}
      </nav>

      {/* ── FAB (desktop only, hidden on mobile via CSS) ── */}
      {isLoggedIn && (
        <button className="fab" onClick={() => setShareOpen(true)} title="Share a video">
          <span className="material-symbols-outlined" style={{ fontSize: 26 }}>add</span>
        </button>
      )}

      {/* ── Modals ── */}
      <MFALoginModal
        show={mfaModal.show}
        pendingData={mfaModal.pendingData}
        onHide={() => setMfaModal({ show: false, pendingData: null })}
      />
      <ProfileModal    show={profileOpen}  onHide={() => setProfileOpen(false)} />
      <ShareModal      show={shareOpen}    onHide={() => setShareOpen(false)} />
      <DeleteConfirmModal
        show={deleteModal.show}
        video={deleteModal.video}
        onHide={() => setDeleteModal({ show: false, video: null })}
      />

      {/* ── Comment panel ── */}
      {commentVideo && (
        <CommentPanel video={commentVideo} onClose={() => setCommentVideo(null)} />
      )}
    </>
  )
}
