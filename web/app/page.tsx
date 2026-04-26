import Link from 'next/link'
import styles from './page.module.css'

export default function LandingPage() {
  return (
    <main className={styles.main}>
      <nav className={styles.nav}>
        <span className={styles.logo}>Gravital Shell</span>
        <div className={styles.navLinks}>
          <Link href="/dashboard">Dashboard</Link>
          <Link href="/auth/signin" className={styles.ctaNav}>Sign in</Link>
        </div>
      </nav>

      <section className={styles.hero}>
        <h1 className={styles.headline}>
          A real Linux terminal,<br />anywhere.
        </h1>
        <p className={styles.sub}>
          Run Alpine Linux environments on Android — no root required.
          Access your sessions from any device via the web.
        </p>
        <div className={styles.ctaGroup}>
          <Link href="/auth/signin" className={styles.ctaBtn}>Get started</Link>
          <a
            href="https://github.com/angelnereira/gravital-shell"
            className={styles.secondaryBtn}
          >
            View on GitHub
          </a>
        </div>
      </section>

      <section className={styles.features}>
        <Feature
          title="Real Linux environment"
          body="Alpine Linux running via proot — no root, no emulation. Native binaries, real package manager."
        />
        <Feature
          title="Session persistence"
          body="Your environments survive app restarts. Ephemeral sandboxes auto-clean when you close them."
        />
        <Feature
          title="File bridge"
          body="Import files from Android into your environment. Export results back with one tap."
        />
        <Feature
          title="CLI tools ready"
          body="Start with Dev Toolkit, Claude Code, or Gemini CLI templates pre-configured."
        />
        <Feature
          title="Web access"
          body="Sign in and open a terminal from your browser. Your sessions follow you."
        />
        <Feature
          title="Open architecture"
          body="Rust core, Kotlin UI, Next.js web. All source available."
        />
      </section>

      <footer className={styles.footer}>
        <span>Gravital Labs</span>
      </footer>
    </main>
  )
}

function Feature({ title, body }: { title: string; body: string }) {
  return (
    <div className={styles.featureCard}>
      <h3>{title}</h3>
      <p>{body}</p>
    </div>
  )
}
