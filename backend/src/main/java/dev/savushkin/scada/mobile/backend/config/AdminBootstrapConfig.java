package dev.savushkin.scada.mobile.backend.config;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.RoleJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap-компонент для создания начального администратора.
 * <p>
 * При запуске приложения проверяется, есть ли в БД хотя бы один пользователь
 * с ролью ADMIN. Если нет — создаётся роль ADMIN (если ещё не существует)
 * и пользователь-администратор с кодом и паролем из переменных окружения:
 * <ul>
 *   <li>{@code SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE}</li>
 *   <li>{@code SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD}</li>
 * </ul>
 * <p>
 * Если переменные окружения не заданы, bootstrap пропускается.
 */
@Component
public class AdminBootstrapConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);
    private static final String ADMIN_ROLE_NAME = "ADMIN";
    private static final String ADMIN_FULL_NAME = "System Administrator";

    /** Имя переменной окружения для кода (логина) администратора. */
    public static final String ENV_ADMIN_CODE = "SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE";
    /** Имя переменной окружения для пароля администратора. */
    public static final String ENV_ADMIN_PASSWORD = "SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD";

    private final RoleJpaRepository roleRepository;
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapConfig(RoleJpaRepository roleRepository,
                                UserJpaRepository userRepository,
                                PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hasAdminUser()) {
            log.info("Bootstrap: admin user already exists, skipping admin creation");
            return;
        }

        String adminCode = System.getenv(ENV_ADMIN_CODE);
        String adminPassword = System.getenv(ENV_ADMIN_PASSWORD);

        if (adminCode == null || adminCode.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Bootstrap: no admin user found, but env variables {} and/or {} are not set. Skipping.",
                    ENV_ADMIN_CODE, ENV_ADMIN_PASSWORD);
            return;
        }

        RoleEntity adminRole = roleRepository.findAll().stream()
                .filter(r -> ADMIN_ROLE_NAME.equals(r.getName()))
                .findFirst()
                .orElseGet(() -> {
                    RoleEntity role = new RoleEntity();
                    role.setName(ADMIN_ROLE_NAME);
                    return roleRepository.save(role);
                });

        String passwordHash = passwordEncoder.encode(adminPassword);

        UserEntity admin = new UserEntity();
        admin.setRole(adminRole);
        admin.setCode(adminCode);
        admin.setPassword(passwordHash);
        admin.setFullName(ADMIN_FULL_NAME);
        admin.setActive(true);
        userRepository.save(admin);

        log.info("Bootstrap: initial admin account created (code={})", adminCode);
    }

    /**
     * Проверяет, существует ли в БД пользователь с ролью ADMIN.
     */
    private boolean hasAdminUser() {
        return userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() != null && ADMIN_ROLE_NAME.equals(u.getRole().getName()));
    }
}
