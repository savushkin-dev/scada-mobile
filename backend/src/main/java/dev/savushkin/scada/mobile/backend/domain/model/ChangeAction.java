package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Действие, произведённое администратором над сущностью.
 * Используется в доменных событиях изменений данных, которые рассылаются
 * подключённым клиентам через WebSocket после успешного коммита в БД.
 */
public enum ChangeAction {
    CREATE,
    UPDATE,
    DELETE
}
