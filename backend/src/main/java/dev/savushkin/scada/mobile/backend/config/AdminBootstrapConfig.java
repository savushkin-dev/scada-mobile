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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Bootstrap-компонент для создания начального администратора.
 * <p>
 * При первом запуске приложения, если в БД отсутствуют роли и пользователи,
 * создаётся роль ADMIN и пользователь-администратор с автоматически
 * сгенерированным кодом и паролем.
 * <p>
 * Сгенерированные учётные данные записываются в файл {@code /app/admin-credentials.txt}
 * внутри контейнера. Файл можно прочитать через:
 * <pre>
 *   docker exec scada-mobile-backend-1 cat /app/admin-credentials.txt
 * </pre>
 * <p>
 * Это позволяет развёртывать приложение в production без предварительного
 * seed-скрипта: администратор входит с сгенерированными учётными данными
 * и создаёт остальных пользователей через админ-панель.
 */
@Component
public class AdminBootstrapConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);
    private static final String ADMIN_ROLE_NAME = "ADMIN";
    private static final String ADMIN_FULL_NAME = "System Administrator";
    private static final int RANDOM_PASSWORD_BYTES = 12;

    /** Путь к файлу с учётными данными администратора внутри контейнера. */
    public static final Path CREDENTIALS_FILE = Path.of("/app/admin-credentials.txt");

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
        if (userRepository.count() > 0) {
            log.info("Bootstrap: users already exist in database, skipping admin creation");
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

        String adminCode = generateRandomCode();
        String rawPassword = generateRandomPassword();
        String passwordHash = passwordEncoder.encode(rawPassword);

        UserEntity admin = new UserEntity();
        admin.setRole(adminRole);
        admin.setCode(adminCode);
        admin.setPassword(passwordHash);
        admin.setFullName(ADMIN_FULL_NAME);
        admin.setActive(true);
        userRepository.save(admin);

        // Записываем учётные данные в файл
        writeCredentialsFile(adminCode, rawPassword);

        log.info("Bootstrap: initial admin account created (code={})", adminCode);
    }

    private void writeCredentialsFile(String code, String password) {
        try {
            String content = """
                    SCADA Mobile — Initial Admin Credentials
                    =========================================
                    Code:     %s
                    Password: %s
                    =========================================
                    Save these credentials securely. This file is created only once.
                    """.formatted(code, password);
            Files.writeString(CREDENTIALS_FILE, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Bootstrap: admin credentials written to {}", CREDENTIALS_FILE);
        } catch (Exception e) {
            log.warn("Bootstrap: unable to write credentials file {}: {}", CREDENTIALS_FILE, e.getMessage());
        }
    }

    private String generateRandomCode() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[RANDOM_PASSWORD_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
