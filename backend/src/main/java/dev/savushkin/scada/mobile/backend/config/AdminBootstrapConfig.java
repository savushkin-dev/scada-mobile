package dev.savushkin.scada.mobile.backend.config;

import dev.savushkin.scada.mobile.backend.domain.auth.EmployeeCredentialsGenerator;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bootstrap-компонент для создания начального администратора.
 * <p>
 * При запуске приложения проверяется, есть ли в БД хотя бы один пользователь
 * с ролью {@code ADMIN}. Если нет — бэкенд автоматически применяет скрипт
 * {@code db/bootstrap/bootstrap_admin.sql}, создающий роль {@code ADMIN}
 * и пользователя-администратора.
 * <p>
 * Код и пароль администратора генерируются автоматически
 * через {@link EmployeeCredentialsGenerator}. Пароль является временным:
 * при первом входе администратор должен сменить его через {@code /change-password}.
 * Сгенерированные учётные данные выводятся в лог (единственный раз).
 */
@Component
public class AdminBootstrapConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);
    private static final String ADMIN_ROLE_NAME = "ADMIN";
    private static final String BOOTSTRAP_SCRIPT = "classpath:db/bootstrap/bootstrap_admin.sql";

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;

    public AdminBootstrapConfig(UserJpaRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                JdbcTemplate jdbcTemplate,
                                ResourceLoader resourceLoader) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.resourceLoader = resourceLoader;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hasAdminUser()) {
            log.info("Bootstrap: admin user already exists, skipping admin creation");
            return;
        }

        String adminCode = EmployeeCredentialsGenerator.generateCode();
        String adminPassword = EmployeeCredentialsGenerator.generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(adminPassword);

        String sql = loadBootstrapScript(adminCode, passwordHash);
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }

        log.warn("""
                
                ================================================================================
                Bootstrap: initial admin account created.
                Code:     {}
                Password: {}
                IMPORTANT: this is a temporary password. The admin must change it on first login.
                ================================================================================
                """, adminCode, adminPassword);
    }

    private boolean hasAdminUser() {
        return userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() != null && ADMIN_ROLE_NAME.equals(u.getRole().getName()));
    }

    private String loadBootstrapScript(String adminCode, String passwordHash) {
        Resource resource = resourceLoader.getResource(BOOTSTRAP_SCRIPT);
        try {
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return sql
                    .replace("${ADMIN_CODE}", adminCode)
                    .replace("${PASSWORD_HASH}", passwordHash);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load admin bootstrap script: " + BOOTSTRAP_SCRIPT, e);
        }
    }
}
