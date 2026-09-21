package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Регрессионный тест на прод-инцидент 2026-09-21: при двух публичных
 * конструкторах без {@link Autowired} Spring не может выбрать кандидата
 * и падает при старте с «No default constructor found» — приложение
 * не поднимается. Гарантируем, что ровно один конструктор помечен
 * {@link Autowired}, и что Spring-контекст реально создаёт бин.
 */
class LineBatchEndDetectorWiringTest {

    @Test
    @DisplayName("Ровно один конструктор детектора помечен @Autowired (иначе контекст не стартует)")
    void exactlyOneConstructorIsMarkedAutowired() {
        Constructor<?>[] constructors = LineBatchEndDetector.class.getDeclaredConstructors();

        long autowiredCount = java.util.Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Autowired.class))
                .count();

        assertThat(constructors.length).isGreaterThan(1);
        assertThat(autowiredCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Spring-контекст создаёт бин детектора через autowiring (выбор конструктора)")
    void springContextInstantiatesDetectorViaAutowiring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(InstanceSnapshotRepository.class,
                    () -> mock(InstanceSnapshotRepository.class));
            context.registerBean(NotificationService.class,
                    () -> mock(NotificationService.class));
            context.registerBean(PrintSrvProperties.class, PrintSrvProperties::new);
            context.register(LineBatchEndDetector.class);
            context.refresh();

            assertThat(context.getBean(LineBatchEndDetector.class)).isNotNull();
        }
    }
}
