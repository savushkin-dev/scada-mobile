package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.config.jwt.JwtProperties;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtTokenProvider;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.MachineTokenEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.MachineTokenJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Сервис управления machine-токенами (автоматы / СКАДА).
 * <p>
 * Токен — долгоживущий JWT с {@code subject_type = "machine"} и {@code sub} =
 * PrintSrv instance id автомата. Выдаётся администратором через
 * {@code POST /admin/machine-tokens}; реестр выданных токенов хранится в таблице
 * {@code machine_tokens} (по {@code jti}), что позволяет отозвать токен до истечения
 * срока ({@code DELETE /admin/machine-tokens/{jti}}).
 * <p>
 * Проверка отзыва выполняется на каждом запросе с machine-JWT:
 * в HTTP — {@code MachineTokenRevocationFilter}, в WebSocket — {@code WebSocketJwtInterceptor}.
 */
@Service
public class MachineTokenService {

    private static final Logger log = LoggerFactory.getLogger(MachineTokenService.class);

    private final MachineTokenJpaRepository machineTokenRepository;
    private final UnitJpaRepository unitRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public MachineTokenService(MachineTokenJpaRepository machineTokenRepository,
                               UnitJpaRepository unitRepository,
                               JwtTokenProvider jwtTokenProvider,
                               JwtProperties jwtProperties) {
        this.machineTokenRepository = machineTokenRepository;
        this.unitRepository = unitRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Выдаёт новый machine-токен для аппарата и регистрирует его в реестре.
     *
     * @param unitId    числовой id аппарата
     * @param issuedBy  id администратора, выдавшего токен
     * @param ttlDays   срок жизни в днях; {@code null} — дефолт из конфигурации
     *                  ({@code jwt.machine-token-expiration-days})
     * @return выданный токен с метаданными, либо {@code Optional.empty()},
     *         если аппарат не найден или у него нет PrintSrv instance id
     */
    @Transactional
    public @NonNull Optional<IssuedMachineToken> issueToken(long unitId, long issuedBy, Integer ttlDays) {
        Optional<UnitEntity> unitOpt = unitRepository.findById(unitId);
        if (unitOpt.isEmpty()
                || unitOpt.get().getPrintsrvInstanceId() == null
                || unitOpt.get().getPrintsrvInstanceId().isBlank()) {
            log.warn("Machine token issue rejected: unitId='{}' not found or has no PrintSrv instance id", unitId);
            return Optional.empty();
        }
        UnitEntity unit = unitOpt.get();

        long effectiveTtlDays = ttlDays != null ? ttlDays : jwtProperties.getMachineTokenExpirationDays();
        JwtTokenProvider.MachineToken generated =
                jwtTokenProvider.generateMachineToken(unit.getPrintsrvInstanceId(), effectiveTtlDays);

        MachineTokenEntity entity = new MachineTokenEntity();
        entity.setUnitId(unitId);
        entity.setJti(generated.jti());
        entity.setIssuedBy(issuedBy);
        entity.setIssuedAt(LocalDateTime.now(ZoneOffset.UTC));
        entity.setExpiresAt(LocalDateTime.ofInstant(generated.expiresAt(), ZoneOffset.UTC));
        machineTokenRepository.save(entity);

        log.info("Machine token issued: unitId='{}', printsrv='{}', jti='{}', expiresAt='{}', by userId='{}'",
                unitId, unit.getPrintsrvInstanceId(), generated.jti(), generated.expiresAt(), issuedBy);

        return Optional.of(new IssuedMachineToken(
                generated.token(),
                generated.jti(),
                unitId,
                unit.getPrintsrvInstanceId(),
                generated.expiresAt()
        ));
    }

    /**
     * Отзывает токен по {@code jti}: с этого момента запросы с ним отклоняются (401).
     *
     * @return {@code true}, если токен найден и отозван; {@code false} если не найден
     */
    @Transactional
    public boolean revokeToken(@NonNull String jti) {
        Optional<MachineTokenEntity> tokenOpt = machineTokenRepository.findByJti(jti);
        if (tokenOpt.isEmpty()) {
            return false;
        }
        MachineTokenEntity token = tokenOpt.get();
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
            machineTokenRepository.save(token);
        }
        log.info("Machine token revoked: jti='{}', unitId='{}'", jti, token.getUnitId());
        return true;
    }

    /**
     * Проверяет, что machine-токен зарегистрирован, не отозван и не истёк.
     * Вызывается на каждом запросе с machine-JWT (HTTP и WebSocket).
     */
    @Transactional(readOnly = true)
    public boolean isTokenActive(@NonNull String jti) {
        return machineTokenRepository.findByJti(jti)
                .filter(token -> token.getRevokedAt() == null)
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .isPresent();
    }

    /**
     * Возвращает реестр выданных токенов (без значений самих токенов).
     */
    @Transactional(readOnly = true)
    public @NonNull List<MachineTokenEntity> listTokens() {
        return machineTokenRepository.findAll().stream()
                .sorted(Comparator.comparing(MachineTokenEntity::getIssuedAt).reversed())
                .toList();
    }

    /**
     * Выданный machine-токен с метаданными.
     *
     * @param token              подписанный JWT (возвращается один раз, при выдаче)
     * @param jti                идентификатор токена для отзыва
     * @param unitId             числовой id аппарата
     * @param printsrvInstanceId PrintSrv instance id аппарата (sub токена)
     * @param expiresAt          время истечения
     */
    public record IssuedMachineToken(
            String token,
            String jti,
            long unitId,
            String printsrvInstanceId,
            Instant expiresAt
    ) {
    }
}
