package dev.savushkin.scada.mobile.backend.domain.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Доменная модель push-подписки устройства.
 * <p>
 * Представляет одну Web Push Subscription, зарегистрированную клиентом.
 * {@code installationId} выступает суррогатным идентификатором устройства/пользователя,
 * поскольку в проекте пока нет авторизации.
 *
 * <h3>Уникальность</h3>
 * Уникальный ключ хранилища — {@code installationId}.
 * Новая регистрация с тем же {@code installationId} заменяет старую (upsert-семантика).
 *
 * @param installationId     стабильный идентификатор устройства (UUID из localStorage)
 * @param endpoint           URL endpoint Web Push сервиса браузера
 * @param p256dhKey          публичный ключ шифрования (Base64url)
 * @param authKey            аутентификационный секрет (Base64url)
 * @param platform           платформа клиента: {@code android}, {@code desktop}, {@code unknown}
 * @param appChannel         канал доставки: {@code PWA} или {@code TWA}
 * @param preferredWorkshopId опциональный фильтр по цеху (зарезервировано для scoped delivery)
 * @param preferredUnitId     опциональный фильтр по аппарату (зарезервировано для scoped delivery)
 * @param registeredAt       момент регистрации (UTC)
 * @param active             флаг активности; {@code false} — подписка деактивирована
 */
public record PushSubscription(
        @NonNull String installationId,
        @NonNull String endpoint,
        @NonNull String p256dhKey,
        @NonNull String authKey,
        @NonNull String platform,
        @NonNull String appChannel,
        @Nullable String preferredWorkshopId,
        @Nullable String preferredUnitId,
        @NonNull Instant registeredAt,
        boolean active
) {
}
