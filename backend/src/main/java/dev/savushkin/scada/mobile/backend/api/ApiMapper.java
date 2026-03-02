package dev.savushkin.scada.mobile.backend.api;

import org.springframework.stereotype.Component;

/**
 * Маппер для преобразования между доменными моделями и API DTOs.
 * <p>
 * На данном этапе маппинг workshop/unit выполняется непосредственно
 * в {@link dev.savushkin.scada.mobile.backend.services.WorkshopService},
 * т.к. данные агрегируются из конфигурации и snapshot-ов.
 * <p>
 * В будущем, при добавлении WebSocket-сообщений (LINE_STATUS, DEVICES_STATUS и пр.),
 * сюда будут добавлены методы маппинга из доменных моделей в WS-DTO.
 */
@Component
public class ApiMapper {
}
