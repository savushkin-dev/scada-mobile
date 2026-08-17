// НТ-2: нагрузка на REST — GET /api/v1.0.0/workshops/topology с ETag-кэшем.
//
// Поток VU имитирует реального клиента: первый запрос без If-None-Match
// (200 + ETag), дальнейшие — с If-None-Match (304 без тела).
// Критерий приёмки эпика: REST p95 < 200 мс.
//
// Запуск: k6 run load-tests/k6/rest-topology.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { API_URL, login, workerCodeForVu, stagesFromEnv } from './lib/common.js';

export const options = {
    stages: stagesFromEnv([
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 },
    ]),
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{endpoint:topology}': ['p(95)<200'],
    },
};

// Состояние VU между итерациями (k6 хранит module-scope per VU)
let token = null;
let etag = null;

export default function () {
    if (!token) {
        token = login(workerCodeForVu(__VU));
        if (!token) {
            sleep(1);
            return;
        }
    }

    const headers = { Authorization: `Bearer ${token}` };
    if (etag) {
        headers['If-None-Match'] = etag;
    }

    const res = http.get(`${API_URL}/workshops/topology`, {
        headers,
        tags: { endpoint: 'topology' },
    });

    if (res.status === 200) {
        check(res, {
            'topology 200 has ETag': (r) => !!(r.headers['ETag'] || r.headers['Etag']),
            'topology 200 has workshops': (r) => {
                try {
                    return Array.isArray(r.json()) || typeof r.json() === 'object';
                } catch {
                    return false;
                }
            },
        });
        etag = res.headers['ETag'] || res.headers['Etag'] || etag;
    } else {
        check(res, { 'topology 304 (ETag match)': (r) => r.status === 304 });
        // 401/др. — токен протух или сервер упал: сбрасываем и перелогинимся
        if (res.status === 401) {
            token = null;
        }
    }

    sleep(1);
}
