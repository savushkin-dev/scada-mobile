// НТ-2: нагрузка на аутентификацию — POST /api/v1.0.0/auth/login.
//
// ВНИМАНИЕ: логин — CPU-bound из-за BCrypt (сила 10). Это осознанно:
// НТ-5 (#66) отдельно проверяет массовый login (50 логинов/сек).
// Поэтому threshold здесь мягче, чем общий REST p95 < 200 мс.
//
// Запуск: k6 run load-tests/k6/auth-login.js
//   k6 run -e STAGES='[{"duration":"1m","target":50}]' load-tests/k6/auth-login.js

import { sleep } from 'k6';
import { login, workerCodeForVu, stagesFromEnv } from './lib/common.js';

export const options = {
    stages: stagesFromEnv([
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
    ]),
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{endpoint:login}': ['p(95)<800'],
    },
};

export default function () {
    login(workerCodeForVu(__VU));
    sleep(1);
}
