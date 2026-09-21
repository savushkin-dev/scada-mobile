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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты детектора сигнала «последняя партия» по счётчику
 * {@code FinishBatch} устройства {@code Line}.
 * <p>
 * Событийная семантика: markserver инкрементирует счётчик при команде
 * {@code LINE_CMD_FINISH_BATCH (113)}, детектор срабатывает по изменению
 * значения между poll-циклами. Первое наблюдение — baseline без активации.
 * Уведомление снимается по TTL (mutable clock) или при потере снапшота.
 */
class LineBatchEndDetectorTest {

    private static final String INSTANCE_ID = "hassia2";
    private static final String OTHER_INSTANCE_ID = "hassia3";
    private static final Instant T0 = Instant.parse("2026-09-21T10:00:00Z");

    private InstanceSnapshotRepository snapshotRepository;
    private NotificationService notificationService;
    private AtomicReference<Instant> now;
    private LineBatchEndDetector detector;

    @BeforeEach
    void setUp() {
        snapshotRepository = mock(InstanceSnapshotRepository.class);
        notificationService = mock(NotificationService.class);
        now = new AtomicReference<>(T0);
        Clock clock = new Clock() {
            @Override
            public Instant instant() {
                return now.get();
            }

            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }
        };
        detector = new LineBatchEndDetector(snapshotRepository, notificationService,
                new PrintSrvProperties(), clock);
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

    private void advanceMinutes(long minutes) {
        now.updateAndGet(t -> t.plusSeconds(minutes * 60));
    }

    // ─── Кейс 1: первое наблюдение — baseline, активации нет ─────────────

    @Test
    @DisplayName("Первое наблюдение счётчика — только baseline, без активации и без снятия")
    void firstObservationIsBaselineOnly() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("5"));

        poll(INSTANCE_ID);

        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 2: изменение счётчика — активация ───────────────────────────

    @Test
    @DisplayName("Изменение счётчика между poll-циклами активирует MACHINE-уведомление")
    void counterChangeActivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("5"));
        poll(INSTANCE_ID);

        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("6"));
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(true);
        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
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
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(true);
        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
    }

    // ─── Кейс 5: авто-снятие по TTL ───────────────────────────────────────

    @Test
    @DisplayName("По истечении TTL уведомление снимается автоматически")
    void notificationExpiresAfterTtl() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(true);
        poll(INSTANCE_ID);
        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);

        advanceMinutes(10);
        poll(INSTANCE_ID);

        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
    }

    @Test
    @DisplayName("До истечения TTL уведомление не снимается")
    void notificationKeptBeforeTtl() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(true);
        poll(INSTANCE_ID);

        advanceMinutes(9);
        poll(INSTANCE_ID);

        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 6: snapshot == null — снятие «зависшего» сигнала ───────────

    @Test
    @DisplayName("Потеря снапшота снимает активное уведомление и сбрасывает baseline")
    void missingSnapshotDeactivatesAndResetsBaseline() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(true);
        poll(INSTANCE_ID);

        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(null);
        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();
        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);

        // Baseline сброшен: возврат того же значения — новое первое наблюдение,
        // ложной активации быть не должно.
        clearInvocations(notificationService);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        poll(INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 7: свойство отсутствует — как потеря сигнала ────────────────

    @Test
    @DisplayName("Отсутствие свойства FinishBatch трактуется как потеря сигнала")
    void absentPropertyDeactivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(null));

        poll(INSTANCE_ID);

        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 8: активация отклонена (USER-уведомление) — TTL не трекается ─

    @Test
    @DisplayName("Если активация отклонена (активно USER-уведомление), TTL-снятие не планируется")
    void declinedActivationIsNotTrackedForTtl() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(false);
        poll(INSTANCE_ID);

        advanceMinutes(60);
        poll(INSTANCE_ID);

        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 9: исключения глотаются ─────────────────────────────────────

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

    // ─── Кейс 10: разные instanceId изолированы ──────────────────────────

    @Test
    @DisplayName("Состояние детектора изолировано по instanceId")
    void instancesAreIsolated() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        when(snapshotRepository.get(OTHER_INSTANCE_ID, "Line")).thenReturn(lineSnapshot("1"));
        poll(INSTANCE_ID);
        poll(OTHER_INSTANCE_ID);

        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot("2"));
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(true);
        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(eq(OTHER_INSTANCE_ID), any());
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 11: кастомные deviceName/propertyName из конфигурации ───────

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
        when(notificationService.activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID))
                .thenReturn(true);
        customDetector.onInstancePolled(new PrintSrvInstancePolledEvent(INSTANCE_ID));

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
    }
}
