// НТ-2: нагрузка на WebSocket /ws/live — самая горячая точка системы.
//
// Поток VU: login → WS connect (token в query) → SUBSCRIBE_WORKSHOP →
// приём ALERT_SNAPSHOT / NOTIFICATION_SNAPSHOT / UNITS_STATUS / ALERT →
// закрытие по таймауту сессии (WS_SESSION_MS, дефолт 30 с).
//
// Метрики:
//   ws_snapshot_latency  — время до первого ALERT_SNAPSHOT (критерий: p95 < 500 мс)
//   ws_units_status_latency — время до первого UNITS_STATUS после подписки
//   ws_messages          — счётчик принятых сообщений по типам
//   ws_errors            — доля ошибок (parse error / обрыв / не 101)
//
// Запуск: k6 run load-tests/k6/ws-live.js

import { check } from 'k6';
import ws from 'k6/ws';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
    WS_BASE_URL,
    WORKSHOP_COUNT,
    WS_SESSION_MS,
    login,
    workerCodeForVu,
    stagesFromEnv,
} from './lib/common.js';

const snapshotLatency = new Trend('ws_snapshot_latency', true);
const unitsStatusLatency = new Trend('ws_units_status_latency', true);
const msgCounter = new Counter('ws_messages');
const errRate = new Rate('ws_errors');

export const options = {
    stages: stagesFromEnv([
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
    ]),
    thresholds: {
        ws_snapshot_latency: ['p(95)<500'],
        ws_errors: ['rate<0.001'], // < 0.1% по критериям эпика
    },
};

export default function () {
    const token = login(workerCodeForVu(__VU));
    if (!token) {
        errRate.add(1);
        return;
    }

    const workshopId = (__VU % WORKSHOP_COUNT) + 1;
    const url = `${WS_BASE_URL}/ws/live?token=${token}`;

    let t0 = 0;
    let gotSnapshot = false;
    let gotUnitsStatus = false;
    let closing = false;

    const res = ws.connect(url, {}, function (socket) {
        socket.on('open', () => {
            t0 = Date.now();
            socket.send(JSON.stringify({ action: 'SUBSCRIBE_WORKSHOP', workshopId }));
        });

        socket.on('message', (data) => {
            msgCounter.add(1);
            let msg;
            try {
                msg = JSON.parse(data);
            } catch {
                errRate.add(1);
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

        socket.on('error', () => errRate.add(1));

        socket.on('close', () => {
            // Обрыв до нашего закрытия — ошибка сессии
            if (!closing) {
                errRate.add(1);
            }
            // Не получили обязательные сообщения — тоже сбой сессии
            if (!gotSnapshot || !gotUnitsStatus) {
                errRate.add(1);
            }
        });

        socket.setTimeout(() => {
            closing = true;
            socket.close();
        }, WS_SESSION_MS);
    });

    check(res, { 'ws handshake 101': (r) => r && r.status === 101 });
    if (!res || res.status !== 101) {
        errRate.add(1);
    }
}
