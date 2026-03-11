import {
  DOMAIN_FLAGS,
  LOGS_ACTIVE_TITLE_STYLE,
  LOGS_DESCRIPTION_STYLE,
  LOGS_EMPTY_SUCCESS_STYLE,
  LOGS_ERROR_DESC_STYLE,
  LOGS_ERROR_NAME_STYLE,
  LOGS_GROUP_BADGE_STYLE,
  LOGS_META_STYLE,
  UI_COPY,
} from '../../config';
import { useDetailsContext } from '../../context/DetailsContext';
import { TabContentState } from '../TabContentState';
import { LogsTabSkeleton } from '../skeleton/LogsTabSkeleton';

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
      <>
        <div className="card p-5 card-static mb-4">
          <div className="card-title" style={LOGS_ACTIVE_TITLE_STYLE}>
            {UI_COPY.activeErrorsTitle}
          </div>
          {activeErrors.length === 0 ? (
            <p style={LOGS_EMPTY_SUCCESS_STYLE}>{UI_COPY.activeErrorsEmpty}</p>
          ) : (
            activeErrors.map((err, i) => (
              <div key={i} className="error-item">
                <div style={LOGS_ERROR_NAME_STYLE}>{err.objectName}</div>
                <div style={LOGS_ERROR_DESC_STYLE}>{err.propertyDesc}</div>{' '}
                {err.description && (
                  <div style={LOGS_DESCRIPTION_STYLE}>{err.description}</div>
                )}{' '}
              </div>
            ))
          )}
        </div>

        <div className="card p-5 card-static mb-4">
          <div className="card-title">{UI_COPY.eventLogTitle}</div>
          {!errorsData?.logs?.length ? (
            <p className="text-center text-[#74777F] py-2.5 text-[0.88rem]">
              {UI_COPY.eventLogEmpty}
            </p>
          ) : (
            errorsData.logs.map((log, i) => (
              <div key={i} className="log-item">
                <div style={LOGS_META_STYLE}>
                  {log.time}
                  <span style={LOGS_GROUP_BADGE_STYLE}>{log.group}</span>
                </div>
                <div style={LOGS_DESCRIPTION_STYLE}>{log.description}</div>
              </div>
            ))
          )}
        </div>
      </>
    </TabContentState>
  );
}
