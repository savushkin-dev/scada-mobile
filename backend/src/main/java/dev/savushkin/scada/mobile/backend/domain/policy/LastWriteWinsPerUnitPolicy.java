package dev.savushkin.scada.mobile.backend.domain.policy;

import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Реализация LWW: для одного unit хранится только последняя команда.
 */
@Component
public class LastWriteWinsPerUnitPolicy implements LastWriteWinsPolicy {

    @Override
    public WriteCommand apply(Map<Integer, WriteCommand> current, WriteCommand incoming) {
        if (current == null) {
            throw new IllegalArgumentException("Current map cannot be null");
        }
        if (incoming == null) {
            throw new IllegalArgumentException("Incoming command cannot be null");
        }
        return current.put(incoming.unitNumber(), incoming);
    }
}

