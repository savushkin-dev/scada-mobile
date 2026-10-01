// НТ-4 (#64): комбинированный production-like тест — главный сценарий эпика #51.
// Эмуляция реального распределения пользователей:
//   70% — смотрят цех через /ws/live (live_watchers)
//   20% — открывают детали автомата /ws/unit/{instanceId} (unit_viewers)
//   8%  — листают топологию по REST с ETag (topology_browsers)
//   2%  — логинятся / обновляют токен (auth_users)
//
// Профиль по умолчанию (stress + breakpoint): 0 → 100 → 300 → 500 пользователей.
// Критерии приёмки (#51): REST p95 < 200 мс; WS initial snapshot p95 < 500 мс;
// WS ошибки < 0.1%; ≥ 300 одновременных сессий.
//
// Запуск:
//   make load-k6 SCRIPT=load-tests/k6/combined.js
//   make load-k6 SCRIPT=load-tests/k6/combined.js 'STAGES=[{"duration":"2m","target":100}]'
// Smoke-вариант (НТ-8): make load-smoke

import { check, sleep } from 'k6';
import http from 'k6/http';
import ws from 'k6/ws';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
    API_URL,
    WS_BASE_URL,
    WORKSHOP_COUNT,
    UNIT_INSTANCE_IDS,
    WS_SESSION_MS,
    login,
    loginFull,
    refreshTokens,
    workerCodeForVu,
    stagesFromEnv,
} from './lib/common.js';

// ─── Метрики ────────────────────────────────────────────────────────────────

const snapshotLatency = new Trend('ws_snapshot_latency', true);
const unitsStatusLatency = new Trend('ws_units_status_latency', true);
const unitPackageLatency = new Trend('ws_unit_initial_package', true);
const wsErrors = new Rate('ws_errors');
const wsSessions = new Counter('ws_sessions_total');

// ─── Профиль нагрузки и распределение ───────────────────────────────────────

const RATIOS = { live: 0.70, unit: 0.20, topology: 0.08, auth: 0.02 };

// Stress + breakpoint: плато 100 (штатная нагрузка) → 300 (критерий приёмки) →
// 500 (поиск точки отказа). Когда пороги краснеют — точка найдена.
const DEFAULT_PROFILE = [
    { duration: '2m', target: 100 },
    { duration: '3m', target: 100 },
    { duration: '2m', target: 300 },
    { duration: '5m', target: 300 },
    { duration: '2m', target: 500 },
    { duration: '3m', target: 500 },
    { duration: '2m', target: 0 },
];

function scaledStages(ratio) {
    return stagesFromEnv(DEFAULT_PROFILE).map((s) => ({
        duration: s.duration,
        // при ненулевом target даём сценарию минимум 1 VU, чтобы каждый путь работал
        target: s.target > 0 ? Math.max(1, Math.round(s.target * ratio)) : 0,
    }));
}

export const options = {
    scenarios: {
        live_watchers: {
            executor: 'ramping-vus',
            exec: 'liveWatcher',
            stages: scaledStages(RATIOS.live),
            gracefulRampDown: '30s',
        },
        unit_viewers: {
            executor: 'ramping-vus',
            exec: 'unitViewer',
            stages: scaledStages(RATIOS.unit),
            gracefulRampDown: '30s',
        },
        topology_browsers: {
            executor: 'ramping-vus',
            exec: 'topologyBrowser',
            stages: scaledStages(RATIOS.topology),
            gracefulRampDown: '30s',
        },
        auth_users: {
            executor: 'ramping-vus',
            exec: 'authUser',
            stages: scaledStages(RATIOS.auth),
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        'http_req_duration{endpoint:topology}': ['p(95)<200'],
        'http_req_duration{endpoint:login}': ['p(95)<800'],
        'http_req_duration{endpoint:refresh}': ['p(95)<800'],
        http_req_failed: ['rate<0.01'],
        ws_snapshot_latency: ['p(95)<500'],
        ws_unit_initial_package: ['p(95)<500'],
        ws_errors: ['rate<0.001'],
        checks: ['rate>0.99'],
    },
};

// ─── 70%: наблюдатели цеха (/ws/live) ───────────────────────────────────────

export function liveWatcher() {
    const token = login(workerCodeForVu(__VU));
    if (!token) {
        wsErrors.add(1);
        return;
    }

    const workshopId = (__VU % WORKSHOP_COUNT) + 1;
    const url = `${WS_BASE_URL}/ws/live?token=${token}`;

    let t0 = 0;
    let gotSnapshot = false;
    let gotUnitsStatus = false;
    let closing = false;

    const res = ws.connect(url, { tags: { channel: 'live' } }, function (socket) {
        socket.on('open', () => {
            wsSessions.add(1);
            t0 = Date.now();
            socket.send(JSON.stringify({ action: 'SUBSCRIBE_WORKSHOP', workshopId }));
        });

        socket.on('message', (data) => {
            let msg;
            try {
                msg = JSON.parse(data);
            } catch {
                wsErrors.add(1);
                return;
            }
            if (msg.type === 'ALERT_SNAPSHOT' && !gotSnapshot) {
                gotSnapshot = true;
                snapshotLatency.add(Date.now() - t0);
            } else if (msg.type === 'UNITS_STATUS' && !gotUnitsStatus) {
                gotUnitsStatus = true;
                unitsStatusLatency.add(Date.now() - t0);
            }
        });

        socket.on('error', () => wsErrors.add(1));
        socket.on('close', () => {
            if (!closing || !gotSnapshot || !gotUnitsStatus) {
                wsErrors.add(1);
            }
        });

        socket.setTimeout(() => {
            closing = true;
            socket.close();
        }, WS_SESSION_MS);
    });

    check(res, { 'ws/live handshake 101': (r) => r && r.status === 101 });
    if (!res || res.status !== 101) {
        wsErrors.add(1);
    }
}

// ─── 20%: детали автомата (/ws/unit/{instanceId}) ───────────────────────────

const UNIT_EXPECTED = ['LINE_STATUS', 'DEVICES_STATUS', 'QUEUE', 'ERRORS'];

export function unitViewer() {
    const token = login(workerCodeForVu(__VU));
    if (!token) {
        wsErrors.add(1);
        return;
    }

    const unitId = UNIT_INSTANCE_IDS[(__VU - 1) % UNIT_INSTANCE_IDS.length];
    const url = `${WS_BASE_URL}/ws/unit/${unitId}?token=${token}`;

    let t0 = 0;
    let closing = false;
    const received = new Set();

    const res = ws.connect(url, { tags: { channel: 'unit' } }, function (socket) {
        socket.on('open', () => {
            wsSessions.add(1);
            t0 = Date.now();
        });

        socket.on('message', (data) => {
            let msg;
            try {
                msg = JSON.parse(data);
            } catch {
                wsErrors.add(1);
                return;
            }
            if (UNIT_EXPECTED.includes(msg.type) && received.size < UNIT_EXPECTED.length) {
                received.add(msg.type);
                if (received.size === UNIT_EXPECTED.length) {
                    unitPackageLatency.add(Date.now() - t0);
                }
            }
        });

        socket.on('error', () => wsErrors.add(1));
        socket.on('close', () => {
            if (!closing || received.size !== UNIT_EXPECTED.length) {
                wsErrors.add(1);
            }
        });

        socket.setTimeout(() => {
            closing = true;
            socket.close();
        }, WS_SESSION_MS);
    });

    check(res, { 'ws/unit handshake 101': (r) => r && r.status === 101 });
    if (!res || res.status !== 101) {
        wsErrors.add(1);
    }
}

// ─── 8%: браузеры топологии (REST + ETag) ───────────────────────────────────

let topologyToken = null;
let topologyEtag = null;

export function topologyBrowser() {
    if (!topologyToken) {
        topologyToken = login(workerCodeForVu(__VU));
        if (!topologyToken) {
            sleep(1);
            return;
        }
        // Холодный прогрев (JIT, кэши) — тегируем отдельно, чтобы первый
        // медленный ответ не ломал p95 топологии на коротких прогонах.
        const warm = http.get(`${API_URL}/workshops/topology`, {
            headers: { Authorization: `Bearer ${topologyToken}` },
            tags: { endpoint: 'warmup' },
        });
        if (warm.status === 200) {
            topologyEtag = warm.headers['ETag'] || warm.headers['Etag'] || topologyEtag;
        }
    }

    const headers = { Authorization: `Bearer ${topologyToken}` };
    if (topologyEtag) {
        headers['If-None-Match'] = topologyEtag;
    }

    const res = http.get(`${API_URL}/workshops/topology`, {
        headers,
        tags: { endpoint: 'topology' },
    });

    if (res.status === 200) {
        check(res, {
            'topology 200 has ETag': (r) => !!(r.headers['ETag'] || r.headers['Etag']),
        });
        topologyEtag = res.headers['ETag'] || res.headers['Etag'] || topologyEtag;
    } else {
        check(res, { 'topology 304 (ETag match)': (r) => r.status === 304 });
        if (res.status === 401) {
            topologyToken = null;
        }
    }

    sleep(2);
}

// ─── 2%: аутентификация (login + refresh) ───────────────────────────────────

export function authUser() {
    const body = loginFull(workerCodeForVu(__VU));
    if (!body) {
        sleep(1);
        return;
    }

    sleep(1);

    // Ротация токена — типичное поведение живого клиента
    refreshTokens(body.refreshToken);

    sleep(3);
}
