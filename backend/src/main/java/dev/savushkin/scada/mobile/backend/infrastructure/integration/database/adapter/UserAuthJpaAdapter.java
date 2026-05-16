package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.UserAuthRepository;
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
    public @NonNull Optional<AuthUserWithPassword> findByCode(@NonNull String code) {
        return userRepository.findByCode(code)
                .map(this::toDomain);
    }

    private AuthUserWithPassword toDomain(UserEntity entity) {
        return new AuthUserWithPassword(
                entity.getId(),
                entity.getCode(),
                entity.getFullName(),
                entity.isActive(),
                entity.getPassword()
        );
    }
}
