package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import org.springframework.context.ApplicationEvent;

/**
 * Событие, публикуемое {@code ScanCycleScheduler} после завершения
 * каждого цикла опроса PrintSrv.
 * <p>
 * Используется для информирования {@link StatusBroadcaster} о том,
 * что данные в {@code InMemoryInstanceSnapshotStore} были обновлены
 * и WebSocket-клиентам нужно отправить актуальный статус.
 */
public class ScanCycleCompletedEvent extends ApplicationEvent {

    public ScanCycleCompletedEvent(Object source) {
        super(source);
    }
}
