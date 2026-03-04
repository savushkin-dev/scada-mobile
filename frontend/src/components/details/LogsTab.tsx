import type { ErrorsPayload } from '../../types';

interface Props {
  data: ErrorsPayload | null;
}

export function LogsTab({ data }: Props) {
  const activeErrors = (data?.deviceErrors ?? []).filter((e) => e.value === 1);

  return (
    <>
      <div className="card p-5 card-static mb-4">
        <div className="card-title" style={{ color: '#EA4335' }}>
          ⚠️ Активные ошибки
        </div>
        {activeErrors.length === 0 ? (
          <p
            style={{
              textAlign: 'center',
              color: '#34A853',
              padding: '16px 0',
              fontWeight: 600,
              fontSize: '0.9rem',
            }}
          >
            ✅ Нет активных ошибок
          </p>
        ) : (
          activeErrors.map((err, i) => (
            <div key={i} className="error-item">
              <div style={{ fontWeight: 700, color: '#EA4335', fontSize: '0.9rem' }}>
                {err.objectName}
              </div>
              <div style={{ fontSize: '0.85rem', color: '#1A1C1E', marginTop: '4px' }}>
                {err.propertyDesc}
              </div>
            </div>
          ))
        )}
      </div>

      <div className="card p-5 card-static mb-4">
        <div className="card-title">📝 Журнал событий</div>
        {!data?.logs?.length ? (
          <p className="text-center text-[#74777F] py-2.5 text-[0.88rem]">Журнал пуст</p>
        ) : (
          data.logs.map((log, i) => (
            <div key={i} className="log-item">
              <div style={{ fontSize: '0.75rem', color: '#74777F', marginBottom: '4px' }}>
                {log.time}
                <span
                  style={{
                    background: '#EDEEF0',
                    color: '#5F6368',
                    padding: '2px 7px',
                    borderRadius: '8px',
                    marginLeft: '6px',
                    fontWeight: 600,
                    fontSize: '0.7rem',
                  }}
                >
                  {log.group}
                </span>
              </div>
              <div style={{ fontSize: '0.9rem', color: '#1A1C1E' }}>{log.description}</div>
            </div>
          ))
        )}
      </div>
    </>
  );
}
