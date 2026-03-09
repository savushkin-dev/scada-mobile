import { QUEUE_PRIMARY_TEXT_STYLE, QUEUE_SECONDARY_TEXT_STYLE, UI_COPY } from '../../config';
import type { QueuePayload } from '../../types';

interface Props {
  data: QueuePayload | null;
}

export function QueueTab({ data }: Props) {
  return (
    <div className="card p-5 card-static mb-4">
      <div className="card-title">{UI_COPY.queueTitle}</div>
      {!data?.items?.length ? (
        <p className="text-center text-[#74777F] py-5 text-[0.88rem]">{UI_COPY.queueEmpty}</p>
      ) : (
        data.items.map((item) => (
          <div key={item.position} className="queue-item">
            <div className="queue-pos">{item.position}</div>
            <div className="queue-details">
              <div style={QUEUE_PRIMARY_TEXT_STYLE}>
                {UI_COPY.queueBatchPrefix}: {item.batch}
              </div>
              <div style={QUEUE_SECONDARY_TEXT_STYLE}>
                {UI_COPY.queueShortCodePrefix}: {item.shortCode}&nbsp;|&nbsp;
                {UI_COPY.queueProducedPrefix}: {item.dateProduced}
              </div>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
