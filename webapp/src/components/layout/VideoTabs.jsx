import React, { useState } from 'react'
import { Nav } from 'react-bootstrap'
import { useAuth } from '../../hooks/useAuth.js'
import { PublicFeed, PrivateFeed } from '../video/VideoFeed.jsx'

const TABS = [
  { key: 'popular', label: 'Popular', category: null },
  { key: 'funny', label: 'Funny', category: 'funny' },
  { key: 'private', label: 'Private', requiresAuth: true },
]

export default function VideoTabs({ onShowComments, onDeleteVideo }) {
  const { isLoggedIn, user } = useAuth()
  const [activeTab, setActiveTab] = useState('popular')
  const [privateLoaded, setPrivateLoaded] = useState(false)

  const visibleTabs = TABS.filter(t => !t.requiresAuth || isLoggedIn)

  function handleTabSelect(key) {
    setActiveTab(key)
    if (key === 'private') setPrivateLoaded(true)
  }

  return (
    <div>
      <Nav variant="tabs" activeKey={activeTab} onSelect={handleTabSelect} className="mb-3">
        {visibleTabs.map(tab => (
          <Nav.Item key={tab.key}>
            <Nav.Link eventKey={tab.key}>{tab.label}</Nav.Link>
          </Nav.Item>
        ))}
      </Nav>

      <div>
        {activeTab === 'popular' && (
          <PublicFeed
            category={null}
            onShowComments={onShowComments}
            onDeleteVideo={onDeleteVideo}
            currentUser={user}
          />
        )}
        {activeTab === 'funny' && (
          <PublicFeed
            category="funny"
            onShowComments={onShowComments}
            onDeleteVideo={onDeleteVideo}
            currentUser={user}
          />
        )}
        {activeTab === 'private' && privateLoaded && (
          <PrivateFeed
            onShowComments={onShowComments}
            onDeleteVideo={onDeleteVideo}
            currentUser={user}
          />
        )}
      </div>
    </div>
  )
}
