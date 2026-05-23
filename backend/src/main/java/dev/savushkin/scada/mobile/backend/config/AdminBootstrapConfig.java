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
import java.util.List;

/**
 * Bootstrap-компонент для создания начального администратора.
 * <p>
 * При первом запуске приложения, если в БД отсутствует пользователь с ролью ADMIN,
 * создаётся роль ADMIN (если ещё не существует) и пользователь-администратор
 * с автоматически сгенерированным кодом и паролем.
 * <p>
 * Сгенерированные учётные данные дописываются в файл {@code .env.prod.local}
 * в корне проекта (переменные {@code SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE}
 * и {@code SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD}).
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

    /** Имя переменной окружения для кода (логина) администратора. */
    public static final String ENV_ADMIN_CODE = "SCADA_MOBILE_ADMIN_BOOTSTRAP_CODE";
    /** Имя переменной окружения для пароля администратора. */
    public static final String ENV_ADMIN_PASSWORD = "SCADA_MOBILE_ADMIN_BOOTSTRAP_PASSWORD";

    /** Путь к файлу .env.prod.local относительно корня проекта. */
    public static final Path ENV_FILE = Path.of(".env.prod.local");

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

        // Записываем учётные данные в .env.prod.local
        writeEnvFile(adminCode, rawPassword);

        log.info("Bootstrap: initial admin account created (code={})", adminCode);
    }

    /**
     * Проверяет, существует ли в БД пользователь с ролью ADMIN.
     */
    private boolean hasAdminUser() {
        return userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() != null && ADMIN_ROLE_NAME.equals(u.getRole().getName()));
    }

    /**
     * Дописывает (или обновляет) переменные окружения с учётными данными
     * администратора в файл {@code .env.prod.local}.
     */
    private void writeEnvFile(String code, String password) {
        try {
            Path envPath = ENV_FILE.toAbsolutePath().normalize();

            List<String> lines;
            if (Files.exists(envPath)) {
                lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            } else {
                lines = List.of();
            }

            // Удаляем старые значения, если есть
            lines = lines.stream()
                    .filter(l -> !l.trim().startsWith(ENV_ADMIN_CODE + "=")
                            && !l.trim().startsWith(ENV_ADMIN_PASSWORD + "="))
                    .toList();

            // Добавляем новые значения
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append(System.lineSeparator());
            }
            sb.append(System.lineSeparator());
            sb.append("# ── Auto-generated admin credentials (created on first bootstrap) ──────────").append(System.lineSeparator());
            sb.append(ENV_ADMIN_CODE).append("=").append(code).append(System.lineSeparator());
            sb.append(ENV_ADMIN_PASSWORD).append("=").append(password).append(System.lineSeparator());

            Files.writeString(envPath, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Bootstrap: admin credentials written to {}", envPath);
        } catch (Exception e) {
            log.warn("Bootstrap: unable to write credentials to {}: {}", ENV_FILE, e.getMessage());
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
