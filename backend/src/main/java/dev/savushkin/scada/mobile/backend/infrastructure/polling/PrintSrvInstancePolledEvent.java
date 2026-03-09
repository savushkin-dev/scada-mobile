package dev.savushkin.scada.mobile.backend.infrastructure.polling;

/**
 * Событие завершения одного polling-прохода по конкретному инстансу PrintSrv.
 *
 * <p>Публикуется сразу после успешного опроса инстанса и используется для
 * немедленной live-рассылки без ожидания остальных машин.
 */
public record PrintSrvInstancePolledEvent(String instanceId) {
}