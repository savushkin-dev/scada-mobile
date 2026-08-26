package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationCreatorType;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import dev.savushkin.scada.mobile.backend.services.NotificationService.NotificationAccessDeniedException;
import dev.savushkin.scada.mobile.backend.services.NotificationService.ToggleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты доменной логики toggle «последняя партия» (работник и автомат/СКАДА).
 */
class NotificationServiceTest {

    private static final String UNIT_ID = "hassia1";

    private NotificationRepository notificationRepository;
    private UserAssignmentRepository userAssignmentRepository;
    private ApplicationEventPublisher eventPublisher;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userAssignmentRepository = mock(UserAssignmentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new NotificationService(notificationRepository, userAssignmentRepository, eventPublisher);
    }

    // ─── Работник (USER) ─────────────────────────────────────────────────

    @Test
    void userWithoutAssignmentIsDenied() {
        when(userAssignmentRepository.canSendNotification(42L, UNIT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.toggleNotification(UNIT_ID, 42L))
                .isInstanceOf(NotificationAccessDeniedException.class);

        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void userActivatesNotification() {
        when(userAssignmentRepository.canSendNotification(42L, UNIT_ID)).thenReturn(true);
        when(notificationRepository.findActiveByUnitId(UNIT_ID)).thenReturn(Optional.empty());

        ToggleResult result = service.toggleNotification(UNIT_ID, 42L);

        assertThat(result).isInstanceOf(ToggleResult.Activated.class);
        ProductionNotification saved = captureSaved();
        assertThat(saved.active()).isTrue();
        assertThat(saved.creatorType()).isEqualTo(NotificationCreatorType.USER);
        assertThat(saved.creatorId()).isEqualTo("42");
        verify(eventPublisher).publishEvent(any(NotificationStateChangedEvent.class));
    }

    @Test
    void sameUserDeactivatesOwnNotification() {
        when(userAssignmentRepository.canSendNotification(42L, UNIT_ID)).thenReturn(true);
        when(notificationRepository.findActiveByUnitId(UNIT_ID))
                .thenReturn(Optional.of(ProductionNotification.activate(UNIT_ID, "42")));

        ToggleResult result = service.toggleNotification(UNIT_ID, 42L);

        assertThat(result).isInstanceOf(ToggleResult.Deactivated.class);
        ProductionNotification saved = captureSaved();
        assertThat(saved.active()).isFalse();
        assertThat(saved.deactivatedAt()).isNotNull();
    }

    @Test
    void userCannotDeactivateNotificationOfOtherCreator() {
        when(userAssignmentRepository.canSendNotification(42L, UNIT_ID)).thenReturn(true);
        when(notificationRepository.findActiveByUnitId(UNIT_ID))
                .thenReturn(Optional.of(ProductionNotification.activate(UNIT_ID, "7")));

        ToggleResult result = service.toggleNotification(UNIT_ID, 42L);

        assertThat(result).isInstanceOf(ToggleResult.AlreadyActiveByOther.class);
        assertThat(((ToggleResult.AlreadyActiveByOther) result).existingCreatorId()).isEqualTo("7");
        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── Автомат / СКАДА (MACHINE) ───────────────────────────────────────

    @Test
    void machineActivatesWithoutAssignmentCheck() {
        when(notificationRepository.findActiveByUnitId(UNIT_ID)).thenReturn(Optional.empty());

        ToggleResult result = service.toggleMachineNotification(UNIT_ID, UNIT_ID);

        assertThat(result).isInstanceOf(ToggleResult.Activated.class);
        ProductionNotification saved = captureSaved();
        assertThat(saved.creatorType()).isEqualTo(NotificationCreatorType.MACHINE);
        assertThat(saved.creatorId()).isEqualTo(UNIT_ID);
        // Права по user_unit_assignments для автомата не проверяются
        verifyNoInteractions(userAssignmentRepository);
    }

    @Test
    void sameMachineDeactivatesOwnNotification() {
        when(notificationRepository.findActiveByUnitId(UNIT_ID))
                .thenReturn(Optional.of(ProductionNotification.activateAsMachine(UNIT_ID, UNIT_ID)));

        ToggleResult result = service.toggleMachineNotification(UNIT_ID, UNIT_ID);

        assertThat(result).isInstanceOf(ToggleResult.Deactivated.class);
        ProductionNotification saved = captureSaved();
        assertThat(saved.active()).isFalse();
    }

    @Test
    void machineCannotDeactivateUserNotification() {
        when(notificationRepository.findActiveByUnitId(UNIT_ID))
                .thenReturn(Optional.of(ProductionNotification.activate(UNIT_ID, "42")));

        ToggleResult result = service.toggleMachineNotification(UNIT_ID, UNIT_ID);

        assertThat(result).isInstanceOf(ToggleResult.AlreadyActiveByOther.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void machineCannotReactivateWhileActiveByOtherMachine() {
        when(notificationRepository.findActiveByUnitId(UNIT_ID))
                .thenReturn(Optional.of(ProductionNotification.activateAsMachine(UNIT_ID, "other-machine")));

        ToggleResult result = service.toggleMachineNotification(UNIT_ID, UNIT_ID);

        assertThat(result).isInstanceOf(ToggleResult.AlreadyActiveByOther.class);
        verify(notificationRepository, never()).save(any());
    }

    // ─── Чтение состояния ────────────────────────────────────────────────

    @Test
    void getActiveNotificationDelegatesToRepository() {
        ProductionNotification active = ProductionNotification.activate(UNIT_ID, "42");
        when(notificationRepository.findActiveByUnitId(UNIT_ID)).thenReturn(Optional.of(active));

        assertThat(service.getActiveNotification(UNIT_ID)).contains(active);
        assertThat(service.getActiveNotification("unknown")).isEmpty();
    }

    private ProductionNotification captureSaved() {
        ArgumentCaptor<ProductionNotification> captor = ArgumentCaptor.forClass(ProductionNotification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }
}
