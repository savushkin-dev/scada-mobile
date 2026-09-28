package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты детекции доступности инстанса в {@link PrintSrvInstancePoller}.
 *
 * <p>Ключевой сценарий — старт бэкенда с недоступным инстансом: первый
 * неуспешный poll обязан засчитываться как изменение доступности, чтобы
 * {@link PrintSrvInstancePolledEvent} опубликовалось и слушатели (в частности,
 * {@code LineBatchEndDetector}) смогли сверить состояние. Регрессионный тест
 * на баг «вечного» MACHINE-уведомления при рестарте с offline-инстансом.
 */
class PrintSrvInstancePollerTest {

    private static final String INSTANCE_ID = "hassia2";

    private final PrintSrvClient client = mock(PrintSrvClient.class);
    private final InstanceSnapshotRepository snapshotRepo = mock(InstanceSnapshotRepository.class);
    private final PrintSrvMapper mapper = new PrintSrvMapper();

    private PrintSrvInstancePoller pollerWith(List<String> devices) {
        when(client.getInstanceId()).thenReturn(INSTANCE_ID);
        return new PrintSrvInstancePoller(client, mapper, snapshotRepo, devices);
    }

    @Test
    @DisplayName("Старт с недоступным инстансом: первый failed-poll = изменение доступности, событие публикуется")
    void firstFailedPollIsAvailabilityChange() throws IOException {
        when(client.queryAll(any())).thenThrow(new IOException("connection refused"));
        PrintSrvInstancePoller poller = pollerWith(List.of("Line", "scada"));

        PrintSrvInstancePoller.PollResult result = poller.poll();

        assertThat(result.reachable()).isFalse();
        assertThat(result.availabilityChanged()).isTrue();
        assertThat(result.shouldPublishLiveUpdate()).isTrue();
        verify(snapshotRepo, times(1)).clearInstance(INSTANCE_ID);
    }

    @Test
    @DisplayName("Повторные failed-poll без изменений: событие не публикуется")
    void repeatedFailedPollsAreNotAvailabilityChanges() throws IOException {
        when(client.queryAll(any())).thenThrow(new IOException("connection refused"));
        PrintSrvInstancePoller poller = pollerWith(List.of("Line"));

        poller.poll();
        PrintSrvInstancePoller.PollResult second = poller.poll();

        assertThat(second.reachable()).isFalse();
        assertThat(second.availabilityChanged()).isFalse();
        assertThat(second.shouldPublishLiveUpdate()).isFalse();
        verify(snapshotRepo, times(1)).clearInstance(INSTANCE_ID);
    }

    @Test
    @DisplayName("Штатный старт с доступным инстансом: успех без ложного 'изменения доступности'")
    void firstSuccessfulPollIsNotAvailabilityChange() throws IOException {
        QueryAllResponseDTO dto = new QueryAllResponseDTO("Line", "QueryAll", Map.of());
        when(client.queryAll(eq("Line"))).thenReturn(dto);
        PrintSrvInstancePoller poller = pollerWith(List.of("Line"));

        PrintSrvInstancePoller.PollResult result = poller.poll();

        assertThat(result.reachable()).isTrue();
        assertThat(result.availabilityChanged()).isFalse();
        assertThat(result.shouldPublishLiveUpdate()).isTrue();
    }

    @Test
    @DisplayName("Восстановление после сбоя: failed -> success = изменение доступности")
    void recoveryIsAvailabilityChange() throws IOException {
        when(client.queryAll(any()))
                .thenThrow(new IOException("connection refused"))
                .thenReturn(new QueryAllResponseDTO("Line", "QueryAll", Map.of()));
        PrintSrvInstancePoller poller = pollerWith(List.of("Line"));

        poller.poll();
        PrintSrvInstancePoller.PollResult recovered = poller.poll();

        assertThat(recovered.reachable()).isTrue();
        assertThat(recovered.availabilityChanged()).isTrue();
        assertThat(recovered.shouldPublishLiveUpdate()).isTrue();
    }
}
