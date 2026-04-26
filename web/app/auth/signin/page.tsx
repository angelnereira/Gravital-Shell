'use client'

import { signIn } from 'next-auth/react'
import { useSearchParams } from 'next/navigation'
import styles from './signin.module.css'

export default function SignInPage() {
  const params = useSearchParams()
  const callbackUrl = params.get('callbackUrl') ?? '/dashboard'

  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <h1 className={styles.logo}>Gravital Shell</h1>
        <p className={styles.tagline}>Sign in to access your sessions</p>
        <button
          className={styles.githubBtn}
          onClick={() => signIn('github', { callbackUrl })}
        >
          Continue with GitHub
        </button>
      </div>
    </main>
  )
}
