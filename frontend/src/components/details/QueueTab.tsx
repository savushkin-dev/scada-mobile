import { QUEUE_PRIMARY_TEXT_STYLE, QUEUE_SECONDARY_TEXT_STYLE, UI_COPY } from '../../config';
import { useDetailsContext } from '../../context/DetailsContext';
import { TabContentState } from '../TabContentState';
import { QueueTabSkeleton } from '../skeleton/QueueTabSkeleton';

export function QueueTab() {
  const { queueData, unitSignal, pageError } = useDetailsContext();

  const isLoading =
    (unitSignal === 'idle' || unitSignal === 'reconnecting') &&
    queueData === null &&
    pageError === null;
  const error = pageError !== null && queueData === null ? pageError : null;

  return (
    <TabContentState isLoading={isLoading} error={error} skeleton={<QueueTabSkeleton />}>
      <div className="card p-5 card-static mb-4">
        <div className="card-title">{UI_COPY.queueTitle}</div>
        {!queueData?.items?.length ? (
          <p className="text-center text-[#74777F] py-5 text-[0.88rem]">{UI_COPY.queueEmpty}</p>
        ) : (
          queueData.items.map((item) => (
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
    </TabContentState>
  );
}
