/**
 * HTML + JSON report for every k6 run (k6 handleSummary).
 *
 * Each test file does:   export const handleSummary = report('user', 'smoke');
 * and k6 then writes, at the end of the run:
 *   reports/<testid>.html   styled report: cards, metrics, endpoints, thresholds, run details, checks
 *   reports/<testid>.json   raw end-of-test summary data
 * plus k6's usual end-of-test summary on the console (k6 skips its own once handleSummary exists).
 *
 * <testid> is -e TEST_ID=... (set by scripts/run.ps1) or <api>-<type>-<yyyyMMdd-HHmmss>.
 * The reports/ folder must exist (it is kept in git with reports/.gitkeep).
 */
import { CONFIG } from '../config/enviroment.js';

const TITLES = { user: 'User API', product: 'Product API', transaction: 'Transaction API' };

/** Endpoints shown in the "Endpoints" tab (the `name` tag set in src/api/*.js). */
export const ENDPOINTS = {
  user: ['POST /users', 'GET /users/:id', 'PUT /users/:id', 'GET /users'],
  product: ['POST /products', 'GET /products/:id', 'PUT /products/:id', 'GET /products'],
  transaction: ['GET /products/:id', 'POST /transactions', 'GET /transactions/:id', 'GET /transactions'],
};

/**
 * k6 only keeps per-endpoint numbers for sub-metrics that have a threshold, so this adds an
 * always-true threshold per endpoint. They are hidden from the report's Thresholds tab.
 */
export function endpointThresholds(api) {
  const t = {};
  for (const name of ENDPOINTS[api] || []) {
    t[`http_req_duration{name:${name}}`] = ['max>=0'];
    t[`http_req_failed{name:${name}}`] = ['rate>=0'];
    t[`http_reqs{name:${name}}`] = ['count>=0'];
  }
  return t;
}

const isTracking = (metricName) => metricName.includes('{name:');

export function report(api, type) {
  return function handleSummary(data) {
    const now = new Date();
    const testId = __ENV.TEST_ID || `${api}-${type}-${stamp(now)}`;
    const html = `reports/${testId}.html`;
    const json = `reports/${testId}.json`;
    return {
      [html]: renderHtml(data, { api, type, testId, now }),
      [json]: JSON.stringify(data, null, 2),
      stdout: renderText(data, { api, type, testId, html, json }),
    };
  };
}

// ---------------------------------------------------------------- helpers

const pad = (n) => String(n).padStart(2, '0');
const stamp = (d) => `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
const when = (d) => `${pad(d.getMonth() + 1)}/${pad(d.getDate())}/${d.getFullYear()}, ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const num = (v, d = 2) => (typeof v === 'number' && isFinite(v) ? v.toFixed(d) : '-');
const pct = (v) => (typeof v === 'number' ? `${(v * 100).toFixed(2)}%` : '-');
const ms = (v) => (typeof v === 'number' ? `${v.toFixed(2)}ms` : '-');
const val = (data, metric, key) => data.metrics[metric] && data.metrics[metric].values[key];

function bytes(b) {
  if (typeof b !== 'number') return '-';
  if (b < 1000) return `${Math.round(b)} B`;
  if (b < 1e6) return `${(b / 1e3).toFixed(1)} kB`;
  if (b < 1e9) return `${(b / 1e6).toFixed(1)} MB`;
  return `${(b / 1e9).toFixed(2)} GB`;
}

function duration(msTotal) {
  const s = Math.round((msTotal || 0) / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  if (h) return `${h}h${pad(m)}m${pad(r)}s`;
  if (m) return `${m}m${pad(r)}s`;
  return `${r}s`;
}

/** Thresholds defined in src/config/threshold.js (endpoint tracking ones excluded). */
function userThresholds(data) {
  const out = [];
  for (const [name, m] of Object.entries(data.metrics)) {
    if (!m.thresholds || isTracking(name)) continue;
    for (const [expr, r] of Object.entries(m.thresholds)) out.push({ metric: name, expr, ok: r.ok, m });
  }
  return out;
}

function actualFor(t) {
  const v = t.m.values;
  const key = (t.expr.match(/^\s*([a-z]+(?:\([\d.]+\))?)/) || [])[1];
  if (t.m.type === 'rate') return pct(v.rate);
  if (key && v[key] !== undefined) return t.m.contains === 'time' ? ms(v[key]) : num(v[key]);
  return '-';
}

/** Flattens nested groups into [{path, checks}]. */
function groupsWithChecks(group, path = []) {
  const out = [];
  const here = group.name ? [...path, group.name] : path;
  if (group.checks && group.checks.length) out.push({ path: here, checks: group.checks });
  for (const g of group.groups || []) out.push(...groupsWithChecks(g, here));
  return out;
}

// ---------------------------------------------------------------- console
// Same layout as k6's own end-of-test summary (k6 stops printing it once a test defines
// handleSummary), followed by the paths of the HTML and JSON reports.

const SECTIONS = {
  HTTP: ['http_req_duration', 'http_req_failed', 'http_reqs'],
  EXECUTION: ['dropped_iterations', 'iteration_duration', 'iterations', 'vus', 'vus_max'],
  NETWORK: ['data_received', 'data_sent'],
};
// built-in metrics k6 hides in its default ("compact") summary
const HIDDEN = ['checks', 'group_duration', 'http_req_blocked', 'http_req_connecting', 'http_req_receiving',
  'http_req_sending', 'http_req_tls_handshaking', 'http_req_waiting'];
const BUILTIN = new Set([...Object.values(SECTIONS).flat(), ...HIDDEN]);

const trim = (n, d) => String(Number(n.toFixed(d)));
function k6Time(v) {
  if (typeof v !== 'number') return '-';
  if (v < 1) return `${trim(v * 1000, 2)}µs`;
  if (v < 1000) return `${trim(v, 2)}ms`;
  if (v < 60000) return `${trim(v / 1000, 2)}s`;
  return `${Math.floor(v / 60000)}m${trim((v % 60000) / 1000, 1)}s`;
}
function k6Bytes(b) {
  // k6 style: 1 decimal below 10 (3.1 MB), whole numbers from 10 up (84 kB)
  const units = ['B', 'kB', 'MB', 'GB'];
  let v = b || 0;
  let i = 0;
  while (v >= 1000 && i < units.length - 1) { v /= 1000; i++; }
  return `${v < 10 && i > 0 ? trim(v, 1) : Math.round(v)} ${units[i]}`;
}
const rate6 = (v) => `${trim(v, 6)}/s`;

function metricLine(name, m) {
  const v = m.values;
  if (m.type === 'trend') {
    const stats = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];
    return { name, cols: stats.map((k) => `${k}=${m.contains === 'time' ? k6Time(v[k]) : trim(v[k], 2)}`) };
  }
  if (m.type === 'rate') {
    const total = v.passes + v.fails;
    return { name, value: pct(v.rate), cols: [`${v.passes} out of ${total}`] };
  }
  if (m.type === 'counter') {
    return m.contains === 'data'
      ? { name, value: k6Bytes(v.count), cols: [`${k6Bytes(v.rate)}/s`] }
      : { name, value: String(v.count), cols: [rate6(v.rate)] };
  }
  return { name, value: String(v.value), cols: [`min=${v.min}`, `max=${v.max}`] }; // gauge
}

function formatSection(lines, nameWidth) {
  const valueWidth = Math.max(0, ...lines.filter((l) => l.value !== undefined).map((l) => l.value.length));
  const colWidths = [];
  for (const l of lines) l.cols.forEach((c, i) => { colWidths[i] = Math.max(colWidths[i] || 0, c.length); });
  return lines.map((l) => {
    const label = `${l.name}${'.'.repeat(Math.max(3, nameWidth - l.name.length))}: `;
    const value = l.value !== undefined ? `${l.value.padEnd(valueWidth)} ` : '';
    const cols = l.cols.map((c, i) => (i < l.cols.length - 1 ? c.padEnd(colWidths[i]) : c)).join(' ');
    return `    ${label}${value}${cols}`.trimEnd();
  });
}

function renderText(data, { testId, html, json }) {
  const color = !(data.options && data.options.noColor) && data.state && data.state.isStdOutTTY;
  const paint = (code, s) => (color ? `\x1b[${code}m${s}\x1b[0m` : s);
  const ok = (pass) => (pass ? paint('32', '✓') : paint('31', '✗'));
  const out = [''];

  // █ THRESHOLDS
  const th = userThresholds(data);
  if (th.length) {
    out.push('', '  █ THRESHOLDS ', '');
    const byMetric = {};
    th.forEach((t) => (byMetric[t.metric] = byMetric[t.metric] || []).push(t));
    for (const metric of Object.keys(byMetric).sort()) {
      out.push(`    ${metric}`);
      for (const t of byMetric[metric]) {
        const key = (t.expr.match(/^\s*([a-z]+(?:\([\d.]+\))?)/) || [])[1] || 'value';
        const actual = t.m.type === 'rate' ? pct(t.m.values.rate)
          : t.m.contains === 'time' ? k6Time(t.m.values[key]) : trim(t.m.values[key] || 0, 2);
        out.push(`    ${ok(t.ok)} '${t.expr}' ${key}=${actual}`);
      }
      out.push('');
    }
  }

  // █ TOTAL RESULTS
  out.push('', '  █ TOTAL RESULTS ', '');
  const checks = data.metrics.checks;
  if (checks) {
    const c = checks.values;
    const total = c.passes + c.fails;
    const secs = (data.state.testRunDurationMs || 1) / 1000;
    out.push(...formatSection([
      { name: 'checks_total', value: String(total), cols: [rate6(total / secs)] },
      { name: 'checks_succeeded', value: pct(total ? c.passes / total : 0), cols: [`${c.passes} out of ${total}`] },
      { name: 'checks_failed', value: pct(total ? c.fails / total : 0), cols: [`${c.fails} out of ${total}`] },
    ], 19), '');
    for (const { checks: list } of groupsWithChecks(data.root_group)) {
      for (const ck of list) {
        out.push(`    ${ok(ck.fails === 0)} ${ck.name}`);
        if (ck.fails) out.push(paint('31', `      ↳  ${Math.round((ck.passes / (ck.passes + ck.fails)) * 100)}% — ✓ ${ck.passes} / ✗ ${ck.fails}`));
      }
    }
    out.push('');
  }

  const sections = [['CUSTOM', Object.keys(data.metrics).filter((n) => !n.includes('{') && !BUILTIN.has(n)).sort()],
    ...Object.entries(SECTIONS)];
  for (const [title, names] of sections) {
    const lines = [];
    for (const n of names) {
      if (!data.metrics[n]) continue;
      lines.push(metricLine(n, data.metrics[n]));
      const sub = data.metrics[`${n}{expected_response:true}`];
      if (sub) lines.push(metricLine('  { expected_response:true }', sub));
    }
    if (!lines.length) continue;
    out.push(`    ${title}`, ...formatSection(lines, 31), '');
  }

  out.push(`  HTML report : ${html}`, `  JSON summary: ${json}`, '', '');
  return out.join('\n');
}

// ---------------------------------------------------------------- HTML

const ICONS = {
  globe: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20"/></svg>',
  check: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="m7 12 3.5 3.5L17 9"/></svg>',
  cross: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6M9 9l6 6"/></svg>',
  warn: '<svg viewBox="0 0 24 24"><path d="M12 3 2 21h20L12 3z"/><path d="M12 10v5M12 18h.01"/></svg>',
  eye: '<svg viewBox="0 0 24 24"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>',
};

const card = (cls, label, value, sub, icon) =>
  `<div class="card ${cls}"><div class="card-label">${label}</div><div class="card-value">${value}</div>` +
  `<div class="card-sub">${sub}</div><div class="card-icon">${icon}</div></div>`;

const table = (head, rows, cls = '') =>
  `<div class="table-wrap"><table${cls ? ` class="${cls}"` : ''}>` +
  (head ? `<thead><tr>${head.map((h) => `<th>${h}</th>`).join('')}</tr></thead>` : '') +
  `<tbody>${rows.join('')}</tbody></table></div>`;

function renderHtml(data, { api, type, testId, now }) {
  const title = `${TITLES[api] || api} - ${type.charAt(0).toUpperCase()}${type.slice(1)} Test Report`;
  const runMs = data.state && data.state.testRunDurationMs;
  const th = userThresholds(data);
  const breached = th.filter((t) => !t.ok).length;
  const reqs = val(data, 'http_reqs', 'count') || 0;
  const reqRate = val(data, 'http_reqs', 'rate') || 0;
  const failRate = val(data, 'http_req_failed', 'rate') || 0;
  const failedReqs = val(data, 'http_req_failed', 'passes') || 0; // for http_req_failed, "passes" = failed requests
  const checkFails = val(data, 'checks', 'fails') || 0;
  const checkPasses = val(data, 'checks', 'passes') || 0;
  const passed = breached === 0;

  // Detailed metrics (top-level metrics only, no sub-metrics)
  const all = Object.entries(data.metrics)
    .filter(([n]) => !n.includes('{'))
    .sort(([a], [b]) => a.localeCompare(b));
  const trendRows = all
    .filter(([, m]) => m.type === 'trend')
    .map(([n, m]) =>
      `<tr><td class="name">${esc(n)}</td>` +
      ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'].map((k) => `<td>${num(m.values[k])}</td>`).join('') +
      '</tr>');
  const otherRows = all
    .filter(([, m]) => m.type !== 'trend')
    .map(([n, m]) => {
      const v = m.values;
      let value = '-';
      let detail = '';
      if (m.type === 'rate') {
        value = pct(v.rate);
        detail = n === 'checks' ? `${v.passes} passed of ${v.passes + v.fails}` : `${v.passes} failed of ${v.passes + v.fails}`;
      } else if (m.type === 'counter') {
        const isData = m.contains === 'data';
        value = isData ? bytes(v.count) : String(v.count);
        detail = isData ? `${bytes(v.rate)}/s` : `${num(v.rate)}/s`;
      } else if (m.type === 'gauge') {
        value = String(v.value);
        detail = `min ${v.min} / max ${v.max}`;
      }
      return `<tr><td class="name">${esc(n)}</td><td><span class="tag">${m.type}</span></td><td>${value}</td><td>${detail}</td></tr>`;
    });

  // Endpoints
  const endpointRows = (ENDPOINTS[api] || [])
    .map((name) => {
      const d = data.metrics[`http_req_duration{name:${name}}`];
      if (!d) return '';
      const v = d.values;
      const count = val(data, `http_reqs{name:${name}}`, 'count') || 0;
      const err = val(data, `http_req_failed{name:${name}}`, 'rate') || 0;
      return `<tr><td class="name">${esc(name)}</td><td>${count}</td><td>${ms(v.avg)}</td><td>${ms(v.med)}</td>` +
        `<td>${ms(v['p(90)'])}</td><td>${ms(v['p(95)'])}</td><td>${ms(v.max)}</td>` +
        `<td class="${err > 0 ? 'fail' : 'pass'}">${pct(err)}</td></tr>`;
    })
    .filter(Boolean);

  // Thresholds
  const thresholdRows = th.map((t) =>
    `<tr><td class="${t.ok ? 'pass' : 'fail'}">${t.ok ? '&#10004; PASS' : '&#10008; FAIL'}</td>` +
    `<td class="name">${esc(t.metric)}</td><td><code>${esc(t.expr)}</code></td><td>${actualFor(t)}</td></tr>`);

  // Run details
  const vusMax = val(data, 'vus_max', 'max') || val(data, 'vus', 'max') || '-';
  const details = [
    ['Test ID', testId],
    ['Test', TITLES[api] || api],
    ['Test type', type],
    ['Environment', CONFIG.ENV],
    ['Base URL', CONFIG.BASE_API_URL],
    ['Run duration', duration(runMs)],
    ['Report generated', when(now)],
    ['Iterations', val(data, 'iterations', 'count') || 0],
    ['Max virtual users', vusMax],
    ['Throughput', `${num(reqRate)} req/s`],
    ['Dropped iterations', val(data, 'dropped_iterations', 'count') || 0],
    ['Data received', bytes(val(data, 'data_received', 'count'))],
    ['Data sent', bytes(val(data, 'data_sent', 'count'))],
  ].map(([k, v]) => `<tr><td class="name">${k}</td><td>${esc(v)}</td></tr>`);

  // Checks & groups
  const groups = groupsWithChecks(data.root_group).map(({ path, checks }) => {
    const rows = checks.map((c) => {
      const total = c.passes + c.fails;
      const rate = total ? c.passes / total : 0;
      return `<tr><td class="${c.fails ? 'fail' : 'pass'}">${c.fails ? '&#10008;' : '&#10004;'}</td>` +
        `<td class="name">${esc(c.name)}</td><td>${c.passes}</td><td>${c.fails}</td>` +
        `<td><div class="bar"><span style="width:${(rate * 100).toFixed(1)}%"></span></div>${pct(rate)}</td></tr>`;
    });
    const heading = path.length
      ? `<h3 class="group">&#9656; Group: ${esc(path.join(' › '))}</h3>`
      : '<h3 class="group">&#9656; Checks outside a group</h3>';
    return heading + table(['', 'CHECK', 'PASSES', 'FAILURES', 'PASS RATE'], rows);
  });

  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}: ${esc(testId)}</title>
<style>
  :root { --p1:#7b5cff; --p2:#5b2fc9; --good1:#8fd19e; --good2:#6dbb83; --bad1:#f28b82; --bad2:#d9534f;
          --ink:#1f2330; --muted:#6b7280; --line:#e6e8ef; --bg:#f4f5fb; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: "Segoe UI", Inter, system-ui, -apple-system, sans-serif; color:var(--ink);
         background: linear-gradient(135deg,#6f86d6 0%,#8a64d8 100%); min-height:100vh; padding:16px; }
  .page { max-width:1400px; margin:0 auto; background:#fff; border-radius:14px; overflow:hidden; box-shadow:0 10px 40px rgba(40,20,90,.35); }
  header { background:linear-gradient(90deg,var(--p1),var(--p2)); color:#fff; padding:26px 32px; display:flex; align-items:center; gap:18px; flex-wrap:wrap; }
  .logo { width:54px; height:54px; border-radius:12px; background:rgba(255,255,255,.18); display:grid; place-items:center; font-weight:800; font-size:20px; }
  header h1 { margin:0; font-size:30px; font-weight:700; }
  header .meta { opacity:.85; font-size:14px; margin-top:4px; }
  .verdict { margin-left:auto; padding:8px 16px; border-radius:999px; font-weight:700; letter-spacing:.04em; background:#2fb36b; }
  .cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr)); gap:24px; padding:26px 26px 6px; }
  .card { position:relative; border-radius:10px; padding:22px 24px; color:#fff; min-height:120px; overflow:hidden; box-shadow:0 4px 12px rgba(0,0,0,.12); }
  .card.neutral { background:linear-gradient(135deg,#6a7fe0,#7a4fb0); }
  .card.good { background:linear-gradient(135deg,var(--good1),var(--good2)); }
  .card.bad { background:linear-gradient(135deg,var(--bad1),var(--bad2)); }
  .card-label { font-size:14px; font-weight:600; letter-spacing:.06em; text-transform:uppercase; opacity:.95; }
  .card-value { font-size:42px; font-weight:700; margin-top:8px; }
  .card-sub { font-size:13px; opacity:.9; }
  .card-icon svg { position:absolute; right:22px; top:26px; width:70px; height:70px; fill:none; stroke:rgba(255,255,255,.35); stroke-width:2.2; stroke-linecap:round; stroke-linejoin:round; }
  .tabs { display:flex; gap:4px; padding:20px 26px 0; flex-wrap:wrap; }
  .tab { border:1px solid var(--line); border-bottom:none; background:#f2f3f8; color:#4b5563; padding:13px 26px; border-radius:10px 10px 0 0; cursor:pointer; font-size:15px; font-weight:600; font-family:inherit; }
  .tab.active { background:#fff; color:var(--p2); position:relative; top:1px; }
  .panels { margin:0 26px 26px; border:1px solid var(--line); border-radius:0 10px 10px 10px; padding:10px 32px 28px; }
  .panel { display:none; } .panel.active { display:block; }
  h2 { font-size:19px; font-weight:600; margin:30px 0 14px; color:#2d3142; }
  h3.group { font-size:15px; margin:26px 0 10px; color:var(--p2); }
  .table-wrap { overflow-x:auto; }
  table { width:100%; border-collapse:collapse; font-size:14px; border-radius:8px; overflow:hidden; }
  thead th { background:linear-gradient(90deg,#6a7fe0,#7a4fb0); color:#fff; text-align:left; padding:14px 16px; font-weight:600; letter-spacing:.04em; font-size:13px; }
  tbody td { padding:13px 16px; border-bottom:1px solid var(--line); }
  tbody tr:nth-child(even) { background:#f8f9fc; }
  td.name { font-weight:600; }
  td.pass { color:#1f9d55; font-weight:700; } td.fail { color:#d64545; font-weight:700; }
  code { background:#f1effc; color:#5b2fc9; padding:2px 6px; border-radius:4px; font-size:13px; }
  .tag { background:#eef0f7; border-radius:4px; padding:2px 8px; font-size:12px; color:#555; }
  .hint { color:var(--muted); font-size:13px; margin:8px 0 0; }
  .bar { display:inline-block; width:90px; height:8px; background:#fde2e1; border-radius:4px; margin-right:8px; vertical-align:middle; overflow:hidden; }
  .bar span { display:block; height:100%; background:#47b972; }
  table.kv td:first-child { width:260px; }
  footer { text-align:center; color:var(--muted); font-size:12px; padding:0 0 22px; }
  @media (max-width:640px) { header h1 { font-size:22px; } .panels { padding:6px 14px 18px; margin:0 12px 16px; } .cards { padding:16px 12px 4px; gap:14px; } .tabs { padding:14px 12px 0; } }
</style></head>
<body><div class="page">
<header>
  <div class="logo">k6</div>
  <div><h1>${esc(title)}</h1><div class="meta">${esc(testId)} &middot; ${when(now)} &middot; ${duration(runMs)}</div></div>
  <div class="verdict"${passed ? '' : ' style="background:#d9534f"'}>${passed ? 'PASSED' : 'FAILED'}</div>
</header>
<div class="cards">
  ${card('neutral', 'Total Requests', reqs, `${num(reqRate)} req/s`, ICONS.globe)}
  ${card(failedReqs ? 'bad' : 'good', 'Failed Requests', failedReqs, pct(failRate), failedReqs ? ICONS.cross : ICONS.check)}
  ${card(breached ? 'bad' : 'good', 'Breached Thresholds', breached, `of ${th.length}`, ICONS.warn)}
  ${card(checkFails ? 'bad' : 'good', 'Failed Checks', checkFails, `${checkPasses} passed`, ICONS.eye)}
</div>
<div class="tabs">
  <button class="tab active" data-tab="metrics">&#128202; Detailed Metrics</button>
  <button class="tab" data-tab="endpoints">&#128279; Endpoints</button>
  <button class="tab" data-tab="thresholds">&#9888; Thresholds</button>
  <button class="tab" data-tab="details">&#9201; Test Run Details</button>
  <button class="tab" data-tab="checks">&#9745; Checks &amp; Groups</button>
</div>
<div class="panels">
  <section class="panel active" id="metrics">
    <h2>&#128200; Trends &amp; Times</h2>
    ${table(['', 'AVG', 'MIN', 'MED', 'P(90)', 'P(95)', 'P(99)', 'MAX'], trendRows)}
    <p class="hint">Times in milliseconds.</p>
    <h2>&#128290; Rates, Counters &amp; Gauges</h2>
    ${table(['', 'TYPE', 'VALUE', 'DETAIL'], otherRows)}
  </section>
  <section class="panel" id="endpoints">
    <h2>&#128279; Response Time by Endpoint</h2>
    ${endpointRows.length ? table(['ENDPOINT', 'REQUESTS', 'AVG', 'MED', 'P(90)', 'P(95)', 'MAX', 'ERRORS'], endpointRows) : '<p class="hint">No per-endpoint data.</p>'}
  </section>
  <section class="panel" id="thresholds">
    <h2>&#9888; Thresholds (SLAs)</h2>
    ${table(['RESULT', 'METRIC', 'THRESHOLD', 'ACTUAL'], thresholdRows)}
  </section>
  <section class="panel" id="details">
    <h2>&#9201; Test Run Details</h2>
    ${table(null, details, 'kv')}
  </section>
  <section class="panel" id="checks">
    <h2>&#9745; Checks &amp; Groups</h2>
    ${groups.join('') || '<p class="hint">No checks.</p>'}
  </section>
</div>
<footer>Generated by k6 handleSummary &middot; src/utils/report.js</footer>
</div>
<script>
document.querySelectorAll('.tab').forEach(function (b) {
  b.addEventListener('click', function () {
    document.querySelectorAll('.tab').forEach(function (x) { x.classList.toggle('active', x === b); });
    document.querySelectorAll('.panel').forEach(function (p) { p.classList.toggle('active', p.id === b.dataset.tab); });
  });
});
</script>
</body></html>`;
}
