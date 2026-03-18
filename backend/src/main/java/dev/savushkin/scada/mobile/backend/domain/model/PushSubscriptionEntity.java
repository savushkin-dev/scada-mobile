package dev.savushkin.scada.mobile.backend.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscriptionEntity {

    @Id
    @Column(name = "installation_id", length = 50)
    private String installationId;

    @Column(nullable = false, length = 1024)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false)
    private String authKey;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(name = "app_channel", nullable = false, length = 50)
    private String appChannel;

    @Column(name = "preferred_workshop_id", length = 100)
    private String preferredWorkshopId;

    @Column(name = "preferred_unit_id", length = 100)
    private String preferredUnitId;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(nullable = false)
    private boolean active = true;

    // Getters and Setters
    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getP256dhKey() { return p256dhKey; }
    public void setP256dhKey(String p256dhKey) { this.p256dhKey = p256dhKey; }

    public String getAuthKey() { return authKey; }
    public void setAuthKey(String authKey) { this.authKey = authKey; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getAppChannel() { return appChannel; }
    public void setAppChannel(String appChannel) { this.appChannel = appChannel; }

    public String getPreferredWorkshopId() { return preferredWorkshopId; }
    public void setPreferredWorkshopId(String preferredWorkshopId) { this.preferredWorkshopId = preferredWorkshopId; }

    public String getPreferredUnitId() { return preferredUnitId; }
    public void setPreferredUnitId(String preferredUnitId) { this.preferredUnitId = preferredUnitId; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
