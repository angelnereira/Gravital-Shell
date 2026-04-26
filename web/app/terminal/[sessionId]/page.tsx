import { getServerSession } from 'next-auth'
import { redirect } from 'next/navigation'
import { authOptions } from '@/lib/auth'
import sql from '@/lib/db'
import TerminalPage from './TerminalPage'

interface Props {
  params: { sessionId: string }
}

export default async function Page({ params }: Props) {
  const session = await getServerSession(authOptions)
  if (!session?.user) redirect('/auth/signin')

  const userId = (session.user as { id: string }).id
  const rows = await sql`
    SELECT id, name, policy, state
    FROM sessions
    WHERE id = ${params.sessionId} AND user_id = ${userId}
  `

  if (!rows.length) redirect('/dashboard')

  await sql`
    UPDATE sessions SET state = 'Running', last_active = NOW()
    WHERE id = ${params.sessionId}
  `

  return <TerminalPage sessionId={params.sessionId} sessionName={rows[0].name as string} />
}
