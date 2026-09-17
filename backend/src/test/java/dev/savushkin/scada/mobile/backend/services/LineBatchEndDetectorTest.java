package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvInstancePolledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты детектора сигнала «последняя партия» по {@code Line.command = 113}.
 * <p>
 * Уровневая семантика: детектор на каждом poll-цикле сводит уровень сигнала
 * (снапшот) с уровнем уведомления (БД, здесь — мок {@link NotificationService}).
 * Идемпотентность повторных активаций — на совести сервиса, поэтому при
 * удержании {@code 113} activate вызывается каждый раз, а deactivate — никогда.
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

    /** Снапшот устройства Line с одним юнитом с заданным command. */
    private static DeviceSnapshot lineSnapshot(Integer command) {
        return lineSnapshot(new Integer[]{command});
    }

    /** Снапшот устройства Line с несколькими юнитами (u1, u2, ...). */
    private static DeviceSnapshot lineSnapshot(Integer... commands) {
        Map<String, UnitSnapshot> units = new LinkedHashMap<>();
        for (int i = 0; i < commands.length; i++) {
            UnitProperties properties = UnitProperties.builder().command(commands[i]).build();
            units.put("u" + (i + 1), new UnitSnapshot(i + 1, "ok", "task", 0, properties));
        }
        return new DeviceSnapshot("Line", units);
    }

    private void poll(String instanceId) {
        detector.onInstancePolled(new PrintSrvInstancePolledEvent(instanceId));
    }

    // ─── Кейс 1: 0 → 113 — активация ─────────────────────────────────────

    @Test
    void transitionFromZeroToOneHundredThirteenActivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(0));
        poll(INSTANCE_ID);
        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());

        clearInvocations(notificationService);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(113));
        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 2: 113 → 113 → 113 — удержание, деактиватор не вызывается ──

    @Test
    void heldOneHundredThirteenActivatesOnEachPollWithoutDeactivate() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(113));

        poll(INSTANCE_ID);
        poll(INSTANCE_ID);
        poll(INSTANCE_ID);

        verify(notificationService, times(3)).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 3: 113 → 0 — деактивация ───────────────────────────────────

    @Test
    void transitionFromOneHundredThirteenToZeroDeactivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(113));
        poll(INSTANCE_ID);
        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);

        clearInvocations(notificationService);
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(0));
        poll(INSTANCE_ID);

        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 4: 0 → 0 — уровневая сверка, deactivate вызывается ─────────

    @Test
    void heldZeroDeactivatesOnEachPollWithoutActivate() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(0));

        poll(INSTANCE_ID);
        poll(INSTANCE_ID);

        verify(notificationService, times(2)).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 5: snapshot == null — снятие «зависшего» сигнала ───────────

    @Test
    void missingSnapshotDeactivatesWithoutExceptions() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(null);

        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();

        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 6: command == null (тег отсутствует) — деактивация ─────────

    @Test
    void absentCommandTagDeactivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot((Integer) null));

        poll(INSTANCE_ID);

        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
    }

    // ─── Кейс 7: код != 113 (например, 555) — не активируем ──────────────

    @Test
    void foreignCommandCodeDeactivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(555));

        poll(INSTANCE_ID);

        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
    }

    // ─── Кейс 8: исключение из NotificationService глотается ─────────────

    @Test
    void serviceExceptionIsSwallowedAndDoesNotPropagate() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(113));
        doThrow(new RuntimeException("DB is down"))
                .when(notificationService).activateMachineNotificationIfAbsent(any(), any());

        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
    }

    /** Исключение из репозитория тоже не должно ронять polling worker. */
    @Test
    void repositoryExceptionIsSwallowedAndDoesNotPropagate() {
        when(snapshotRepository.get(INSTANCE_ID, "Line"))
                .thenThrow(new RuntimeException("store failure"));

        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();

        verifyNoInteractions(notificationService);
    }

    // ─── Кейс 9: несколько юнитов, хотя бы один 113 — активен ────────────

    @Test
    void anyUnitWithOneHundredThirteenActivates() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(0, 113));

        poll(INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(any(), any());
    }

    // ─── Кейс 10: разные instanceId изолированы друг от друга ────────────

    @Test
    void instancesAreIsolated() {
        when(snapshotRepository.get(INSTANCE_ID, "Line")).thenReturn(lineSnapshot(113));
        when(snapshotRepository.get(OTHER_INSTANCE_ID, "Line")).thenReturn(lineSnapshot(0));

        poll(INSTANCE_ID);
        poll(OTHER_INSTANCE_ID);

        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService).deactivateMachineNotificationIfPresent(OTHER_INSTANCE_ID, OTHER_INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(eq(OTHER_INSTANCE_ID), any());
        verify(notificationService, never()).deactivateMachineNotificationIfPresent(eq(INSTANCE_ID), any());
    }

    // ─── Кейс 11: устройство называется не Line → снапшот не найден ──────

    @Test
    void otherDeviceNameIsLookedUpByConfiguredNameAndTreatedAsMissing() {
        // Репозиторий отдаёт снапшот только по запрошенному имени устройства;
        // для "Line" (конфиг по умолчанию) возвращается null.
        when(snapshotRepository.get(INSTANCE_ID, "OtherDevice"))
                .thenReturn(new DeviceSnapshot("OtherDevice", Map.of(
                        "u1", new UnitSnapshot(1, "ok", "task", 0,
                                UnitProperties.builder().command(113).build()))));

        assertThatCode(() -> poll(INSTANCE_ID)).doesNotThrowAnyException();

        // Детектор обязан искать устройство по имени из конфигурации ("Line").
        verify(snapshotRepository).get(INSTANCE_ID, "Line");
        // Снапшот не найден → поведение как при потере сигнала: снятие.
        verify(notificationService).deactivateMachineNotificationIfPresent(INSTANCE_ID, INSTANCE_ID);
        verify(notificationService, never()).activateMachineNotificationIfAbsent(any(), any());
    }

    /** Кастомный deviceName/commandCode из конфигурации используется детектором. */
    @Test
    void customDeviceNameAndCommandCodeFromConfigAreHonored() {
        PrintSrvProperties properties = new PrintSrvProperties();
        properties.getBatchEnd().setDeviceName("CustomLine");
        properties.getBatchEnd().setCommandCode(200);
        LineBatchEndDetector customDetector = new LineBatchEndDetector(
                snapshotRepository, notificationService, properties);

        when(snapshotRepository.get(INSTANCE_ID, "CustomLine"))
                .thenReturn(new DeviceSnapshot("CustomLine", Map.of(
                        "u1", new UnitSnapshot(1, "ok", "task", 0,
                                UnitProperties.builder().command(200).build()))));

        customDetector.onInstancePolled(new PrintSrvInstancePolledEvent(INSTANCE_ID));

        verify(snapshotRepository).get(INSTANCE_ID, "CustomLine");
        verify(notificationService).activateMachineNotificationIfAbsent(INSTANCE_ID, INSTANCE_ID);
    }
}
