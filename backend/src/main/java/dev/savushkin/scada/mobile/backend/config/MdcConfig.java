package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Регистрация {@link MdcFilter} в контейнере сервлетов.
 * <p>
 * Фильтр должен выполняться <b>как можно раньше</b> в цепочке фильтров —
 * до CORS, Spring Security, DispatcherServlet — чтобы MDC-контекст был
 * доступен во всех последующих фильтрах и в логах Spring-инфраструктуры.
 * <p>
 * Порядок {@link Ordered#HIGHEST_PRECEDENCE} гарантирует, что фильтр
 * будет первым в цепочке.
 */
@Configuration
public class MdcConfig {

    /**
     * Регистрирует {@link MdcFilter} с наивысшим приоритетом выполнения.
     *
     * @return бин регистрации фильтра
     */
    @Bean
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistration() {
        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcFilter());
        registration.addUrlPatterns("/*");         // применяем ко всем URL
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("mdcFilter");
        return registration;
    }
}

