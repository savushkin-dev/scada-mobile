package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;

import java.util.Map;

/**
 * Порт для scan-cycle: атомарно забрать pending-команды и очистить буфер.
 * <p>
 * Выделен отдельно от {@link PendingWriteCommandsPort}, чтобы application слой не APIшился про drain.
 */
public interface PendingWriteCommandsDrainPort {

    /**
     * Забирает и очищает весь буфер.
     * Команды, пришедшие в процессе drain, должны попасть в следующий цикл.
     */
    Map<Integer, WriteCommand> drain();
}

