package dev.savushkin.scada.mobile.backend.api.dto;

import java.util.List;

/**
 * Группа устройств аппарата для вкладки «Устройства» (паритет со старой SCADA).
 *
 * @param label отображаемая метка группы (имя автомата, «Поток», «Поток 2», …)
 * @param order порядок группы на экране (0 — группа машины)
 * @param codes коды устройств внутри группы (по display_order)
 */
public record DeviceGroupDTO(String label, int order, List<String> codes) {
}
