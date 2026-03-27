package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.List;
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
 */
@Service
public class PrintSrvPollingRuntime implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvPollingRuntime.class);

    private final List<PrintSrvInstancePoller> pollers;
    private final ApplicationEventPublisher eventPublisher;
    private final long fixedDelayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ExecutorService executor;

    public PrintSrvPollingRuntime(
            PrintSrvPollerFactory pollerFactory,
            ApplicationEventPublisher eventPublisher,
            PrintSrvProperties properties
    ) {
        this.pollers = pollerFactory.createAll();
        this.eventPublisher = eventPublisher;
        this.fixedDelayMs = properties.getPolling().getFixedDelayMs();

        int totalDevices = pollers.stream()
                .mapToInt(PrintSrvInstancePoller::getConfiguredDeviceCount)
                .sum();
        log.info("PrintSrvPollingRuntime initialized: {} worker(s), {} configured device(s), delay={}ms",
                pollers.size(), totalDevices, fixedDelayMs);
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

        for (PrintSrvInstancePoller poller : pollers) {
            executor.submit(() -> runPollLoop(poller));
        }

        log.info("PrintSrvPollingRuntime started with {} virtual worker(s)", pollers.size());
    }

    private void runPollLoop(@NonNull PrintSrvInstancePoller poller) {
        String instanceId = poller.getInstanceId();
        log.debug("[{}] polling worker started", instanceId);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                PrintSrvInstancePoller.PollResult pollResult = poller.poll();
                if (pollResult.shouldPublishLiveUpdate()) {
                    eventPublisher.publishEvent(new PrintSrvInstancePolledEvent(instanceId));
                }
            } catch (Exception ex) {
                log.error("[{}] unexpected polling worker failure: {}", instanceId, ex.getMessage(), ex);
            }

            try {
                Thread.sleep(fixedDelayMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.debug("[{}] polling worker stopped", instanceId);
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
