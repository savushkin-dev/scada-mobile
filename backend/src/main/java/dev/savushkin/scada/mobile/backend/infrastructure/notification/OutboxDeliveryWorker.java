package dev.savushkin.scada.mobile.backend.infrastructure.notification;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationOutboxRepository;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationOutboxEntity;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class OutboxDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryWorker.class);
    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 100;

    private final NotificationOutboxRepository outboxRepository;
    private final WebPushNotificationService pushService;

    public OutboxDeliveryWorker(NotificationOutboxRepository outboxRepository,
                                WebPushNotificationService pushService) {
        this.outboxRepository = outboxRepository;
        this.pushService = pushService;
    }

    @Scheduled(fixedDelay = 2000)
    public void processOutbox() {
        if (!pushService.isConfigured()) {
            return; // Web Push disabled, skip processing
        }

        Instant now = Instant.now();
        List<NotificationOutboxEntity> pendingJobs = outboxRepository.findPendingJobs(
                NotificationOutboxEntity.STATUS_PENDING,
                now,
                PageRequest.of(0, BATCH_SIZE)
        );
        if (pendingJobs.isEmpty()) {
            return;
        }

        log.debug("Found {} pending push notification jobs", pendingJobs.size());

        for (NotificationOutboxEntity job : pendingJobs) {
            boolean success = pushService.deliverToJob(job);
            
            if (success) {
                job.setStatus(NotificationOutboxEntity.STATUS_COMPLETED);
                job.setProcessedAt(Instant.now());
                job.setNextRetryAt(null);
            } else {
                int nextRetryCount = job.getRetryCount() + 1;
                job.setRetryCount(nextRetryCount);

                if (nextRetryCount >= MAX_RETRIES) {
                    job.setStatus(NotificationOutboxEntity.STATUS_FAILED);
                    job.setProcessedAt(Instant.now());
                    job.setNextRetryAt(null);
                } else {
                    // Exponential backoff
                    long delaySeconds = (long) Math.pow(2, nextRetryCount) * 5;
                    job.setNextRetryAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
                }
            }
            saveJobState(job);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveJobState(NotificationOutboxEntity job) {
        outboxRepository.save(job);
    }
}
