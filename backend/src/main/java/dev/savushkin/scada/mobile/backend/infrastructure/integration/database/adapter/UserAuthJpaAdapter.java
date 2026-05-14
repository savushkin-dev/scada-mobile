package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.UserAuthRepository;
import dev.savushkin.scada.mobile.backend.domain.model.AuthUser;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserAuthJpaAdapter implements UserAuthRepository {

    private final UserJpaRepository userRepository;

    public UserAuthJpaAdapter(UserJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public @NonNull Optional<AuthUser> findByCredentials(@NonNull String code, @NonNull String password) {
        return userRepository.findByCodeAndPassword(code, password)
                .map(this::toDomain);
    }

    private AuthUser toDomain(UserEntity entity) {
        return new AuthUser(
                entity.getId(),
                entity.getCode(),
                entity.getFullName(),
                entity.isActive()
        );
    }
}
