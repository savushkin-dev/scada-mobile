package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationCreatorType;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import dev.savushkin.scada.mobile.backend.services.NotificationService.NotificationAccessDeniedException;
import dev.savushkin.scada.mobile.backend.services.NotificationService.ToggleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Test
    void pendingNotificationCanBeAcceptedByUser() {
        ProductionNotification notification = ProductionNotification.activate(UNIT_ID, "42");

        ProductionNotification accepted = notification.accept("77");

        assertThat(accepted.status()).isEqualTo(dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus.IN_PROGRESS);
        assertThat(accepted.active()).isTrue();
        assertThat(accepted.acceptedBy()).isEqualTo("77");
        assertThat(accepted.acceptedAt()).isNotNull();
        assertThat(accepted.version()).isEqualTo(1L);
    }

    @Test
    void acceptedNotificationCanBeCompletedByCreatorOrAssignee() {
        ProductionNotification accepted = ProductionNotification.activate(UNIT_ID, "42").accept("77");

        ProductionNotification completed = accepted.complete("77");

        assertThat(completed.status()).isEqualTo(dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus.COMPLETED);
        assertThat(completed.active()).isFalse();
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void pendingNotificationCanBeCancelledOnlyByCreator() {
        ProductionNotification notification = ProductionNotification.activate(UNIT_ID, "42");

        ProductionNotification cancelled = notification.cancel("42");

        assertThat(cancelled.status()).isEqualTo(dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus.CANCELLED);
        assertThat(cancelled.active()).isFalse();
        assertThat(cancelled.cancelledAt()).isNotNull();
    }

    @Test
    void completedNotificationCannotBeCancelledOrCompletedAgain() {
        ProductionNotification completed = ProductionNotification.activate(UNIT_ID, "42")
                .accept("77")
                .complete("77");

        assertThatThrownBy(() -> completed.cancel("42"))
                .isInstanceOf(ProductionNotification.NotificationTransitionException.class);
        assertThatThrownBy(() -> completed.complete("42"))
                .isInstanceOf(ProductionNotification.NotificationTransitionException.class);
    }

    // ─── Workflow на уровне сервиса (права + переходы) ──────────────────

    private static final long NOTIFICATION_ID = 1001L;

    private ProductionNotification persisted(ProductionNotification notification) {
        return new ProductionNotification(
                NOTIFICATION_ID, notification.unitId(), notification.creatorId(),
                notification.creatorType(), notification.status(), notification.active(),
                notification.activatedAt(), notification.deactivatedAt(),
                notification.acceptedBy(), notification.acceptedAt(),
                notification.completedAt(), notification.cancelledAt(), notification.version());
    }

    private void stubNotification(ProductionNotification notification) {
        when(notificationRepository.findByNotificationId(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
    }

    @Test
    void subscribedUserAcceptsPendingNotification() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42")));
        when(userAssignmentRepository.getSubscribedUnitIds(77L)).thenReturn(Set.of(UNIT_ID));

        ProductionNotification accepted = service.acceptNotification(NOTIFICATION_ID, 77L);

        assertThat(accepted.status()).isEqualTo(NotificationStatus.IN_PROGRESS);
        assertThat(accepted.acceptedBy()).isEqualTo("77");
        verify(notificationRepository).save(any());
        assertPublishedEvent(NotificationStateChangedEvent.EventType.ACCEPTED);
    }

    @Test
    void subscribedButNotAssignedUserCanAccept() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42")));
        // Подписка на "Вызов" от аппарата есть, закрепления может и не быть.
        when(userAssignmentRepository.getSubscribedUnitIds(88L)).thenReturn(Set.of(UNIT_ID));
        when(userAssignmentRepository.canSendNotification(88L, UNIT_ID)).thenReturn(false);

        ProductionNotification accepted = service.acceptNotification(NOTIFICATION_ID, 88L);

        assertThat(accepted.status()).isEqualTo(NotificationStatus.IN_PROGRESS);
        assertThat(accepted.acceptedBy()).isEqualTo("88");
        verify(notificationRepository).save(any());
        assertPublishedEvent(NotificationStateChangedEvent.EventType.ACCEPTED);
    }

    @Test
    void unsubscribedUserCannotAccept() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42")));
        when(userAssignmentRepository.getSubscribedUnitIds(77L)).thenReturn(Set.of("other-unit"));

        assertThatThrownBy(() -> service.acceptNotification(NOTIFICATION_ID, 77L))
                .isInstanceOf(NotificationAccessDeniedException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void cannotAcceptAlreadyAcceptedNotification() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42").accept("77")));
        when(userAssignmentRepository.getSubscribedUnitIds(78L)).thenReturn(Set.of(UNIT_ID));

        assertThatThrownBy(() -> service.acceptNotification(NOTIFICATION_ID, 78L))
                .isInstanceOf(ProductionNotification.NotificationTransitionException.class);
    }

    @Test
    void cannotCompleteBeforeAcceptance() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42")));

        assertThatThrownBy(() -> service.completeNotification(NOTIFICATION_ID, 42L))
                .isInstanceOf(ProductionNotification.NotificationTransitionException.class);
    }

    @Test
    void creatorAndExecutorCanCompleteAcceptedNotification() {
        ProductionNotification accepted = persisted(ProductionNotification.activate(UNIT_ID, "42").accept("77"));

        stubNotification(accepted);
        assertThat(service.completeNotification(NOTIFICATION_ID, 42L).status())
                .isEqualTo(NotificationStatus.COMPLETED);

        stubNotification(accepted);
        assertThat(service.completeNotification(NOTIFICATION_ID, 77L).status())
                .isEqualTo(NotificationStatus.COMPLETED);
    }

    @Test
    void thirdPartyCannotCompleteAcceptedNotification() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42").accept("77")));

        assertThatThrownBy(() -> service.completeNotification(NOTIFICATION_ID, 78L))
                .isInstanceOf(NotificationAccessDeniedException.class);
    }

    @Test
    void creatorCancelsPendingNotification() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42")));

        ProductionNotification cancelled = service.cancelNotification(NOTIFICATION_ID, 42L);

        assertThat(cancelled.status()).isEqualTo(NotificationStatus.CANCELLED);
        assertPublishedEvent(NotificationStateChangedEvent.EventType.CANCELLED);
    }

    @Test
    void nonCreatorCannotCancelNotification() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42")));

        assertThatThrownBy(() -> service.cancelNotification(NOTIFICATION_ID, 77L))
                .isInstanceOf(NotificationAccessDeniedException.class);
    }

    @Test
    void acceptedNotificationCannotBeCancelled() {
        stubNotification(persisted(ProductionNotification.activate(UNIT_ID, "42").accept("77")));

        assertThatThrownBy(() -> service.cancelNotification(NOTIFICATION_ID, 42L))
                .isInstanceOf(ProductionNotification.NotificationTransitionException.class);
    }

    @Test
    void incomingContainsOnlyPendingFromSubscribedUnits() {
        ProductionNotification pendingMine = persisted(ProductionNotification.activate(UNIT_ID, "42"));
        ProductionNotification acceptedMine = persisted(pendingMine.accept("77"));
        ProductionNotification pendingOtherUnit = new ProductionNotification(
                1002L, "hassia2", "43", NotificationCreatorType.USER, NotificationStatus.PENDING,
                true, pendingMine.activatedAt(), null, null, null, null, null, 0L);
        when(notificationRepository.findAllActive())
                .thenReturn(List.of(pendingMine, acceptedMine, pendingOtherUnit));
        when(userAssignmentRepository.getSubscribedUnitIds(77L)).thenReturn(Set.of(UNIT_ID));

        List<ProductionNotification> incoming = service.getIncoming(77L);

        assertThat(incoming).containsExactly(pendingMine);
    }

    private ProductionNotification captureSaved() {
        ArgumentCaptor<ProductionNotification> captor = ArgumentCaptor.forClass(ProductionNotification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertPublishedEvent(NotificationStateChangedEvent.EventType expectedType) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(NotificationStateChangedEvent.class::isInstance)
                .map(NotificationStateChangedEvent.class::cast)
                .anySatisfy(event -> assertThat(event.type()).isEqualTo(expectedType));
    }
}
