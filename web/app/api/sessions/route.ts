import { getServerSession } from 'next-auth'
import { NextRequest, NextResponse } from 'next/server'
import { authOptions } from '@/lib/auth'
import sql from '@/lib/db'

export async function GET() {
  const session = await getServerSession(authOptions)
  if (!session?.user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const userId = (session.user as { id: string }).id
  const rows = await sql`
    SELECT id, name, policy, state, created_at, last_active
    FROM sessions
    WHERE user_id = ${userId}
    ORDER BY last_active DESC
  `
  return NextResponse.json(rows)
}

export async function POST(req: NextRequest) {
  const session = await getServerSession(authOptions)
  if (!session?.user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const userId = (session.user as { id: string }).id
  const body = await req.json()
  const { name, policy = 'Persistent' } = body as { name: string; policy?: string }

  if (!name?.trim()) return NextResponse.json({ error: 'name required' }, { status: 400 })

  const rows = await sql`
    INSERT INTO sessions (user_id, name, policy)
    VALUES (${userId}, ${name.trim()}, ${policy})
    RETURNING id, name, policy, state, created_at, last_active
  `
  return NextResponse.json(rows[0], { status: 201 })
}
