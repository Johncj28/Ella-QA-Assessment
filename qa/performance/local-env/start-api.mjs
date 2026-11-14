/**
 * Start the Ella API locally WITHOUT Docker:
 *   1. a private PostgreSQL on port 5433 (embedded-postgres, data kept in local-env/pgdata)
 *   2. the Ella API on port 4000 (migrations run automatically on boot)
 *
 *   npm install          # once
 *   npm run api:start    # Ctrl+C stops the API and the database
 *
 * Options (environment variables):
 *   ELLA_API_DIR   path to the Ella API repo   (default: this repo's root, three levels up)
 *   DB_PORT        database port               (default 5433, matches the API's .env)
 *   RESET_DB=1     wipe the database first (or: npm run api:reset)
 */
import EmbeddedPostgres from 'embedded-postgres';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const API_DIR = process.env.ELLA_API_DIR || join(here, '..', '..', '..');
const DB_PORT = Number(process.env.DB_PORT || 5433);
const DATA_DIR = join(here, 'pgdata');
const DB = { user: 'postgres', password: 'postgres', name: 'ella' };

if (!existsSync(join(API_DIR, 'package.json'))) {
  console.error(`Ella API not found at ${API_DIR}. Set ELLA_API_DIR to the repo path.`);
  process.exit(1);
}

const RESET = process.env.RESET_DB === '1' || process.argv.includes('--reset');
if (RESET && existsSync(DATA_DIR)) {
  console.log('[db]  reset: deleting existing data');
  rmSync(DATA_DIR, { recursive: true, force: true });
}

const pg = new EmbeddedPostgres({
  databaseDir: DATA_DIR,
  user: DB.user,
  password: DB.password,
  port: DB_PORT,
  persistent: true,
  onLog: () => {},
});

let api;
let stopping = false;

async function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  console.log('\nStopping...');
  if (api && api.exitCode === null) {
    api.kill();
    console.log('[api] stopped');
  }
  try {
    await pg.stop();
    console.log('[db]  stopped');
  } catch (e) {
    // already stopped
  }
  process.exit(code);
}
process.on('SIGINT', () => stop(0));
process.on('SIGTERM', () => stop(0));

try {
  if (!existsSync(join(DATA_DIR, 'PG_VERSION'))) {
    console.log('[db]  first run: initialising database files...');
    await pg.initialise();
  }
  await pg.start();
  try {
    await pg.createDatabase(DB.name);
    console.log(`[db]  created database "${DB.name}"`);
  } catch (e) {
    // database already exists
  }
  console.log(`[db]  PostgreSQL ready on localhost:${DB_PORT}`);
} catch (e) {
  console.error(`[db]  could not start PostgreSQL on port ${DB_PORT}: ${e.message || e}`);
  console.error('      Is something else using that port? Check with: netstat -ano | findstr :' + DB_PORT);
  await stop(1);
}

if (!existsSync(join(API_DIR, 'dist', 'main.js'))) {
  console.log('[api] no build found, running npm run build...');
  const b = spawnSync('npm', ['run', 'build'], { cwd: API_DIR, stdio: 'inherit', shell: true });
  if (b.status !== 0) await stop(1);
}

console.log('[api] starting Ella API...');
api = spawn(process.execPath, ['dist/main'], {
  cwd: API_DIR,
  stdio: 'inherit',
  env: {
    ...process.env,
    PORT: process.env.PORT || '4000',
    DB_HOST: 'localhost',
    DB_PORT: String(DB_PORT),
    DB_USERNAME: DB.user,
    DB_PASSWORD: DB.password,
    DB_DATABASE: DB.name,
  },
});
api.on('exit', (code) => {
  if (!stopping) {
    console.error(`[api] exited with code ${code}. Is port 4000 already in use?`);
    stop(code || 1);
  }
});

// Announce readiness once /health answers
const port = process.env.PORT || '4000';
for (let i = 0; i < 60 && !stopping; i++) {
  await new Promise((r) => setTimeout(r, 1000));
  try {
    const res = await fetch(`http://localhost:${port}/health`);
    if (res.ok) {
      console.log(`\n✅ Ella API ready: http://localhost:${port}  (Swagger: http://localhost:${port}/api)`);
      console.log('   Run tests in another terminal, e.g.  k6 run tests/user/smoke.js');
      console.log('   Press Ctrl+C here to stop the API and the database.\n');
      break;
    }
  } catch (e) {
    // not up yet
  }
}
