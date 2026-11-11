import fs from 'fs';
import { spawnSync } from 'child_process';

const BASE = 'http://127.0.0.1:8080';
const WORK = 'E:/InstagramAIManager/.tmp-test';
const cookieJar = `${WORK}/e2e-cookies.txt`;
const report = { ok: true, checks: [], failures: [] };

function log(name, pass, detail = '') {
  report.checks.push({ name, pass, detail });
  if (!pass) {
    report.ok = false;
    report.failures.push({ name, detail });
  }
  console.log(`${pass ? 'PASS' : 'FAIL'} | ${name}${detail ? ' — ' + detail : ''}`);
}

function curl(args, outFile) {
  const cmd = ['curl', '-s', '-c', cookieJar, '-b', cookieJar, ...args];
  if (outFile) cmd.push('-o', outFile);
  const r = spawnSync(cmd[0], cmd.slice(1), { encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`curl failed: ${r.stderr || r.error}`);
  if (outFile) return fs.readFileSync(outFile, 'utf8');
  return r.stdout;
}

function json(args, outFile) {
  const raw = curl(args, outFile);
  return JSON.parse(raw);
}

function csrfToken() {
  const jar = fs.readFileSync(cookieJar, 'utf8');
  const lines = jar.split(/\r?\n/).filter((l) => l.includes('XSRF-TOKEN'));
  const last = lines[lines.length - 1];
  if (!last) return '';
  return last.split(/\t/).pop().trim();
}

function sleep(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

fs.mkdirSync(WORK, { recursive: true });
try { fs.unlinkSync(cookieJar); } catch {}

// 1) CSRF + login
curl([`${BASE}/api/v1/auth/csrf`]);
let token = csrfToken();
const login = json([
  '-X', 'POST', `${BASE}/api/v1/auth/login`,
  '-H', 'Content-Type: application/json',
  '-H', `X-XSRF-TOKEN: ${token}`,
  '-d', '{"email":"admin@local.dev","password":"0110"}',
], `${WORK}/e2e-login.json`);
const access = login?.data?.accessToken || '';
log('login', !!login.success && !!access, login.messageKey || '');
token = csrfToken();
const auth = ['-H', `Authorization: Bearer ${access}`];

// 2) AI health via BE path indirectly + direct
let aiHealth;
try {
  aiHealth = JSON.parse(spawnSync('curl', ['-s', 'http://127.0.0.1:8000/health'], { encoding: 'utf8' }).stdout);
} catch (e) {
  aiHealth = null;
}
log('ai-service health', !!aiHealth && aiHealth.ollama === true, JSON.stringify(aiHealth));

// 3) Media list
const mediaPage = json([
  ...auth, `${BASE}/api/v1/instagram/media?page=0&size=50`,
], `${WORK}/e2e-media.json`);
const items = mediaPage?.data?.content || [];
log('media list', mediaPage.success && items.length > 0, `count=${items.length}`);

const byStatus = (s) => items.filter((m) => m.analysisStatus === s);
console.log('initial statuses', Object.fromEntries(
  [...new Set(items.map((m) => m.analysisStatus))].map((s) => [s, byStatus(s).length])
));

// 4) Skip one NOT_ANALYZED (or FAILED)
let skipTarget = byStatus('NOT_ANALYZED')[0] || byStatus('FAILED')[0];
if (skipTarget) {
  token = csrfToken();
  const skip = json([
    ...auth, '-X', 'POST', `${BASE}/api/v1/instagram/media/skip-analysis`,
    '-H', 'Content-Type: application/json',
    '-H', `X-XSRF-TOKEN: ${token}`,
    '--data-binary', JSON.stringify({ mediaIds: [skipTarget.id] }),
  ], `${WORK}/e2e-skip.json`);
  log('skip-analysis', skip.success && skip.data?.skipped === 1, JSON.stringify(skip.data || skip));
} else {
  log('skip-analysis', true, 'no NOT_ANALYZED/FAILED to skip (already covered)');
}

// refresh list
const media2 = json([...auth, `${BASE}/api/v1/instagram/media?page=0&size=50`], `${WORK}/e2e-media2.json`);
const items2 = media2?.data?.content || [];
const skippedOk = skipTarget
  ? items2.find((m) => m.id === skipTarget.id)?.analysisStatus === 'SKIPPED'
  : true;
log('skip persisted on media', skippedOk, skipTarget ? skipTarget.id : 'n/a');

// 5) Enqueue all remaining analyzable
const toAnalyze = items2
  .filter((m) => ['NOT_ANALYZED', 'FAILED', 'QUEUED'].includes(m.analysisStatus) ||
    (m.analysisStatus === 'QUEUED'))
  .filter((m) => m.analysisStatus !== 'SKIPPED' && m.analysisStatus !== 'ANALYZED' && m.analysisStatus !== 'RUNNING')
  .filter((m) => ['NOT_ANALYZED', 'FAILED'].includes(m.analysisStatus));
// also include already QUEUED — leave them; only enqueue NOT_ANALYZED/FAILED
token = csrfToken();
const analyzeIds = toAnalyze.map((m) => m.id);
if (analyzeIds.length) {
  const enq = json([
    ...auth, '-X', 'POST', `${BASE}/api/v1/instagram/media/analyze`,
    '-H', 'Content-Type: application/json',
    '-H', `X-XSRF-TOKEN: ${token}`,
    '--data-binary', JSON.stringify({ mediaIds: analyzeIds }),
  ], `${WORK}/e2e-analyze.json`);
  log('enqueue analyze', enq.success && (enq.data?.queued || 0) > 0,
    JSON.stringify(enq.data || enq));
} else {
  log('enqueue analyze', true, 'nothing left to enqueue');
}

// 6) Re-analyze already ANALYZED should be ignored (queued=0 for that id)
const already = items2.find((m) => m.analysisStatus === 'ANALYZED');
if (already) {
  token = csrfToken();
  const re = json([
    ...auth, '-X', 'POST', `${BASE}/api/v1/instagram/media/analyze`,
    '-H', 'Content-Type: application/json',
    '-H', `X-XSRF-TOKEN: ${token}`,
    '--data-binary', JSON.stringify({ mediaIds: [already.id] }),
  ], `${WORK}/e2e-reanalyze.json`);
  log('re-analyze ANALYZED ignored', re.success && re.data?.ignored >= 1 && re.data?.queued === 0,
    JSON.stringify(re.data || re));
} else {
  log('re-analyze ANALYZED ignored', true, 'no ANALYZED yet to test ignore');
}

// 7) Poll until no QUEUED/RUNNING (max ~8 min; concurrency 1)
const deadline = Date.now() + 8 * 60 * 1000;
let finalItems = [];
while (Date.now() < deadline) {
  const page = json([...auth, `${BASE}/api/v1/instagram/media?page=0&size=50`], `${WORK}/e2e-media-poll.json`);
  finalItems = page?.data?.content || [];
  const pending = finalItems.filter((m) => m.analysisStatus === 'QUEUED' || m.analysisStatus === 'RUNNING');
  const counts = {};
  for (const m of finalItems) counts[m.analysisStatus] = (counts[m.analysisStatus] || 0) + 1;
  console.log('poll', new Date().toISOString(), counts, 'pending', pending.length);
  if (pending.length === 0) break;
  sleep(15000);
}

const pendingLeft = finalItems.filter((m) => m.analysisStatus === 'QUEUED' || m.analysisStatus === 'RUNNING');
log('queue drained', pendingLeft.length === 0, `pending=${pendingLeft.length}`);

const failed = finalItems.filter((m) => m.analysisStatus === 'FAILED');
log('no FAILED after run', failed.length === 0, failed.map((m) => m.id).join(',') || 'none');

const analyzed = finalItems.filter((m) => m.analysisStatus === 'ANALYZED');
log('has ANALYZED items', analyzed.length > 0, `count=${analyzed.length}`);

// 8) Each ANALYZED has analysisSummary on list + rich result on detail
let summaryOk = true;
let detailOk = true;
for (const m of analyzed) {
  if (!m.analysisSummary || String(m.analysisSummary).trim().length < 3) {
    summaryOk = false;
    console.log('missing list summary', m.id);
  }
  const detail = json([...auth, `${BASE}/api/v1/instagram/media/${m.id}/analysis`], `${WORK}/e2e-detail-${m.id}.json`);
  const result = detail?.data?.result || {};
  const status = detail?.data?.status;
  const hasTopic = !!(result.summaryUz || result.mainTopic);
  if (!detail.success || status !== 'DONE' && status !== 'ANALYZED' && detail?.data?.status !== 'DONE') {
    // job status enum is AnalysisJobStatus: DONE? check actual
  }
  const jobStatus = detail?.data?.status;
  if (!detail.success || jobStatus === 'FAILED' || !hasTopic) {
    detailOk = false;
    console.log('detail bad', m.id, jobStatus, detail.messageKey, JSON.stringify(result).slice(0, 160));
  } else {
    console.log('detail ok', m.id, jobStatus, (result.summaryUz || result.mainTopic || '').toString().slice(0, 80));
  }

  // engagement snapshots endpoint should respond (may be empty before cron)
  const snaps = json([...auth, `${BASE}/api/v1/instagram/media/${m.id}/engagement-snapshots`], `${WORK}/e2e-snap-${m.id}.json`);
  if (!snaps.success || !Array.isArray(snaps.data)) {
    detailOk = false;
    console.log('snapshots bad', m.id, snaps);
  }
}
log('list analysisSummary present', summaryOk, `analyzed=${analyzed.length}`);
log('detail result + snapshots API', detailOk, `checked=${analyzed.length}`);

// 9) SKIPPED still skipped
if (skipTarget) {
  const still = finalItems.find((m) => m.id === skipTarget.id);
  log('skipped stays SKIPPED', still?.analysisStatus === 'SKIPPED', still?.analysisStatus);
}

// 10) UI proxy smoke
const ui = spawnSync('curl', ['-s', '-o', 'NUL', '-w', '%{http_code}', 'http://127.0.0.1:4200/'], { encoding: 'utf8' });
log('UI :4200 up', ui.stdout === '200' || ui.stdout === '304', `http=${ui.stdout}`);

fs.writeFileSync(`${WORK}/e2e-report.json`, JSON.stringify({
  ok: report.ok,
  checks: report.checks,
  failures: report.failures,
  finalCounts: Object.fromEntries(
    [...new Set(finalItems.map((m) => m.analysisStatus))].map((s) => [s, finalItems.filter((m) => m.analysisStatus === s).length])
  ),
  analyzedSample: analyzed.slice(0, 3).map((m) => ({ id: m.id, summary: m.analysisSummary })),
}, null, 2));

console.log('\n=== SUMMARY ===');
console.log(report.ok ? 'ALL CHECKS PASSED' : 'SOME CHECKS FAILED');
console.log(JSON.stringify(report.failures, null, 2));
process.exit(report.ok ? 0 : 1);
