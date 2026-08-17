// Общая конфигурация и хелперы для k6-скриптов (эпик #51, НТ-2).
//
// Все параметры переопределяются через env — так те же скрипты работают
// и локально (дефолты = стенд make load-up), и на сервере:
//   k6 run -e BASE_URL=http://server:8081 load-tests/k6/ws-live.js
//
// STAGES — JSON-строка для профиля нагрузки, напр.:
//   k6 run -e STAGES='[{"duration":"1m","target":100}]' load-tests/k6/ws-live.js

import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
export const API_URL = __ENV.API_URL || `${BASE_URL}/api/v1.0.0`;
export const WS_BASE_URL = __ENV.WS_URL || BASE_URL.replace(/^http/, 'ws');

// Диапазон нагрузочных пользователей (сид scripts/seed_loadtest_users.sql)
export const USER_FIRST = parseInt(__ENV.LOAD_USER_FIRST || '20001', 10);
export const USER_LAST = parseInt(__ENV.LOAD_USER_LAST || '20500', 10);
export const USER_PASSWORD = __ENV.LOAD_USER_PASSWORD || 'password';

// Число автоматов в сиде (units 1..14)
export const UNIT_COUNT = parseInt(__ENV.LOAD_UNIT_COUNT || '14', 10);
// PrintSrv instance IDs автоматов из сида — канал /ws/unit/{id} принимает
// именно их (строки вида 'trepko1'), а НЕ числовые unit_id.
export const UNIT_INSTANCE_IDS = __ENV.LOAD_UNIT_IDS
    ? JSON.parse(__ENV.LOAD_UNIT_IDS)
    : [
        'trepko1', 'trepko2',
        'hassia1', 'hassia2', 'hassia3', 'hassia4', 'hassia5', 'hassia6',
        'bosch',
        'grunwald1', 'grunwald2', 'grunwald5', 'grunwald8', 'grunwald11',
    ];
// Число цехов (workshops 1..2)
export const WORKSHOP_COUNT = parseInt(__ENV.LOAD_WORKSHOP_COUNT || '2', 10);

// Длительность WS-сессии одной итерации (мс)
export const WS_SESSION_MS = parseInt(__ENV.WS_SESSION_MS || '30000', 10);

export function stagesFromEnv(defaultStages) {
    return __ENV.STAGES ? JSON.parse(__ENV.STAGES) : defaultStages;
}

// Детерминированный worker code для VU: равномерно по диапазону 20001..20500
export function workerCodeForVu(vu) {
    const span = USER_LAST - USER_FIRST + 1;
    return String(USER_FIRST + ((vu - 1) % span));
}

// Логин: POST /auth/login → accessToken. Падает check'ом при неуспехе.
export function login(workerCode) {
    const res = http.post(
        `${API_URL}/auth/login`,
        JSON.stringify({ workerCode, password: USER_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } }
    );
    const ok = check(res, {
        'login 200': (r) => r.status === 200,
        'login has accessToken': (r) => {
            try {
                return !!r.json('accessToken');
            } catch {
                return false;
            }
        },
    });
    if (!ok) {
        return null;
    }
    return res.json('accessToken');
}
