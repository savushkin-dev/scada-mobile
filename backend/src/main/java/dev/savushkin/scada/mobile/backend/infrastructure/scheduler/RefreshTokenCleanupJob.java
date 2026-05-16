package dev.savushkin.scada.mobile.backend.infrastructure.scheduler;

import dev.savushkin.scada.mobile.backend.application.ports.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Периодическая очистка истёкших refresh-токенов из БД.
 * <p>
 * Запускается раз в день (02:00 ночи) и удаляет токены, срок действия
 * которых истёк более 7 дней назад. Это предотвращает бесконечный рост
 * таблицы refresh_tokens.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Удаляет истёкшие refresh-токены старше 7 дней.
     * <p>
     * Расписание: каждый день в 02:00.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minusSeconds(7 * 24 * 60 * 60); // 7 days ago
        log.debug("Starting refresh token cleanup, cutoff={}", cutoff);
        refreshTokenRepository.deleteExpired(cutoff);
        log.info("Refresh token cleanup completed, cutoff={}", cutoff);
    }
}
