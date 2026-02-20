package dev.savushkin.scada.mobile.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты для {@link MdcFilter}.
 * <p>
 * Проверяют:
 * <ul>
 *   <li>MDC-поля устанавливаются корректно во время обработки запроса</li>
 *   <li>MDC очищается после обработки (защита от утечек между запросами)</li>
 *   <li>Внешний X-Request-ID принимается при валидном значении</li>
 *   <li>Невалидный X-Request-ID отбрасывается, генерируется свой</li>
 *   <li>Слишком длинный X-Request-ID отбрасывается</li>
 *   <li>X-Request-ID пробрасывается в заголовок ответа</li>
 *   <li>MDC очищается даже при исключении в цепочке фильтров</li>
 * </ul>
 */
class MdcFilterTest {

    private final MdcFilter filter = new MdcFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPopulateMdcDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/commands/queryAll");

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res) {
                assertThat(MDC.get(MdcFilter.MDC_REQUEST_ID)).isNotBlank();
                assertThat(MDC.get(MdcFilter.MDC_METHOD)).isEqualTo("GET");
                assertThat(MDC.get(MdcFilter.MDC_URI)).isEqualTo("/api/v1/commands/queryAll");
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void shouldClearMdcAfterRequest() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/commands/setUnitVars"),
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(MDC.get(MdcFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcFilter.MDC_METHOD)).isNull();
        assertThat(MDC.get(MdcFilter.MDC_URI)).isNull();
    }

    @Test
    void shouldAcceptValidExternalRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/commands/queryAll");
        request.addHeader(MdcFilter.REQUEST_ID_HEADER, "abc12345");

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res) {
                assertThat(MDC.get(MdcFilter.MDC_REQUEST_ID)).isEqualTo("abc12345");
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void shouldRejectInvalidExternalRequestIdAndGenerateOwn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/commands/queryAll");
        request.addHeader(MdcFilter.REQUEST_ID_HEADER, "<script>alert(1)</script>");

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res) {
                String id = MDC.get(MdcFilter.MDC_REQUEST_ID);
                assertThat(id).isNotBlank();
                assertThat(id).doesNotContain("<", ">", "script");
                assertThat(id).hasSize(8);
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void shouldRejectTooLongExternalRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/commands/queryAll");
        request.addHeader(MdcFilter.REQUEST_ID_HEADER, "a".repeat(65));

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res) {
                String id = MDC.get(MdcFilter.MDC_REQUEST_ID);
                assertThat(id).isNotBlank();
                assertThat(id).hasSize(8);
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void shouldPropagateRequestIdToResponseHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/commands/queryAll"),
                response,
                new MockFilterChain()
        );

        assertThat(response.getHeader(MdcFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void shouldClearMdcEvenWhenFilterChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/commands/queryAll");

        FilterChain throwingChain = (req, res) -> {
            throw new ServletException("simulated error");
        };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), throwingChain);
        } catch (ServletException ignored) {
            // ожидаемо — проверяем что MDC почищен
        }

        assertThat(MDC.get(MdcFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcFilter.MDC_METHOD)).isNull();
        assertThat(MDC.get(MdcFilter.MDC_URI)).isNull();
    }
}

