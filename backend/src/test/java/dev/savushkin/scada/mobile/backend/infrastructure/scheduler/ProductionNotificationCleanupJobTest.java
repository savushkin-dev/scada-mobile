package dev.savushkin.scada.mobile.backend.infrastructure.scheduler;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.ProductionNotificationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты ночной очистки производственных уведомлений.
 *
 * <p>Ключевой инвариант: очистка удаляет только <b>неактивные</b> записи
 * ({@code deleteByActiveFalseAndActivatedAtBefore}) — активные уведомления
 * никогда не трогаются, иначе WS-прожекция рассинхронизируется с БД и клиенты
 * получат «фантомные» уведомления, на которые невозможно ответить.
 */
class ProductionNotificationCleanupJobTest {

    private ProductionNotificationJpaRepository notificationRepository;
    private ProductionNotificationCleanupJob job;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(ProductionNotificationJpaRepository.class);
        job = new ProductionNotificationCleanupJob(notificationRepository);
    }

    @Test
    void cleanupDeletesOnlyInactiveNotifications() {
        when(notificationRepository.deleteByActiveFalseAndActivatedAtBefore(any())).thenReturn(2L);

        job.cleanupOldNotifications();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).deleteByActiveFalseAndActivatedAtBefore(cutoffCaptor.capture());

        LocalDateTime cutoff = cutoffCaptor.getValue();
        long ageHours = ChronoUnit.HOURS.between(cutoff, LocalDateTime.now());
        assertThat(ageHours).isEqualTo(24);
    }
}
