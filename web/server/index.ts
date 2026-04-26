import { createServer, IncomingMessage } from 'http'
import { parse } from 'url'
import next from 'next'
import { WebSocketServer, WebSocket } from 'ws'
import pty from 'node-pty'

const dev = process.env.NODE_ENV !== 'production'
const port = parseInt(process.env.PORT ?? '3000', 10)

const app = next({ dev })
const handle = app.getRequestHandler()

interface PtySession {
  ptyProcess: pty.IPty
  clients: Set<WebSocket>
}

const ptySessions = new Map<string, PtySession>()

function getOrCreatePty(sessionId: string): PtySession {
  const existing = ptySessions.get(sessionId)
  if (existing) return existing

  const shell = process.env.SHELL ?? '/bin/bash'
  const ptyProcess = pty.spawn(shell, [], {
    name: 'xterm-256color',
    cols: 80,
    rows: 24,
    cwd: process.env.HOME ?? '/',
    env: process.env as Record<string, string>,
  })

  const session: PtySession = { ptyProcess, clients: new Set() }

  ptyProcess.onData((data: string) => {
    for (const client of session.clients) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(data)
      }
    }
  })

  ptyProcess.onExit(() => {
    for (const client of session.clients) {
      client.close()
    }
    ptySessions.delete(sessionId)
  })

  ptySessions.set(sessionId, session)
  return session
}

app.prepare().then(() => {
  const server = createServer((req, res) => {
    const parsedUrl = parse(req.url!, true)
    handle(req, res, parsedUrl)
  })

  const wss = new WebSocketServer({ server, path: '/ws/terminal' })

  wss.on('connection', (ws: WebSocket, req: IncomingMessage) => {
    const url = parse(req.url ?? '', true)
    const sessionId = String(url.query.session ?? 'default')

    const session = getOrCreatePty(sessionId)
    session.clients.add(ws)

    ws.on('message', (raw) => {
      try {
        const msg = JSON.parse(raw.toString()) as { type: string; data?: string; cols?: number; rows?: number }
        if (msg.type === 'input' && msg.data) {
          session.ptyProcess.write(msg.data)
        } else if (msg.type === 'resize' && msg.cols && msg.rows) {
          session.ptyProcess.resize(msg.cols, msg.rows)
        }
      } catch {
        // ignore malformed messages
      }
    })

    ws.on('close', () => {
      session.clients.delete(ws)
    })
  })

  server.listen(port, () => {
    console.log(`> Ready on http://localhost:${port}`)
  })
})
