package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.PushSubscription;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Порт хранилища push-подписок.
 * <p>
 * Реализации могут быть in-memory (для MVP) или backed by БД (для production).
 * Основная семантика — <b>upsert по {@code installationId}</b>: повторный вызов
 * {@link #save(PushSubscription)} с тем же {@code installationId} заменяет запись.
 */
public interface PushSubscriptionRepository {

    /**
     * Сохраняет или обновляет подписку (upsert по {@code installationId}).
     *
     * @param subscription подписка для сохранения
     */
    void save(@NonNull PushSubscription subscription);

    /**
     * Возвращает подписку по {@code installationId}.
     *
     * @param installationId идентификатор устройства
     * @return подписка или {@link Optional#empty()} если не найдена
     */
    @NonNull
    Optional<PushSubscription> findByInstallationId(@NonNull String installationId);

    /**
     * Деактивирует подписку для данного устройства.
     * Не удаляет запись физически — устанавливает {@code active = false}.
     *
     * @param installationId идентификатор устройства
     * @return {@code true} если подписка была найдена и деактивирована
     */
    boolean deactivate(@NonNull String installationId);

    /**
     * Возвращает все активные подписки.
     * Используется delivery-воркером для fan-out рассылки.
     *
     * @return неизменяемый список активных подписок
     */
    @NonNull
    List<PushSubscription> findAllActive();
}
