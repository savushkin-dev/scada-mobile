package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnitErrorStoreTest {

    private static final String UNIT = "grunwald5";

    private final UnitErrorStore store = new UnitErrorStore();

    @Test
    void assignsOccurredAtOnFirstActivationAndKeepsItWhileActive() {
        store.update(UNIT, List.of(err("Dev041Connection")));
        LocalDateTime first = store.getErrors(UNIT).getFirst().occurredAt();
        assertThat(first).isNotNull();

        // Повторный цикл опроса: ошибка всё ещё активна — occurredAt не меняется
        store.update(UNIT, List.of(err("Dev041Connection")));
        assertThat(store.getErrors(UNIT).getFirst().occurredAt()).isEqualTo(first);
    }

    @Test
    void resetsOccurredAtAfterErrorClears() {
        store.update(UNIT, List.of(err("Dev041Connection")));
        store.update(UNIT, List.of());

        store.update(UNIT, List.of(err("Dev041Connection")));
        assertThat(store.getErrors(UNIT).getFirst().occurredAt()).isNotNull();
    }

    @Test
    void tracksEachErrorKeyIndependently() {
        store.update(UNIT, List.of(err("Dev041Connection")));
        store.update(UNIT, List.of(err("Dev041Connection"), err("Dev041Fail")));

        List<DeviceError> errors = store.getErrors(UNIT);
        assertThat(errors).hasSize(2);
        assertThat(errors.stream().map(DeviceError::occurredAt)).doesNotContainNull();
    }

    @Test
    void enrichKeepsPreAssignedOccurredAt() {
        LocalDateTime fixed = LocalDateTime.of(2026, 9, 25, 10, 0);
        store.update(UNIT, List.of(new DeviceError("Камера 41", "Dev041Connection",
                "Нет связи с устройством", fixed)));

        assertThat(store.getErrors(UNIT).getFirst().occurredAt()).isEqualTo(fixed);
    }

    private static DeviceError err(String propertyDesc) {
        return new DeviceError("Камера 41", propertyDesc, "Нет связи с устройством");
    }
}
