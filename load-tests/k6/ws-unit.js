// НТ-2: нагрузка на WebSocket /ws/unit/{instanceId} — экран деталей автомата.
//
// ВАЖНО: {instanceId} — это PrintSrv instance id ('trepko1', ...), а не
// числовой unit_id из БД: неизвестный id закрывается сервером с кодом 1007.
//
// Поток VU: login → WS connect к /ws/unit/{instanceId} (равномерно по 14 автоматам) →
// приём начального пакета из 4 сообщений (LINE_STATUS, DEVICES_STATUS, QUEUE, ERRORS) →
// приём push-обновлений → закрытие по таймауту сессии.
//
// Метрики:
//   ws_unit_initial_package — время до полного пакета из 4 сообщений (p95 < 500 мс)
//   ws_unit_messages        — счётчик принятых сообщений
//   ws_unit_errors          — доля ошибок
//
// Запуск: k6 run load-tests/k6/ws-unit.js

import { check } from 'k6';
import ws from 'k6/ws';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
    WS_BASE_URL,
    UNIT_INSTANCE_IDS,
    WS_SESSION_MS,
    login,
    workerCodeForVu,
    stagesFromEnv,
} from './lib/common.js';

const initialPackageLatency = new Trend('ws_unit_initial_package', true);
const msgCounter = new Counter('ws_unit_messages');
const errRate = new Rate('ws_unit_errors');

const EXPECTED_TYPES = ['LINE_STATUS', 'DEVICES_STATUS', 'QUEUE', 'ERRORS'];

export const options = {
    stages: stagesFromEnv([
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
    ]),
    thresholds: {
        ws_unit_initial_package: ['p(95)<500'],
        ws_unit_errors: ['rate<0.001'],
    },
};

export default function () {
    const token = login(workerCodeForVu(__VU));
    if (!token) {
        errRate.add(1);
        return;
    }

    const unitId = UNIT_INSTANCE_IDS[(__VU - 1) % UNIT_INSTANCE_IDS.length];
    const url = `${WS_BASE_URL}/ws/unit/${unitId}?token=${token}`;

    let t0 = 0;
    let closing = false;
    const received = new Set();

    const res = ws.connect(url, {}, function (socket) {
        socket.on('open', () => {
            t0 = Date.now();
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
            if (EXPECTED_TYPES.includes(msg.type) && received.size < EXPECTED_TYPES.length) {
                received.add(msg.type);
                if (received.size === EXPECTED_TYPES.length) {
                    initialPackageLatency.add(Date.now() - t0);
                }
            }
        });

        socket.on('error', () => errRate.add(1));

        socket.on('close', () => {
            if (!closing) {
                errRate.add(1);
            }
            if (received.size !== EXPECTED_TYPES.length) {
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
