/**
 * SCADA Mobile — точка входа приложения.
 *
 * Отвечает за:
 * - регистрацию Service Worker (PWA)
 * - ручной запрос состояния SCADA по кнопке
 * - координацию api.js ↔ ui.js
 * - online/offline-статус
 * - install prompt
 */

import { fetchSnapshot, sendSetUnitVars, ApiException } from "./api.js";
import {
  setOnlineStatus,
  setBackendStatus,
  setLastUpdated,
  setRefreshLoading,
  renderUnits,
  showToast,
  setInstallPromptVisible,
  onInstallClick,
} from "./ui.js";

// ─── Константы ───────────────────────────────────────────────────────────────

const APP_VERSION     = "2.0.0";
const SW_PATH         = "./service-worker.js";

// ─── Service Worker ───────────────────────────────────────────────────────────

function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;

  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register(SW_PATH, { scope: "./" })
      .then((reg) => {
        console.log("[SW] Registered, scope:", reg.scope);
      })
      .catch((err) => {
        console.error("[SW] Registration failed:", err);
      });
  });
}

// ─── Online / Offline ────────────────────────────────────────────────────────

function initOnlineStatus() {
  const update = () => setOnlineStatus(navigator.onLine);
  window.addEventListener("online",  update);
  window.addEventListener("offline", update);
  update();
}

// ─── Polling ─────────────────────────────────────────────────────────────────
// Ручная загрузка snapshot по кнопке
async function loadSnapshot() {
  setRefreshLoading(true);
  setBackendStatus("connecting");

  try {
    const snapshot = await fetchSnapshot();
    renderUnits(snapshot, handleSetValue);
    setLastUpdated();
    setBackendStatus("ready");
  } catch (err) {
    const msg = err instanceof ApiException ? err.message : "Неизвестная ошибка";
    setBackendStatus("error", msg);
    showToast(`Ошибка чтения: ${msg}`, "error");
    console.warn("[loadSnapshot] Error:", err);
  } finally {
    setRefreshLoading(false);
  }
}

// ─── Обработка команды установки значения ────────────────────────────────────

/**
 * @param {number} unitIndex — 1-based номер unit
 * @param {number} value
 */
async function handleSetValue(unitIndex, value) {
  try {
    await sendSetUnitVars(unitIndex, value);
    showToast(`u${unitIndex}: значение ${value} принято бекендом`, "success");
  } catch (err) {
    const msg = err instanceof ApiException ? err.message : "Ошибка отправки";
    showToast(`Ошибка: ${msg}`, "error");
    console.error("[handleSetValue]", err);
  }
}

// ─── Кнопка ручного обновления ───────────────────────────────────────────────

function initRefreshButton() {
  const btn = document.getElementById("refresh-btn");
  if (!btn) return;

  btn.addEventListener("click", loadSnapshot);
}

// ─── PWA Install Prompt ──────────────────────────────────────────────────────

let _deferredInstallPrompt = null;

function initInstallPrompt() {
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    _deferredInstallPrompt = e;
    setInstallPromptVisible(true);
  });

  onInstallClick(async () => {
    if (!_deferredInstallPrompt) return;
    _deferredInstallPrompt.prompt();
    const { outcome } = await _deferredInstallPrompt.userChoice;
    if (outcome === "accepted") {
      showToast("Приложение установлено!", "success");
    }
    _deferredInstallPrompt = null;
    setInstallPromptVisible(false);
  });

  window.addEventListener("appinstalled", () => {
    _deferredInstallPrompt = null;
    setInstallPromptVisible(false);
  });
}

// ─── Старт приложения ────────────────────────────────────────────────────────

async function init() {
  console.log(`SCADA Mobile v${APP_VERSION}`);

  registerServiceWorker();
  initOnlineStatus();
  initInstallPrompt();
  initRefreshButton();

  // Без авто-запросов: пользователь сам инициирует чтение кнопкой
  setBackendStatus("connecting");
  setRefreshLoading(false);
  showToast("Нажмите «Получить значения», чтобы запросить данные с сервера", "info", 5000);
}

init();
