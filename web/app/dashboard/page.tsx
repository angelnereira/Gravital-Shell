import { getServerSession } from 'next-auth'
import { redirect } from 'next/navigation'
import { authOptions } from '@/lib/auth'
import sql from '@/lib/db'
import DashboardClient from './DashboardClient'

export default async function DashboardPage() {
  const session = await getServerSession(authOptions)
  if (!session?.user) redirect('/auth/signin?callbackUrl=/dashboard')

  const userId = (session.user as { id: string }).id
  const sessions = await sql`
    SELECT id, name, policy, state, created_at, last_active
    FROM sessions
    WHERE user_id = ${userId}
    ORDER BY last_active DESC
  `

  return (
    <DashboardClient
      user={{ name: session.user.name ?? '', image: session.user.image ?? null }}
      initialSessions={sessions as WebSession[]}
    />
  )
}

export interface WebSession {
  id: string
  name: string
  policy: string
  state: string
  created_at: string
  last_active: string
}
