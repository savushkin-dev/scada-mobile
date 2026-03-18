package dev.savushkin.scada.mobile.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Тело запроса POST /api/v1.0.0/push/subscriptions — регистрация или обновление push-подписки.
 * <p>
 * Соответствует структуре Web Push Subscription из PushManager.subscribe().
 *
 * @param installationId  стабильный UUID устройства (генерируется на клиенте и хранится в localStorage)
 * @param subscription    данные Web Push Subscription (endpoint + ключи шифрования)
 * @param platform        платформа: {@code android}, {@code desktop} или {@code unknown}
 * @param appChannel      канал: {@code PWA} или {@code TWA}
 * @param preferredWorkshopId опциональный фильтр по цеху (зарезервировано)
 * @param preferredUnitId     опциональный фильтр по аппарату (зарезервировано)
 */
public record RegisterPushSubscriptionRequest(

        @NotBlank(message = "installationId обязателен")
        @Size(min = 1, max = 128, message = "installationId должен быть от 1 до 128 символов")
        String installationId,

        @NotNull(message = "subscription обязателен")
        @Valid
        SubscriptionData subscription,

        @NotBlank(message = "platform обязателен")
        @Pattern(regexp = "android|desktop|unknown",
                message = "platform должен быть: android, desktop или unknown")
        String platform,

        @NotBlank(message = "appChannel обязателен")
        @Pattern(regexp = "PWA|TWA",
                message = "appChannel должен быть: PWA или TWA")
        String appChannel,

        @Nullable
        @Size(max = 128, message = "preferredWorkshopId не должен превышать 128 символов")
        String preferredWorkshopId,

        @Nullable
        @Size(max = 128, message = "preferredUnitId не должен превышать 128 символов")
        String preferredUnitId

) {

    /**
     * Данные Web Push Subscription, возвращаемые браузером из {@code PushManager.subscribe()}.
     *
     * @param endpoint URL push-endpoint браузера
     * @param keys     ключи шифрования VAPID
     */
    public record SubscriptionData(

            @NotBlank(message = "subscription.endpoint обязателен")
            @Size(max = 2048, message = "endpoint не должен превышать 2048 символов")
            String endpoint,

            @NotNull(message = "subscription.keys обязателен")
            @Valid
            Keys keys

    ) {

        /**
         * Ключи шифрования VAPID.
         *
         * @param p256dh публичный ключ ECDH P-256 (Base64url)
         * @param auth   аутентификационный секрет (Base64url)
         */
        public record Keys(

                @NotBlank(message = "subscription.keys.p256dh обязателен")
                @Size(min = 1, max = 512, message = "p256dh не должен превышать 512 символов")
                String p256dh,

                @NotBlank(message = "subscription.keys.auth обязателен")
                @Size(min = 1, max = 256, message = "auth не должен превышать 256 символов")
                String auth

        ) {}
    }
}
