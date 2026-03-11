import { useState } from 'react';
import {
  BATCH_ADDITIONAL_FIELDS,
  BATCH_EXPANDED_SECTION_STYLE,
  BATCH_PRIMARY_FIELDS,
  BOOLEAN_LABEL,
  DOMAIN_DEFAULTS,
  DOMAIN_FLAGS,
  UI_COPY,
} from '../../config';
import { useDetailsContext } from '../../context/DetailsContext';
import { TabContentState } from '../TabContentState';
import { BatchTabSkeleton } from '../skeleton/BatchTabSkeleton';
import type { LineStatusPayload } from '../../types';

function val(v: string | number | undefined | null): string {
  if (v === null || v === undefined) return DOMAIN_DEFAULTS.emptyValue;
  return String(v);
}

function formatFieldValue(
  data: LineStatusPayload | null,
  key: keyof LineStatusPayload,
  format?: 'boolean'
) {
  const value = data?.[key];
  if (format === 'boolean') {
    return Number(value) === DOMAIN_FLAGS.active ? BOOLEAN_LABEL.yes : BOOLEAN_LABEL.no;
  }
  return val(value as string | number | undefined | null);
}

export function BatchTab() {
  const { lineData, unitSignal, pageError } = useDetailsContext();
  const [expanded, setExpanded] = useState(false);

  // skeleton: WS ещё не подключён, нет данных и нет известной ошибки
  const isLoading =
    (unitSignal === 'idle' || unitSignal === 'reconnecting') &&
    lineData === null &&
    pageError === null;
  // error: есть ошибка (REST или WS) и данные так и не пришли
  const error = pageError !== null && lineData === null ? pageError : null;

  return (
    <TabContentState isLoading={isLoading} error={error} skeleton={<BatchTabSkeleton />}>
      <div className="card p-5 card-static mb-4">
        <div className="card-title">{UI_COPY.batchTitle}</div>

        {BATCH_PRIMARY_FIELDS.map(({ key, label, format }) => (
          <div key={key} className="kv-row">
            <div className="kv-key">{label}</div>
            <div className="kv-val">{formatFieldValue(lineData, key, format)}</div>
          </div>
        ))}

        <button className="accordion-btn" onClick={() => setExpanded((e) => !e)}>
          {expanded ? UI_COPY.batchShowLess : UI_COPY.batchShowMore}
        </button>

        {expanded && (
          <div style={BATCH_EXPANDED_SECTION_STYLE}>
            {BATCH_ADDITIONAL_FIELDS.map(({ key, label, format }) => (
              <div key={key} className="kv-row">
                <div className="kv-key">{label}</div>
                <div className="kv-val">{formatFieldValue(lineData, key, format)}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </TabContentState>
  );
}
