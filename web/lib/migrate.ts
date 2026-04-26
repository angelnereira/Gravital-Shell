import { Pool } from '@neondatabase/serverless'
import { readFileSync } from 'fs'
import { join } from 'path'

async function migrate() {
  const pool = new Pool({ connectionString: process.env.DATABASE_URL })
  const schema = readFileSync(join(__dirname, 'schema.sql'), 'utf8')
  await pool.query(schema)
  await pool.end()
  console.log('Migration complete.')
}

migrate().catch((err) => {
  console.error(err)
  process.exit(1)
})
