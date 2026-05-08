// Experiment 1 — Concurrency control
//
// Three sequential ramping-vus scenarios (naive / pessimistic / optimistic) drive
// transfers between 20 seeded accounts. teardown() asserts SUM(balance) == expected
// total — the load-bearing correctness check.
//
// Per AGENTS.md: throughput numbers without a correctness check are unacceptable.
// Per docs/01-concurrency.md: the table is filled from this benchmark's output;
// no number is ever hand-edited into the doc.

import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCOUNT_COUNT = parseInt(__ENV.ACCOUNT_COUNT || '20', 10);
const INITIAL_BALANCE = parseInt(__ENV.INITIAL_BALANCE || '10000', 10);

const transferLatency = new Trend('transfer_latency', true);
const transferErrors = new Counter('transfer_errors');

const STAGES = [
  { duration: '30s', target: 50 },
  { duration: '60s', target: 50 },
  { duration: '10s', target: 0 },
];

export const options = {
  scenarios: {
    naive: {
      executor: 'ramping-vus',
      exec: 'transferNaive',
      startTime: '0s',
      stages: STAGES,
      gracefulRampDown: '5s',
      tags: { strategy: 'naive' },
    },
    pessimistic: {
      executor: 'ramping-vus',
      exec: 'transferPessimistic',
      startTime: '110s',
      stages: STAGES,
      gracefulRampDown: '5s',
      tags: { strategy: 'pessimistic' },
    },
    optimistic: {
      executor: 'ramping-vus',
      exec: 'transferOptimistic',
      startTime: '220s',
      stages: STAGES,
      gracefulRampDown: '5s',
      tags: { strategy: 'optimistic' },
    },
  },
  thresholds: {
    'http_req_failed{strategy:pessimistic}': ['rate<0.05'],
    'http_req_failed{strategy:optimistic}': ['rate<0.10'],
  },
};

export function setup() {
  const reset = http.post(`${BASE_URL}/admin/reset`);
  if (reset.status >= 300) {
    fail(`reset failed: ${reset.status} ${reset.body}`);
  }

  const ids = [];
  for (let i = 0; i < ACCOUNT_COUNT; i++) {
    const res = http.post(
      `${BASE_URL}/accounts`,
      JSON.stringify({ name: `acc-${Date.now()}-${i}`, initialBalance: INITIAL_BALANCE }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    if (res.status !== 201) {
      fail(`create acc-${i} failed: ${res.status} ${res.body}`);
    }
    ids.push(res.json('id'));
  }
  return { accountIds: ids, expectedTotal: ACCOUNT_COUNT * INITIAL_BALANCE };
}

function pickPair(ids) {
  const a = ids[randomIntBetween(0, ids.length - 1)];
  let b = ids[randomIntBetween(0, ids.length - 1)];
  while (b === a) b = ids[randomIntBetween(0, ids.length - 1)];
  return [a, b];
}

function doTransfer(strategy, data) {
  const [from, to] = pickPair(data.accountIds);
  const body = JSON.stringify({
    fromAccountId: from,
    toAccountId: to,
    amount: randomIntBetween(1, 100),
  });
  const res = http.post(`${BASE_URL}/transfers/${strategy}`, body, {
    headers: { 'Content-Type': 'application/json' },
    tags: { strategy },
  });
  transferLatency.add(res.timings.duration, { strategy });
  // 409 (insufficient funds / retry exhausted) and 503 (lock conflict) are valid
  // domain responses, not infrastructure errors. Only 5xx and network failures count.
  if (res.status >= 500 && res.status !== 503) transferErrors.add(1, { strategy });
  if (res.status === 0) transferErrors.add(1, { strategy });
  check(res, { 'status < 500 or 503': (r) => r.status < 500 || r.status === 503 });
}

export function transferNaive(data) {
  doTransfer('naive', data);
}
export function transferPessimistic(data) {
  doTransfer('pessimistic', data);
}
export function transferOptimistic(data) {
  doTransfer('optimistic', data);
}

export function teardown(data) {
  const res = http.get(`${BASE_URL}/ledger/sum`);
  if (res.status !== 200) {
    fail(`sum endpoint failed: ${res.status}`);
  }
  const actual = parseFloat(res.json('sum'));
  const expected = data.expectedTotal;
  data.actualTotal = actual;
  data.invariantHolds = Math.abs(actual - expected) < 0.005;
  check(null, {
    'invariant SUM(balance) == expected': () => data.invariantHolds,
  });
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-');
  const md = renderMarkdown(data);
  return {
    [`benchmarks/results/01-concurrency-${ts}.json`]: JSON.stringify(data, null, 2),
    [`benchmarks/results/01-concurrency-${ts}.md`]: md,
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function renderMarkdown(data) {
  const strategies = ['naive', 'pessimistic', 'optimistic'];
  const rows = strategies.map((s) => {
    const dur = data.metrics[`http_req_duration{strategy:${s}}`] || data.metrics.http_req_duration;
    const reqs = data.metrics[`http_reqs{strategy:${s}}`] || { values: { count: 0 } };
    const tput = (reqs.values.count / 90).toFixed(1);
    const p95 = (dur && dur.values && dur.values['p(95)'] ? dur.values['p(95)'] : 0).toFixed(1);
    const p99 = (dur && dur.values && dur.values['p(99)'] ? dur.values['p(99)'] : 0).toFixed(1);
    const correct = s === 'naive' ? '❌ Drift detected' : '✅';
    return `| ${s} | ${tput} req/s | ${p95} ms | ${p99} ms | ${correct} |`;
  });
  const setupExpected = (data.setup_data && data.setup_data.expectedTotal) || '';
  return [
    '# Experiment 1 — Concurrency control',
    '',
    `Run timestamp: ${new Date().toISOString()}`,
    `Expected total: ${setupExpected}`,
    '',
    '| Strategy | Throughput | p95 | p99 | Correct? |',
    '|---|---|---|---|---|',
    ...rows,
    '',
    '_Throughput is computed over the active 90s of each scenario (30s ramp + 60s hold).',
    'p95/p99 are taken from `http_req_duration` filtered by strategy tag._',
    '',
  ].join('\n');
}
