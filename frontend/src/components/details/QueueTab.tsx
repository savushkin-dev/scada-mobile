import type { QueuePayload } from '../../types';

interface Props {
  data: QueuePayload | null;
}

export function QueueTab({ data }: Props) {
  return (
    <div className="card p-5 card-static mb-4">
      <div className="card-title">📋 Очередь печати</div>
      {!data?.items?.length ? (
        <p className="text-center text-[#74777F] py-5 text-[0.88rem]">Очередь пуста</p>
      ) : (
        data.items.map((item) => (
          <div key={item.position} className="queue-item">
            <div className="queue-pos">{item.position}</div>
            <div className="queue-details">
              <div style={{ fontWeight: 600, fontSize: '0.95rem', color: '#1A1C1E' }}>
                Партия: {item.batch}
              </div>
              <div style={{ fontSize: '0.8rem', color: '#74777F', marginTop: '4px' }}>
                Кр. код: {item.shortCode}&nbsp;|&nbsp;Выработка: {item.dateProduced}
              </div>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
