'use client'

import { useEffect, useRef } from 'react'
import Link from 'next/link'
import styles from './terminal.module.css'

declare global {
  interface Window {
    Terminal: typeof import('xterm').Terminal
    FitAddon: { FitAddon: typeof import('@xterm/addon-fit').FitAddon }
  }
}

interface Props {
  sessionId: string
  sessionName: string
}

export default function TerminalPage({ sessionId, sessionName }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!containerRef.current) return

    let term: import('xterm').Terminal | null = null
    let ws: WebSocket | null = null
    let fitAddon: import('@xterm/addon-fit').FitAddon | null = null

    async function init() {
      const { Terminal } = await import('xterm')
      const { FitAddon } = await import('@xterm/addon-fit')
      const { WebLinksAddon } = await import('@xterm/addon-web-links')

      term = new Terminal({
        theme: {
          background: '#0f0f0f',
          foreground: '#d4d4d4',
          cursor: '#39ff14',
          cursorAccent: '#000',
          selectionBackground: 'rgba(57,255,20,0.3)',
        },
        fontFamily: '"JetBrains Mono", "Fira Code", monospace',
        fontSize: 14,
        lineHeight: 1.4,
        cursorBlink: true,
      })

      fitAddon = new FitAddon()
      term.loadAddon(fitAddon)
      term.loadAddon(new WebLinksAddon())
      term.open(containerRef.current!)
      fitAddon.fit()

      const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      ws = new WebSocket(`${proto}//${window.location.host}/ws/terminal?session=${sessionId}`)

      ws.onopen = () => {
        const { cols, rows } = term!
        ws!.send(JSON.stringify({ type: 'resize', cols, rows }))
      }

      ws.onmessage = (ev) => {
        if (typeof ev.data === 'string') {
          term!.write(ev.data)
        }
      }

      ws.onclose = () => {
        term!.write('\r\n\x1b[31m[Connection closed]\x1b[0m\r\n')
      }

      term.onData((data) => {
        if (ws?.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'input', data }))
        }
      })

      term.onResize(({ cols, rows }) => {
        if (ws?.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'resize', cols, rows }))
        }
      })

      const observer = new ResizeObserver(() => fitAddon?.fit())
      observer.observe(containerRef.current!)

      return () => observer.disconnect()
    }

    const cleanup = init()

    return () => {
      cleanup.then((fn) => fn?.())
      ws?.close()
      term?.dispose()
    }
  }, [sessionId])

  return (
    <div className={styles.layout}>
      <div className={styles.topBar}>
        <Link href="/dashboard" className={styles.backLink}>Dashboard</Link>
        <span className={styles.sessionName}>{sessionName}</span>
      </div>
      <div ref={containerRef} className={styles.terminal} />
    </div>
  )
}
