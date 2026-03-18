package dev.savushkin.scada.mobile.backend.infrastructure.notification;

import dev.savushkin.scada.mobile.backend.application.ports.PushSubscriptionRepository;
import dev.savushkin.scada.mobile.backend.config.VapidProperties;
import dev.savushkin.scada.mobile.backend.domain.model.PushSubscription;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationOutboxEntity;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Optional;

@Component
public class WebPushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebPushNotificationService.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final VapidProperties vapidProperties;

    @Nullable
    private PushService pushService;

    public WebPushNotificationService(
            PushSubscriptionRepository subscriptionRepository,
            VapidProperties vapidProperties
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.vapidProperties = vapidProperties;
    }

    @PostConstruct
    public void init() {
        if (!vapidProperties.isConfigured()) {
            log.warn("VAPID keys are not configured. Web Push notifications are disabled.");
            return;
        }

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        try {
            this.pushService = new PushService()
                    .setPublicKey(vapidProperties.getPublicKey())
                    .setPrivateKey(vapidProperties.getPrivateKey())
                    .setSubject(vapidProperties.getSubject());
            log.info("Web Push service initialized (subject='{}')", vapidProperties.getSubject());
        } catch (Exception e) {
            log.error("Failed to initialize Web Push service.", e);
        }
    }

    public boolean isConfigured() {
        return pushService != null;
    }

    public boolean deliverToJob(NotificationOutboxEntity job) {
        if (pushService == null) {
            return false;
        }

        Optional<PushSubscription> subOpt = subscriptionRepository.findByInstallationId(job.getInstallationId());
        if (subOpt.isEmpty() || !subOpt.get().active()) {
            // No active subscription, consider job completed or dropped
            return true; 
        }

        PushSubscription sub = subOpt.get();

        try {
            Notification notification = new Notification(
                    sub.endpoint(),
                    sub.p256dhKey(),
                    sub.authKey(),
                    job.getPayload().getBytes(StandardCharsets.UTF_8)
            );
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();

            if (status == HttpStatus.SC_CREATED) {
                return true;
            } else if (status == HttpStatus.SC_GONE || status == HttpStatus.SC_NOT_FOUND) {
                // Subscription is dead
                subscriptionRepository.deactivate(sub.installationId());
                log.info("Push subscription expired (HTTP {}), deactivated: installationId='{}'", status, sub.installationId());
                return true; // We don't want to retry a dead subscription
            } else {
                log.warn("Push delivery failed with status {}: installationId='{}'", status, sub.installationId());
                return false; // Retry later
            }
        } catch (Exception e) {
            log.error("Push delivery error: installationId='{}'", sub.installationId(), e);
            return false; // Retry
        }
    }
}
