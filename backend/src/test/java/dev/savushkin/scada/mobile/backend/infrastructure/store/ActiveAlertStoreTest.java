package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты для {@link ActiveAlertStore}.
 *
 * <p>Проверяемые сценарии:
 * <ul>
 *   <li>Нет алёрта → нет алёрта: пустая дельта</li>
 *   <li>Нет алёрта → есть алёрт: added</li>
 *   <li>Есть алёрт → нет алёрта: removed</li>
 *   <li>Есть алёрт → тот же состав ошибок: пустая дельта (нет дублирования)</li>
 *   <li>Есть алёрт → изменился состав ошибок: added (новое уведомление)</li>
 *   <li>computeErrorSignature: группировка по device + count</li>
 * </ul>
 */
class ActiveAlertStoreTest {

    private ActiveAlertStore store;

    private static final String UNIT_ID = "hassia2";

    @BeforeEach
    void setUp() {
        store = new ActiveAlertStore();
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    private static AlertMessageDTO alert(List<AlertErrorDTO> errors) {
        return AlertMessageDTO.active("dess", UNIT_ID, "Hassia №2", "Critical", errors,
                "2026-03-17T10:00:00");
    }

    private static AlertErrorDTO err(String device, String message) {
        return new AlertErrorDTO(device, 0, message);
    }

    // ─── Тесты дельта-логики ─────────────────────────────────────────────────

    @Test
    @DisplayName("null → null: пустая дельта")
    void nullToNull_emptyDelta() {
        ActiveAlertStore.Delta delta = store.updateAndDiff(UNIT_ID, null);

        assertThat(delta.added()).isEmpty();
        assertThat(delta.removed()).isEmpty();
    }

    @Test
    @DisplayName("null → alert: added")
    void nullToAlert_added() {
        AlertMessageDTO current = alert(List.of(err("Printer11", "Нет связи")));
        ActiveAlertStore.Delta delta = store.updateAndDiff(UNIT_ID, current);

        assertThat(delta.added()).hasSize(1);
        assertThat(delta.added().getFirst()).isEqualTo(current);
        assertThat(delta.removed()).isEmpty();
    }

    @Test
    @DisplayName("alert → null: removed")
    void alertToNull_removed() {
        AlertMessageDTO existing = alert(List.of(err("Printer11", "Нет связи")));
        store.updateAndDiff(UNIT_ID, existing);

        ActiveAlertStore.Delta delta = store.updateAndDiff(UNIT_ID, null);

        assertThat(delta.added()).isEmpty();
        assertThat(delta.removed()).hasSize(1);
        assertThat(delta.removed().getFirst()).isEqualTo(existing);
    }

    @Test
    @DisplayName("alert → тот же состав ошибок: пустая дельта (нет спама)")
    void sameErrorSignature_emptyDelta() {
        AlertMessageDTO first = alert(List.of(err("Printer11", "Нет связи")));
        store.updateAndDiff(UNIT_ID, first);

        // Такой же состав ошибок, но другой timestamp
        AlertMessageDTO same = AlertMessageDTO.active("dess", UNIT_ID, "Hassia №2", "Critical",
                List.of(err("Printer11", "Нет связи")), "2026-03-17T10:01:00");

        ActiveAlertStore.Delta delta = store.updateAndDiff(UNIT_ID, same);

        assertThat(delta.added()).isEmpty();
        assertThat(delta.removed()).isEmpty();
    }

    @Test
    @DisplayName("alert → изменился состав ошибок: added (новое уведомление)")
    void differentErrorSignature_added() {
        AlertMessageDTO first = alert(List.of(err("Printer11", "Нет связи")));
        store.updateAndDiff(UNIT_ID, first);

        // Добавилась новая ошибка на другом устройстве
        AlertMessageDTO changed = alert(List.of(
                err("Printer11", "Нет связи"),
                err("CamChecker", "Ошибка камеры")
        ));

        ActiveAlertStore.Delta delta = store.updateAndDiff(UNIT_ID, changed);

        assertThat(delta.added()).hasSize(1);
        assertThat(delta.added().getFirst()).isEqualTo(changed);
        assertThat(delta.removed()).isEmpty();
    }

    @Test
    @DisplayName("alert → увеличилось количество ошибок того же устройства: added")
    void increasedErrorCount_added() {
        AlertMessageDTO first = alert(List.of(err("Printer11", "Ошибка 1")));
        store.updateAndDiff(UNIT_ID, first);

        AlertMessageDTO changed = alert(List.of(
                err("Printer11", "Ошибка 1"),
                err("Printer11", "Ошибка 2")
        ));

        ActiveAlertStore.Delta delta = store.updateAndDiff(UNIT_ID, changed);

        assertThat(delta.added()).hasSize(1);
    }

    @Test
    @DisplayName("getAll() возвращает активные алёрты")
    void getAll_returnsActiveAlerts() {
        assertThat(store.getAll()).isEmpty();

        store.updateAndDiff(UNIT_ID, alert(List.of(err("Printer11", "Ошибка"))));
        assertThat(store.getAll()).hasSize(1);
    }

    @Test
    @DisplayName("getAll() не включает удалённые алёрты")
    void getAll_excludesRemovedAlerts() {
        store.updateAndDiff(UNIT_ID, alert(List.of(err("Printer11", "Ошибка"))));
        store.updateAndDiff(UNIT_ID, null);

        assertThat(store.getAll()).isEmpty();
    }

    // ─── Тесты computeErrorSignature ─────────────────────────────────────────

    @Test
    @DisplayName("signature: пустой список → пустая строка")
    void signature_emptyList() {
        String sig = ActiveAlertStore.computeErrorSignature(List.of());
        assertThat(sig).isEmpty();
    }

    @Test
    @DisplayName("signature: одно устройство, одна ошибка")
    void signature_singleDevice() {
        String sig = ActiveAlertStore.computeErrorSignature(
                List.of(err("Printer11", "Нет связи")));
        assertThat(sig).isEqualTo("Printer11:1");
    }

    @Test
    @DisplayName("signature: одно устройство, две ошибки — счётчик 2")
    void signature_twoErrorsSameDevice() {
        String sig = ActiveAlertStore.computeErrorSignature(
                List.of(err("Printer11", "Ошибка 1"), err("Printer11", "Ошибка 2")));
        assertThat(sig).isEqualTo("Printer11:2");
    }

    @Test
    @DisplayName("signature: два устройства — отсортированы по алфавиту")
    void signature_twoDevices_sortedAlphabetically() {
        String sig = ActiveAlertStore.computeErrorSignature(
                List.of(err("Printer11", "Ошибка"), err("CamChecker", "Ошибка")));
        assertThat(sig).isEqualTo("CamChecker:1,Printer11:1");
    }

    @Test
    @DisplayName("signature: стабильна вне зависимости от порядка ошибок")
    void signature_orderIndependent() {
        List<AlertErrorDTO> order1 = List.of(err("Printer11", "А"), err("CamChecker", "Б"));
        List<AlertErrorDTO> order2 = List.of(err("CamChecker", "Б"), err("Printer11", "А"));

        assertThat(ActiveAlertStore.computeErrorSignature(order1))
                .isEqualTo(ActiveAlertStore.computeErrorSignature(order2));
    }

    @Test
    @DisplayName("signature: разные тексты того же устройства не меняют signature")
    void signature_sameDeviceDifferentMessages_sameSignature() {
        List<AlertErrorDTO> first = List.of(err("Printer11", "Сообщение A"));
        List<AlertErrorDTO> second = List.of(err("Printer11", "Сообщение B"));

        assertThat(ActiveAlertStore.computeErrorSignature(first))
                .isEqualTo(ActiveAlertStore.computeErrorSignature(second));
    }
}
