import {
  DOMAIN_FLAGS,
  LOGS_ACTIVE_TITLE_STYLE,
  LOGS_DESCRIPTION_STYLE,
  LOGS_EMPTY_SUCCESS_STYLE,
  LOGS_ERROR_DESC_STYLE,
  LOGS_ERROR_NAME_STYLE,
  UI_COPY,
} from '../../config';
import { useDetailsContext } from '../../context/DetailsContext';
import { TabContentState } from '../TabContentState';
import { LogsTabSkeleton } from '../skeleton/LogsTabSkeleton';

/**
 * Вкладка "Журнал".
 *
 * Содержит срез активных ошибок (текущее состояние аппарата).
 * Исторический «Журнал событий» осознанно не отображается — на терминале
 * 4.2" он не востребован и только занимает место (см. issue #83).
 *
 * Границы загрузки/ошибки унифицированы через {@link ../TabContentState.tsx}.
 */

const pad2 = (n: number) => String(n).padStart(2, '0');

/**
 * Форматирует occurredAt (ISO local date-time) в формат старой SCADA:
 * «25.09.2026 5:57:00» (дд.мм.гггг чч:мм:сс, час без ведущего нуля).
 * Нераспарсенное значение возвращается как есть.
 */
function formatOccurredAt(raw: string): string {
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return `${pad2(d.getDate())}.${pad2(d.getMonth() + 1)}.${d.getFullYear()} ${d.getHours()}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
}

export function LogsTab() {
  const { errorsData, unitSignal, pageError } = useDetailsContext();
  const activeErrors = (errorsData?.deviceErrors ?? []).filter(
    (e) => Number(e.value) !== DOMAIN_FLAGS.inactive
  );

  const isLoading =
    (unitSignal === 'idle' || unitSignal === 'reconnecting') &&
    errorsData === null &&
    pageError === null;
  const error = pageError !== null && errorsData === null ? pageError : null;

  return (
    <TabContentState isLoading={isLoading} error={error} skeleton={<LogsTabSkeleton />}>
      <div className="card p-4 card-static mb-3">
        <div className="card-title flex items-center gap-2" style={LOGS_ACTIVE_TITLE_STYLE}>
          <img src="/assets/warning.svg" alt="" aria-hidden="true" className="h-5 w-5" />
          {UI_COPY.activeErrorsTitle}
        </div>
        {activeErrors.length === 0 ? (
          <p style={LOGS_EMPTY_SUCCESS_STYLE}>Нет активных ошибок</p>
        ) : (
          activeErrors.map((err, i) => (
            <div key={i} className="error-item">
              <div style={LOGS_ERROR_NAME_STYLE}>{err.objectName}</div>
              {err.occurredAt && (
                <div style={LOGS_ERROR_DESC_STYLE}>{formatOccurredAt(err.occurredAt)}</div>
              )}
              {err.description && <div style={LOGS_DESCRIPTION_STYLE}>{err.description}</div>}
            </div>
          ))
        )}
      </div>
    </TabContentState>
  );
}
