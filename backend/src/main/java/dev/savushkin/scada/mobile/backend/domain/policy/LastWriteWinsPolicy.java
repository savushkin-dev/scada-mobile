package dev.savushkin.scada.mobile.backend.domain.policy;

import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;

import java.util.Map;

/**
 * Доменная политика слияния команд записи: Last-Write-Wins по unitNumber.
 * <p>
 * Смысл для SCADA: в пределах одного scan-cycle важен только конечный "намеренный" результат для каждого unit.
 * История промежуточных изменений не имеет ценности.
 */
public interface LastWriteWinsPolicy {

    /**
     * Применяет/сливает команду в текущее состояние pending-команд.
     *
     * @param current  текущее состояние (key=unitNumber)
     * @param incoming новая команда
     * @return предыдущая команда для unit (если была), иначе null
     */
    WriteCommand apply(Map<Integer, WriteCommand> current, WriteCommand incoming);
}

