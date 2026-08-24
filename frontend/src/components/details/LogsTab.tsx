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
          <p style={LOGS_EMPTY_SUCCESS_STYLE} className="flex items-center gap-1.5">
            <img
              src="/assets/check-circle.svg"
              alt=""
              aria-hidden="true"
              className="h-5 w-5"
              style={{
                filter:
                  'invert(48%) sepia(95%) saturate(378%) hue-rotate(88deg) brightness(95%) contrast(92%)',
              }}
            />
            Нет активных ошибок
          </p>
        ) : (
          activeErrors.map((err, i) => (
            <div key={i} className="error-item">
              <div style={LOGS_ERROR_NAME_STYLE}>{err.objectName}</div>
              <div style={LOGS_ERROR_DESC_STYLE}>{err.propertyDesc}</div>{' '}
              {err.description && <div style={LOGS_DESCRIPTION_STYLE}>{err.description}</div>}{' '}
            </div>
          ))
        )}
      </div>
    </TabContentState>
  );
}
