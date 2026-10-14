import React, { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../hooks/useAuth.js'
import { useTheme } from '../../hooks/useTheme.js'
import LoginForm from '../auth/LoginForm.jsx'
import MagicLinkHandler from '../auth/MagicLinkHandler.jsx'
import MFALoginModal from '../auth/MFALoginModal.jsx'
import ProfileModal from '../auth/ProfileModal.jsx'
import ShareModal from '../modals/ShareModal.jsx'
import DeleteConfirmModal from '../modals/DeleteConfirmModal.jsx'
import { CommentPanel } from '../comments/index.js'
import { PublicFeed } from '../video/VideoFeed.jsx'
import ExploreView from '../explore/ExploreView.jsx'
import LibraryView from '../library/LibraryView.jsx'
import ComingSoon from './ComingSoon.jsx'
import AdminView from '../admin/AdminView.jsx'
import SettingsPage from '../settings/SettingsPage.jsx'
import ActivityBar from '../common/ActivityBar.jsx'
import LanguageSwitcher from '../common/LanguageSwitcher.jsx'
import NotificationBell from '../notifications/NotificationBell.jsx'
import { useNotificationStream } from '../../hooks/useNotifications.js'
import { useLocaleSync } from '../../hooks/useLocaleSync.js'

// Mobile bottom nav (no side nav on mobile). Settings is only useful once logged in.
const MOBILE_NAV = [
  { key: 'home',     icon: 'home',          labelKey: 'navHome'     },
  { key: 'library',  icon: 'video_library', labelKey: 'navLibrary'  },
  { key: 'explore',  icon: 'explore',       labelKey: 'navExplore'  },
  { key: 'settings', icon: 'settings',      labelKey: 'navSettings', requiresAuth: true },
]

const SIDE_NAV = [
  { key: 'home',     icon: 'home',                labelKey: 'navHome'    },
  { key: 'explore',  icon: 'explore',             labelKey: 'navExplore' },
  { key: 'library',  icon: 'video_library',       labelKey: 'navLibrary' },
  { key: 'history',  icon: 'history',             labelKey: 'navHistory' },
  { key: 'admin',    icon: 'admin_panel_settings', labelKey: 'navAdmin',  requiresAdmin: true },
]

export default function AppShell() {
  const { t } = useTranslation(['shell', 'common'])
  const { isLoggedIn, user, logout, sessionNotice, clearSessionNotice } = useAuth()
  const { theme, toggle: toggleTheme } = useTheme()
  const [message,       setMessage]       = useState(null)
  const [mfaModal,      setMfaModal]      = useState({ show: false, pendingData: null })
  const [profileOpen,   setProfileOpen]   = useState(false)
  const [shareOpen,     setShareOpen]     = useState(false)
  const [deleteModal,   setDeleteModal]   = useState({ show: false, video: null })
  // Deep link from the URL: ?v={videoId}[&c={commentId}] or ?l={collectionId} — read once, then strip from the address bar
  const [initialLink] = useState(() => {
    const params = new URLSearchParams(window.location.search)
    const v = params.get('v')
    const c = params.get('c')
    const l = params.get('l')
    if (v || l) window.history.replaceState({}, document.title, window.location.pathname)
    return { v: v || null, c: v && c ? c : null, l: !v && l ? l : null }
  })
  const [activeNav,     setActiveNav]     = useState(() => (initialLink.l ? 'library' : 'home'))
  const [commentVideo,    setCommentVideo]    = useState(() => (initialLink.c ? { id: initialLink.v } : null))
  const [highlightCommentId, setHighlightCommentId] = useState(initialLink.c)
  const [feedKey,         setFeedKey]         = useState(0)
  const [mobileLoginOpen, setMobileLoginOpen] = useState(false)
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false)
  const [jumpIndex,       setJumpIndex]       = useState(0)
  const [muted,           setMuted]           = useState(true)
  const [deepLinkId,      setDeepLinkId]      = useState(initialLink.v)

  useNotificationStream()
  useLocaleSync()

  useEffect(() => {
    if (!sessionNotice) return
    setMessage({ text: sessionNotice, type: 'error' })
    clearSessionNotice()
    const timer = setTimeout(() => setMessage(null), 5000)
    return () => clearTimeout(timer)
  }, [sessionNotice, clearSessionNotice])

  function showComments(video) {
    setHighlightCommentId(null)
    setCommentVideo(video)
  }

  function closeComments() {
    setCommentVideo(null)
    setHighlightCommentId(null)
  }

  // Notification click: jump to the video, open its comments and highlight the comment
  function openNotification(n) {
    if (!n?.videoId) return
    setActiveNav('home')
    setDeepLinkId(String(n.videoId))
    setFeedKey(k => k + 1)
    setHighlightCommentId(n.commentId ? String(n.commentId) : null)
    setCommentVideo({ id: String(n.videoId) })
  }

  function showMsg(text, type = 'error') {
    setMessage({ text, type })
    setTimeout(() => setMessage(null), 5000)
  }

  function handleExploreNavigate(index) {
    setJumpIndex(index)
    setActiveNav('home')
  }

  const mobileNav = MOBILE_NAV.filter(n => !n.requiresAuth || isLoggedIn)
  const mobileSplit = Math.ceil(mobileNav.length / 2)
  const mobileTitle = activeNav === 'home' ? t('forYou') : t(SIDE_NAV.find(n => n.key === activeNav)?.labelKey ?? 'forYou')

  function renderMobileNavItem(item) {
    const active = activeNav === item.key
    return (
      <button
        key={item.key}
        className="mobile-nav-tab"
        aria-current={active ? 'page' : undefined}
        onClick={() => setActiveNav(item.key)}
        style={{ color: active ? 'var(--accent-cyan)' : 'var(--text-muted)' }}
      >
        <span className="material-symbols-outlined" style={{
          fontSize: 22,
          fontVariationSettings: active ? "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24" : undefined,
        }}>
          {item.icon}
        </span>
        {t(item.labelKey)}
      </button>
    )
  }

  return (
    <>
      <ActivityBar />
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

        {/* Mobile title — current section centered (desktop hidden via CSS) */}
        <span className="mobile-title">{mobileTitle}</span>

        {/* Right — auth controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 180, justifyContent: 'flex-end' }}>
          {activeNav === 'home' && (
            <button
              className={`icon-btn${mobileSearchOpen ? ' active' : ''}`}
              onClick={() => setMobileSearchOpen(v => !v)}
              title={t('searchVideos')}
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
              <NotificationBell onOpenNotification={openNotification} />
              <button className="icon-btn" onClick={() => setShareOpen(true)} title={t('shareVideo')}>
                <span className="material-symbols-outlined" style={{ fontSize: 20 }}>upload</span>
              </button>
              <button className="icon-btn" onClick={() => setProfileOpen(true)} title={t('profile')}>
                <span className="material-symbols-outlined" style={{ fontSize: 20 }}>person</span>
              </button>
              <button className="icon-btn" onClick={logout} title={t('common:logout')} style={{ color: '#ff6b6b' }}>
                <span className="material-symbols-outlined" style={{ fontSize: 20 }}>logout</span>
              </button>
            </>
          )}
          <LanguageSwitcher />
          <button className="icon-btn" onClick={toggleTheme} title={t('toggleTheme')}>
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
          title={t('search')}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 20 }}>search</span>
        </button>
        {!isLoggedIn ? (
          <>
            <button
              className={`icon-btn mobile-overlay-icon${mobileLoginOpen ? ' active' : ''}`}
              onClick={() => setMobileLoginOpen(v => !v)}
              title={t('common:login')}
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
                  <NotificationBell overlay onOpenNotification={openNotification} />
                  <button
                    className="icon-btn mobile-overlay-icon"
                    onClick={() => setProfileOpen(true)}
                    title={t('profile')}
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: 20 }}>person</span>
                  </button>

                <button className="icon-btn" onClick={logout} title={t('common:logout')} style={{ color: '#ff6b6b' }}>
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
            <span>{t(item.labelKey)}</span>
          </button>
        ))}
        <button
          className={`sidenav-item${activeNav === 'settings' ? ' active' : ''}`}
          style={{ marginTop: 'auto' }}
          onClick={() => setActiveNav('settings')}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 22 }}>settings</span>
          <span>{t('navSettings')}</span>
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
        overflow: (activeNav === 'explore' || activeNav === 'admin' || activeNav === 'settings' || activeNav === 'library') ? 'auto' : 'hidden',
        display: (activeNav === 'explore' || activeNav === 'admin' || activeNav === 'settings' || activeNav === 'library') ? 'block' : 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg)',
      }}>
        {activeNav === 'explore' ? (
          <ExploreView onNavigateToVideo={handleExploreNavigate} />
        ) : activeNav === 'admin' && user?.isAdmin ? (
          <AdminView />
        ) : activeNav === 'settings' && isLoggedIn ? (
          <SettingsPage />
        ) : activeNav === 'settings' && !isLoggedIn ? (
          <ComingSoon page="settings" />
        ) : activeNav === 'library' ? (
          <LibraryView
            initialCollectionId={initialLink.l}
            muted={muted}
            onMutedChange={setMuted}
            onShowComments={showComments}
          />
        ) : activeNav !== 'home' ? (
          <ComingSoon page={activeNav} />
        ) : (
          <PublicFeed
            key={`public-${feedKey}`}
            category={null}
            initialIndex={jumpIndex}
            deepLinkId={deepLinkId}
            onDeepLinkResolved={() => setDeepLinkId(null)}
            muted={muted}
            onMutedChange={setMuted}
            mobileSearchOpen={mobileSearchOpen}
            onCloseMobileSearch={() => setMobileSearchOpen(false)}
            onShowComments={showComments}
            onDeleteVideo={v => setDeleteModal({ show: true, video: v })}
            currentUser={user}
          />
        )}
      </main>

      {/* ── Mobile Bottom Nav ── */}
      <nav className="mobile-nav" style={{
        position: 'fixed', bottom: 0, left: 0, right: 0,
        height: 64, zIndex: 50,
        background: theme === 'light' ? 'rgba(255,255,255,0.95)' : 'rgba(14,14,14,0.88)',
        backdropFilter: 'blur(20px)',
        borderTop: '1px solid var(--border)',
        justifyContent: 'space-around',
        alignItems: 'center', padding: '0 8px',
      }}>
        {mobileNav.slice(0, mobileSplit).map(renderMobileNavItem)}

        {/* Center FAB — inline flex item (YouTube-style) */}
        {isLoggedIn && (
          <button className="mobile-nav-fab" onClick={() => setShareOpen(true)} title={t('shareAVideo')}>
            <span className="material-symbols-outlined" style={{ fontSize: 24 }}>add</span>
          </button>
        )}

        {mobileNav.slice(mobileSplit).map(renderMobileNavItem)}
      </nav>

      {/* ── FAB (desktop only, hidden on mobile via CSS) ── */}
      {isLoggedIn && (
        <button className="fab" onClick={() => setShareOpen(true)} title={t('shareAVideo')}>
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
        <CommentPanel
          video={commentVideo}
          onClose={closeComments}
          highlightCommentId={highlightCommentId}
        />
      )}
    </>
  )
}
