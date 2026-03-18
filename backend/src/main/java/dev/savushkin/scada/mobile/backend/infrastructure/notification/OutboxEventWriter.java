package dev.savushkin.scada.mobile.backend.infrastructure.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.application.ports.NotificationOutboxRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PushSubscriptionRepository;
import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationOutboxEntity;
import dev.savushkin.scada.mobile.backend.domain.model.PushSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutboxEventWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventWriter.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(PushSubscriptionRepository subscriptionRepository,
                             NotificationOutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Async("notificationEventsExecutor")
    @EventListener
    @Transactional
    public void onAlertNotification(AlertNotificationEvent event) {
        if (event.getState() != AlertNotificationEvent.State.ACTIVATED) {
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findAllActive();
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = buildPayload(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize push payload for alertId='{}'", event.getAlertId(), e);
            return;
        }

        List<PushSubscription> scopedSubscriptions = subscriptions.stream()
                .filter(sub -> matchesScope(sub, event))
                .toList();

        if (scopedSubscriptions.isEmpty()) {
            return;
        }

        log.debug("Writing {} notification jobs to outbox for alertId='{}'", scopedSubscriptions.size(), event.getAlertId());

        List<NotificationOutboxEntity> jobs = scopedSubscriptions.stream().map(sub -> {
            NotificationOutboxEntity job = new NotificationOutboxEntity();
            job.setInstallationId(sub.installationId());
            job.setPayload(payload);
            job.setStatus(NotificationOutboxEntity.STATUS_PENDING);
            return job;
        }).toList();

        outboxRepository.saveAll(jobs);
    }

    private String buildPayload(AlertNotificationEvent event) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", event.getAlertId());
        payload.put("state", event.getState().name());
        payload.put("title", buildTitle(event));
        payload.put("body", buildBody(event));
        payload.put("route", event.getRoute());
        payload.put("severity", event.getSeverity());
        payload.put("groupKey", event.getGroupKey());
        payload.put("tag", event.getNotificationTag());
        payload.put("errorSignature", event.getErrorSignature());
        return objectMapper.writeValueAsString(payload);
    }

    private String buildTitle(AlertNotificationEvent event) {
        return "SCADA Alert: " + event.getUnitName();
    }

    private String buildBody(AlertNotificationEvent event) {
        if (event.getErrors().isEmpty()) {
            return "Active incident detected";
        }
        StringBuilder sb = new StringBuilder();
        for (AlertErrorDTO error : event.getErrors()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(error.device()).append(": ").append(error.message());
        }
        return sb.toString();
    }

    private boolean matchesScope(PushSubscription subscription, AlertNotificationEvent event) {
        String preferredWorkshopId = subscription.preferredWorkshopId();
        if (preferredWorkshopId != null && !preferredWorkshopId.isBlank()
                && !preferredWorkshopId.equals(event.getWorkshopId())) {
            return false;
        }

        String preferredUnitId = subscription.preferredUnitId();
        return preferredUnitId == null || preferredUnitId.isBlank() || preferredUnitId.equals(event.getUnitId());
    }
}
