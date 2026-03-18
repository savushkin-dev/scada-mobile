package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.config.VapidProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Push Notifications", description = "Публичная конфигурация Web Push")
@RestController
@RequestMapping("${scada.api.base-path}/push")
public class PushConfigController {

    private final VapidProperties vapidProperties;

    public PushConfigController(VapidProperties vapidProperties) {
        this.vapidProperties = vapidProperties;
    }

    @Operation(
            summary = "Получить публичную конфигурацию Web Push",
            description = "Возвращает публичный VAPID-ключ. Приватный ключ никогда не возвращается."
    )
    @GetMapping("/public-key")
    public PushPublicKeyResponse getPublicKey() {
        boolean enabled = vapidProperties.isConfigured();
        String publicKey = enabled ? vapidProperties.getPublicKey() : null;
        return new PushPublicKeyResponse(enabled, publicKey);
    }

    public record PushPublicKeyResponse(boolean enabled, String publicKey) {}
}
