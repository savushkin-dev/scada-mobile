package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime polling-оркестратор: один независимый worker-loop на каждый инстанс PrintSrv.
 *
 * <p>Каждый worker запускается в отдельном virtual thread, последовательно
 * опрашивает устройства своего инстанса и сразу после успешного прохода
 * публикует {@link PrintSrvInstancePolledEvent}. Это позволяет доставлять live-
 * обновления по мере готовности конкретной машины, а не после общего цикла.
 *
 * <p>Набор worker-ов не зафиксирован на старте: {@link #synchronize()}
 * приводит его к содержимому {@link PrintSrvClientRegistry} после
 * админ-изменений автоматов — новые инстансы начинают опрашиваться,
 * а исчезнувшие останавливаются без перезапуска приложения.
 */
@Service
public class PrintSrvPollingRuntime implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvPollingRuntime.class);

    /**
     * instanceId → poller. Worker завершает свой цикл, как только обнаруживает,
     * что его poller удалён из мапы или заменён новым (сравнение по ссылке).
     */
    private final ConcurrentHashMap<String, PrintSrvInstancePoller> pollers = new ConcurrentHashMap<>();
    private final PrintSrvPollerFactory pollerFactory;
    private final PrintSrvClientRegistry clientRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final long fixedDelayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ExecutorService executor;

    public PrintSrvPollingRuntime(
            PrintSrvPollerFactory pollerFactory,
            PrintSrvClientRegistry clientRegistry,
            ApplicationEventPublisher eventPublisher,
            PrintSrvProperties properties
    ) {
        this.pollerFactory = pollerFactory;
        this.clientRegistry = clientRegistry;
        this.eventPublisher = eventPublisher;
        this.fixedDelayMs = properties.getPolling().getFixedDelayMs();

        pollerFactory.createAll().forEach(poller -> pollers.put(poller.getInstanceId(), poller));

        int totalDevices = pollers.values().stream()
                .mapToInt(PrintSrvInstancePoller::getConfiguredDeviceCount)
                .sum();
        log.info("PrintSrvPollingRuntime initialized: {} worker(s), {} configured device(s), delay={}ms",
                pollers.size(), totalDevices, fixedDelayMs);
        PollingLogger.logRuntimeInitialized(pollers.size(), totalDevices, fixedDelayMs);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        ThreadFactory threadFactory = Thread.ofVirtual()
                .name("printsrv-poller-", 0)
                .factory();
        executor = Executors.newThreadPerTaskExecutor(threadFactory);

        ExecutorService currentExecutor = executor;
        pollers.values().forEach(poller -> currentExecutor.submit(() -> runPollLoop(poller)));

        log.info("PrintSrvPollingRuntime started with {} virtual worker(s)", pollers.size());
        PollingLogger.logRuntimeStarted(pollers.size());
    }

    /**
     * Приводит набор worker-ов к текущему содержимому {@link PrintSrvClientRegistry}.
     *
     * <p>Вызывается {@link PrintSrvConnectionSynchronizer} после сверки реестра
     * клиентов с БД. Поллер создаётся заново, только если клиент инстанса
     * пересоздан (новый PrintSrv ID или смена host/port); неизменные инстансы
     * продолжают опрашиваться без паузы.
     *
     * <p>Осиротевший worker завершается не мгновенно, а на границе цикла
     * (максимум — текущий poll + fixedDelay), что безопасно: его клиент уже
     * закрыт реестром, поэтому poll завершится IOException.
     */
    public synchronized void synchronize() {
        Set<String> activeIds = clientRegistry.getInstanceIds();

        pollers.keySet().removeIf(id -> !activeIds.contains(id));

        ExecutorService currentExecutor = executor;
        for (PrintSrvClient client : clientRegistry.getAll()) {
            String id = client.getInstanceId();
            PrintSrvInstancePoller current = pollers.get(id);
            if (current != null && current.getClient() == client) {
                continue;
            }
            PrintSrvInstancePoller created = pollerFactory.createFor(client);
            pollers.put(id, created);
            if (running.get() && currentExecutor != null) {
                currentExecutor.submit(() -> runPollLoop(created));
            }
            log.info("[{}] poller {} after topology change", id, current == null ? "started" : "restarted");
        }
    }

    private void runPollLoop(@NonNull PrintSrvInstancePoller poller) {
        String instanceId = poller.getInstanceId();
        log.debug("[{}] polling worker started", instanceId);
        PollingLogger.logWorkerStarted(instanceId);

        while (running.get() && pollers.get(instanceId) == poller && !Thread.currentThread().isInterrupted()) {
            try {
                PrintSrvInstancePoller.PollResult pollResult = poller.poll();
                if (pollResult.shouldPublishLiveUpdate()) {
                    eventPublisher.publishEvent(new PrintSrvInstancePolledEvent(instanceId));
                }
            } catch (Exception ex) {
                log.error("[{}] unexpected polling worker failure: {}", instanceId, ex.getMessage(), ex);
                PollingLogger.logWorkerFailure(instanceId, ex);
            }

            try {
                Thread.sleep(fixedDelayMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.debug("[{}] polling worker stopped", instanceId);
        PollingLogger.logWorkerStopped(instanceId);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        ExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
            executor = null;
        }

        log.info("PrintSrvPollingRuntime stopped");
        PollingLogger.logRuntimeStopped();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }
}
