/**
 * SCADA Mobile — UI-слой.
 *
 * Отвечает исключительно за манипуляции с DOM.
 * Не знает об API, polling-интервалах или бизнес-логике.
 */

// ─── Элементы DOM ────────────────────────────────────────────────────────────

const $ = (id) => document.getElementById(id);

const els = {
  unitsGrid:       () => $("units-grid"),
  emptyState:      () => $("empty-state"),
  toastContainer:  () => $("toast-container"),
  lastUpdated:     () => $("last-updated"),
  refreshBtn:      () => $("refresh-btn"),
  onlineIndicator: () => $("online-indicator"),
  onlineText:      () => $("online-text"),
  backendStatus:   () => $("backend-status"),
  installPrompt:   () => $("install-prompt"),
  installBtn:      () => $("install-btn"),
};

// ─── Онлайн-статус ───────────────────────────────────────────────────────────

/**
 * @param {boolean} online
 */
export function setOnlineStatus(online) {
  const ind  = els.onlineIndicator();
  const text = els.onlineText();
  if (!ind || !text) return;

  ind.className = `status-dot ${online ? "status-dot--online" : "status-dot--offline"}`;
  text.textContent = online ? "Онлайн" : "Оффлайн";
}

/**
 * @param {"connecting"|"ready"|"error"} state
 * @param {string} [detail]
 */
export function setBackendStatus(state, detail = "") {
  const el = els.backendStatus();
  if (!el) return;

  const map = {
    connecting: { text: "Готов к запросу", cls: "backend-status--connecting" },
    ready:      { text: "Бекенд: готов",           cls: "backend-status--ready" },
    error:      { text: `Бекенд недоступен${detail ? ": " + detail : ""}`, cls: "backend-status--error" },
  };

  const cfg = map[state] ?? map.error;
  el.textContent = cfg.text;
  el.className = `backend-status ${cfg.cls}`;
}

// ─── Таймштамп последнего обновления ─────────────────────────────────────────

export function setLastUpdated() {
  const el = els.lastUpdated();
  if (!el) return;
  const now = new Date();
  el.textContent = `Обновлено: ${now.toLocaleTimeString("ru-RU")}`;
}

// ─── Кнопка обновления ───────────────────────────────────────────────────────

/**
 * @param {boolean} loading
 */
export function setRefreshLoading(loading) {
  const btn = els.refreshBtn();
  if (!btn) return;
  btn.disabled = loading;
  btn.setAttribute("aria-busy", String(loading));
  btn.textContent = loading ? "Запрос…" : "Получить значения";
}

// ─── Отрисовка units ─────────────────────────────────────────────────────────

/**
 * Отрисовать список units из snapshot.
 * @param {import('./api.js').QueryStateResponse} snapshot
 * @param {function(unitKey: string, value: number): void} onSetValue
 */
export function renderUnits(snapshot, onSetValue) {
  const grid  = els.unitsGrid();
  const empty = els.emptyState();
  if (!grid || !empty) return;

  const entries = Object.entries(snapshot.Units ?? {});

  if (entries.length === 0) {
    grid.innerHTML = "";
    empty.hidden = false;
    return;
  }

  empty.hidden = true;

  // Строим карточки всех units сразу (один reflow)
  grid.innerHTML = entries
    .sort(([a], [b]) => a.localeCompare(b, "ru", { numeric: true }))
    .map(([key, unit]) => buildUnitCard(key, unit))
    .join("");

  // Навешиваем обработчики форм после вставки
  grid.querySelectorAll(".unit-card__form").forEach((form) => {
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const unitKey = form.dataset.unit;
      const input   = form.querySelector(".unit-card__input");
      const submit  = form.querySelector(".unit-card__submit");
      const raw     = parseInt(input?.value ?? "", 10);

      if (!unitKey || isNaN(raw) || raw < 1) {
        showToast("Введите корректное значение (целое число >= 1)", "error");
        return;
      }

      // unit-key вида "u1", "u2" → 1-based номер
      const unitIndex = parseUnitIndex(unitKey);
      if (unitIndex === null) {
        showToast(`Неизвестный формат unit: ${unitKey}`, "error");
        return;
      }

      try {
        if (submit) {
          submit.disabled = true;
          submit.textContent = "Отправка…";
        }
        await onSetValue(unitIndex, raw);
      } finally {
        if (submit) {
          submit.disabled = false;
          submit.textContent = "Установить";
        }
      }
    });
  });
}

/**
 * Построить HTML-строку карточки unit.
 * @param {string} key
 * @param {import('./api.js').UnitState} unit
 * @returns {string}
 */
function buildUnitCard(key, unit) {
  const command    = unit?.Properties?.command != null ? String(unit.Properties.command) : "";

  return `
<article class="unit-card" aria-label="Unit ${esc(key)}">
  <header class="unit-card__header">
    <span class="unit-card__key">${esc(key)}</span>
  </header>

  <dl class="unit-card__details">
    <div class="unit-card__detail-row">
      <dt>Команда</dt>
      <dd>${command !== "" ? esc(command) : "—"}</dd>
    </div>
  </dl>

  <form class="unit-card__form" data-unit="${esc(key)}" novalidate>
    <label class="unit-card__label" for="input-${esc(key)}">Установить значение</label>
    <div class="unit-card__form-row">
      <input
        id="input-${esc(key)}"
        class="unit-card__input"
        type="number"
        min="1"
        step="1"
        value="${esc(command)}"
        placeholder="Новое значение"
        aria-label="Новое значение команды для ${esc(key)}"
      >
      <button class="unit-card__submit" type="submit">Установить</button>
    </div>
  </form>
</article>`;
}

// ─── Toast-уведомления ───────────────────────────────────────────────────────

let _toastTimer = null;

/**
 * @param {string} message
 * @param {"success"|"error"|"info"} [type]
 * @param {number} [durationMs]
 */
export function showToast(message, type = "info", durationMs = 4000) {
  const container = els.toastContainer();
  if (!container) return;

  // Если уже показывается — заменяем
  if (_toastTimer) {
    clearTimeout(_toastTimer);
    container.innerHTML = "";
  }

  const toast = document.createElement("div");
  toast.className = `toast toast--${type}`;
  toast.setAttribute("role", "alert");
  toast.setAttribute("aria-live", "assertive");
  toast.textContent = message;

  container.appendChild(toast);

  // Принудительный reflow для анимации
  void toast.offsetWidth;
  toast.classList.add("toast--visible");

  _toastTimer = setTimeout(() => {
    toast.classList.remove("toast--visible");
    setTimeout(() => {
      container.innerHTML = "";
      _toastTimer = null;
    }, 300);
  }, durationMs);
}

// ─── Install prompt ──────────────────────────────────────────────────────────

/**
 * @param {boolean} visible
 */
export function setInstallPromptVisible(visible) {
  const el = els.installPrompt();
  if (el) el.hidden = !visible;
}

/**
 * @param {function(): void} handler
 */
export function onInstallClick(handler) {
  const btn = els.installBtn();
  if (btn) btn.addEventListener("click", handler);
}

// ─── Вспомогательные ────────────────────────────────────────────────────────

/**
 * Экранирование HTML для безопасной вставки через innerHTML.
 * @param {string} str
 * @returns {string}
 */
function esc(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * Разобрать ключ вида "u1", "u2" → целое число 1, 2, …
 * @param {string} key
 * @returns {number|null}
 */
function parseUnitIndex(key) {
  const match = /^u(\d+)$/i.exec(key);
  if (!match) return null;
  const n = parseInt(match[1], 10);
  return n >= 1 ? n : null;
}
