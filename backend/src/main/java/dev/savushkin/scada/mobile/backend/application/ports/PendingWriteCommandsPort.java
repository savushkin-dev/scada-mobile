package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;

/**
 * Порт буфера pending-команд записи.
 * <p>
 * Контракт:
 * <ul>
 *   <li>Last-Write-Wins по unitNumber: для одного unit хранится только последняя команда.</li>
 *   <li>Буфер потокобезопасен между REST потоками и scan-cycle потоком.</li>
 *   <li>Имеет лимит размера и бросает управляемую ошибку при переполнении.</li>
 * </ul>
 */
public interface PendingWriteCommandsPort {

    /**
     * Добавляет/заменяет команду в буфере (Last-Write-Wins по unitNumber).
     */
    void enqueue(WriteCommand command);

    /**
     * @return текущий размер буфера.
     */
    int size();

    /**
     * @return true если буфер пуст.
     */
    boolean isEmpty();
}

