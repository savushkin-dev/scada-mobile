package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvInstancePolledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты детектора сигнала «последняя партия» по счётчику
 * {@code FinishBatch} устройства {@code Line}.
 * <p>
 * Событийная семантика: markserver инкрементирует счётчик при команде
 * {@code LINE_CMD_FINISH_BATCH (113)}, детектор срабатывает по изменению
 * значения между poll-циклами. Первое наблюдение — baseline без активации.
 * Уведомление живёт, пока сотрудник не снимет его вручную: детектор
 * deactivate не вызывает никогда (даже при потере снапшота — сбрасывается
 * только baseline счётчика).
 */
class LineBatchEndDetectorTest {

    private static final String INSTANCE_ID = "hassia2";
    private static final String OTHER_INSTANCE_ID = "hassia3";

    private InstanceSnapshotRepository snapshotRepository;
    private NotificationService notificationService;
    private LineBatchEndDetector detector;

    @BeforeEach
    void setUp() {
        snapshotRepository = mock(InstanceSnapshotRepository.class);
        notificationService = mock(NotificationService.class);
        detector = new LineBatchEndDetector(snapshotRepository, notificationService,
                new PrintSrvProperties());
    }

    /** Снапшот устройства Line с одним юнитом со значением счётчика FinishBatch. */
    private static DeviceSnapshot lineSnapshot(String finishBatch) {
        Map<String, String> raw = new LinkedHashMap<>();
        if (finishBatch != null) {
            raw.put("FinishBatch", finishBatch);
        }
        UnitProperties properties = UnitProperties.builder().rawProperties(raw).build();
        return new DeviceSnapshot("Line", Map.of("u1", new UnitSnapshot(1, "ok", "task", 0, properties)));
    }

    private void poll(String instanceId) {
        detector.onInstancePolled(new PrintSrvInstancePolledEvent(instanceId));
    }

    // ─── Кейс 1: первое наблюдение — baseline, активации нет ─────────────

    @Test
    @DisplayName("Первое наблюдение счётчика — только baseline, без активации и без снятия")
    void firstObservationIsBaselineOnly() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("5"));

        poll(INSTANCE_ID);

        verifyNoInteractions(notificationService);
    }

    // ─── Кейс 2: изменение счётчика — активация ───────────────────────────

    @Test
    @DisplayName("Изменение счётчика между poll-циклами активирует MACHINE-уведомление")
    void counterChangeActivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("5"));
        poll(INSTANCE_ID);

        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("6"));
        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 3: удержание значения — повторных вызовов нет ──────────────

    @Test
    @DisplayName("Неизменный счётчик: ни активации, ни снятия на повторных poll-циклах")
    void unchangedCounterIsNoOp() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("7"));

        poll(INSTANCE_ID);
        poll(INSTANCE_ID);
        poll(INSTANCE_ID);

        verifyNoInteractions(notificationService);
    }

    // ─── Кейс 4: циклический переход 100 → 1 — тоже событие ──────────────

    @Test
    @DisplayName("Циклический переход счётчика 100 → 1 детектируется как событие")
    void counterWrapAroundActivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("100"));
        poll(INSTANCE_ID);

        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
    }

    // ─── Кейс 5: уведомление живёт до ручного приёма ──────────────────────

    @Test
    @DisplayName("После активации уведомление не снимается детектором на сколь угодно долгих сериях poll-циклов")
    void notificationPersistsUntilManualAck() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        poll(INSTANCE_ID);
        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);

        // Долгая серия poll-циклов без изменения счётчика — снятия нет.
        for (int i = 0; i < 200; i++) {
            poll(INSTANCE_ID);
        }

        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
        // Повторных активаций тоже нет — идемпотентность.
        verify(notificationService, times(1)).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 6: snapshot == null — baseline сброшен, уведомление живёт ───

    @Test
    @DisplayName("Потеря снапшота: уведомление НЕ снимается (только вручную), baseline сбрасывается")
    void missingSnapshotKeepsNotificationAndResetsBaseline() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        poll(INSTANCE_ID);
        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);

        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(null);
        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());

        // Baseline сброшен: возврат того же значения — новое первое наблюдение,
        // ложной активации быть не должно.
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        poll(INSTANCE_ID);
        verify(notificationService, times(1)).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 7: свойство отсутствует — молчим ────────────────────────────

    @Test
    @DisplayName("Отсутствие свойства FinishBatch: ни активации, ни снятия")
    void absentPropertyIsNoOp() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(null));

        poll(INSTANCE_ID);

        verifyNoInteractions(notificationService);
    }

    // ─── Кейс 8: исключения глотаются ─────────────────────────────────────

    @Test
    @DisplayName("Исключение из NotificationService не роняет polling worker")
    void serviceExceptionIsSwallowedAndDoesNotPropagate() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        doThrow(new RuntimeException("DB is down"))
                .when(notificationService).activateMachineNotificationIfAbsent(any(), any());

        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
    }

    @Test
    @DisplayName("Исключение из репозитория не роняет polling worker")
    void repositoryExceptionIsSwallowedAndDoesNotPropagate() {
        when(snapshotRepository.get(INSTANCE_ID, "Line"))
                .thenThrow(new RuntimeException("store failure"));

        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();

        verifyNoInteractions(notificationService);
    }

    // ─── Кейс 9: разные instanceId изолированы ──────────────────────────

    @Test
    @DisplayName("Состояние детектора изолировано по instanceId")
    void instancesAreIsolated() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        when(snapshotRepository.get(OTHER_INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        poll(OTHER_INSTANCE_ID);

        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(eq(OTHER_INSTANCE_ID), any());
    }

    // ─── Кейс 10: кастомные deviceName/propertyName из конфигурации ───────

    @Test
    @DisplayName("Кастомные deviceName и propertyName из конфигурации используются детектором")
    void customDeviceAndPropertyFromConfigAreHonored() {
        PrintSrvProperties properties = new PrintSrvProperties();
        properties.getBatchEnd().setDeviceName("CustomLine");
        properties.getBatchEnd().setPropertyName("CustomCounter");
        LineBatchEndDetector customDetector = new LineBatchEndDetector(
                snapshotRepository, notificationService, properties);

        UnitProperties props = UnitProperties.builder()
                .rawProperties(Map.of("CustomCounter", "3")).build();
        DeviceSnapshot snapshot = new DeviceSnapshot("CustomLine",
                Map.of("u1", new UnitSnapshot(1, "ok", "task", 0, props)));
        when(snapshotRepository.get(INSTANCE_ID, "CustomLine")).thenReturn(snapshot);

        customDetector.onInstancePolled(new PrintSrvInstancePolledEvent(INSTANCE_ID));

        verify(snapshotRepository).get(INSTANCE_ID, "CustomLine");
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());

        UnitProperties changed = UnitProperties.builder()
                .rawProperties(Map.of("CustomCounter", "4")).build();
        when(snapshotRepository.get(INSTANCE_ID, "CustomLine")).thenReturn(
                new DeviceSnapshot("CustomLine",
                        Map.of("u1", new UnitSnapshot(1, "ok", "task", 0, changed))));
        customDetector.onInstancePolled(new PrintSrvInstancePolledEvent(INSTANCE_ID));

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
    }
}
