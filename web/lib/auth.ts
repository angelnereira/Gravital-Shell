import type { NextAuthOptions } from 'next-auth'
import GitHubProvider from 'next-auth/providers/github'
import sql from './db'

export const authOptions: NextAuthOptions = {
  providers: [
    GitHubProvider({
      clientId: process.env.GITHUB_CLIENT_ID!,
      clientSecret: process.env.GITHUB_CLIENT_SECRET!,
    }),
  ],
  session: { strategy: 'jwt' },
  callbacks: {
    async signIn({ user }) {
      if (!user.email) return false
      await sql`
        INSERT INTO users (id, email, name, image)
        VALUES (${user.id!}, ${user.email}, ${user.name ?? null}, ${user.image ?? null})
        ON CONFLICT (id) DO UPDATE
          SET name = EXCLUDED.name, image = EXCLUDED.image
      `
      return true
    },
    async session({ session, token }) {
      if (session.user && token.sub) {
        ;(session.user as { id?: string }).id = token.sub
      }
      return session
    },
  },
  pages: {
    signIn: '/auth/signin',
  },
}
