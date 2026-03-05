package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Описание одной конкретной ошибки в составе алёрта.
 *
 * @param device  Имя устройства-источника ({@code Line}, {@code CamAgregation}, {@code Printer11} и пр.)
 * @param code    Числовой код ошибки из протокола PrintSrv (0 если не применимо)
 * @param message Текстовое описание ошибки
 */
public record AlertErrorDTO(String device, int code, String message) {
}
