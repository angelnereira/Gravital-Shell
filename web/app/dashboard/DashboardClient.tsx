'use client'

import { useState } from 'react'
import { signOut } from 'next-auth/react'
import Link from 'next/link'
import type { WebSession } from './page'
import styles from './dashboard.module.css'

interface Props {
  user: { name: string; image: string | null }
  initialSessions: WebSession[]
}

export default function DashboardClient({ user, initialSessions }: Props) {
  const [sessions, setSessions] = useState<WebSession[]>(initialSessions)
  const [creating, setCreating] = useState(false)
  const [newName, setNewName] = useState('')
  const [newPolicy, setNewPolicy] = useState('Persistent')

  async function createSession() {
    if (!newName.trim()) return
    const res = await fetch('/api/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newName.trim(), policy: newPolicy }),
    })
    if (res.ok) {
      const created = await res.json() as WebSession
      setSessions((prev) => [created, ...prev])
      setNewName('')
      setCreating(false)
    }
  }

  async function deleteSession(id: string) {
    await fetch(`/api/sessions/${id}`, { method: 'DELETE' })
    setSessions((prev) => prev.filter((s) => s.id !== id))
  }

  return (
    <div className={styles.layout}>
      <aside className={styles.sidebar}>
        <span className={styles.logo}>Gravital Shell</span>
        <nav className={styles.sideNav}>
          <Link href="/dashboard" className={styles.navItem + ' ' + styles.active}>Sessions</Link>
        </nav>
        <div className={styles.userRow}>
          {user.image && <img src={user.image} alt="" className={styles.avatar} />}
          <span className={styles.userName}>{user.name}</span>
          <button className={styles.signOutBtn} onClick={() => signOut()}>Out</button>
        </div>
      </aside>

      <main className={styles.main}>
        <div className={styles.header}>
          <h1 className={styles.title}>Sessions</h1>
          <button className="btn-primary" onClick={() => setCreating(true)}>New session</button>
        </div>

        {creating && (
          <div className={styles.createForm}>
            <input
              className={styles.input}
              placeholder="Session name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && createSession()}
              autoFocus
            />
            <select
              className={styles.select}
              value={newPolicy}
              onChange={(e) => setNewPolicy(e.target.value)}
            >
              <option value="Persistent">Persistent</option>
              <option value="Ephemeral">Ephemeral</option>
              <option value="Snapshot">Snapshot</option>
            </select>
            <button className="btn-primary" onClick={createSession}>Create</button>
            <button className="btn-secondary" onClick={() => setCreating(false)}>Cancel</button>
          </div>
        )}

        {sessions.length === 0 ? (
          <div className={styles.empty}>
            <p>No sessions yet. Create one to get started.</p>
          </div>
        ) : (
          <div className={styles.sessionGrid}>
            {sessions.map((s) => (
              <div key={s.id} className={styles.sessionCard}>
                <div className={styles.sessionMeta}>
                  <span className={styles.sessionName}>{s.name}</span>
                  <span className={styles['badge_' + s.state.toLowerCase()] ?? styles.badge}>
                    {s.state}
                  </span>
                </div>
                <div className={styles.sessionInfo}>
                  <span className={styles.policy}>{s.policy}</span>
                  <span className={styles.date}>
                    {new Date(s.last_active).toLocaleDateString()}
                  </span>
                </div>
                <div className={styles.sessionActions}>
                  <Link href={`/terminal/${s.id}`} className={styles.openBtn}>Open terminal</Link>
                  <button className="btn-danger" onClick={() => deleteSession(s.id)}>Delete</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
