package dev.savushkin.scada.mobile.backend.client;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class PrintSrvConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvConnectionPool.class);

    private final BlockingQueue<PrintSrvConnection> connections;
    private final PrintSrvConnectionFactory factory;
    private final int poolSize;

    public PrintSrvConnectionPool(
            PrintSrvConnectionFactory factory,
            @Value("${printsrv.pool.size}") int poolSize
    ) throws IOException {
        this.poolSize = poolSize;
        this.connections = new LinkedBlockingQueue<>(poolSize);
        this.factory = factory;

        for (int i = 0; i < this.poolSize; i++) {
            connections.add(factory.createConnection());
        }

        log.info("PrintSrvConnectionPool initialized (poolSize={})", poolSize);
    }

    public PrintSrvConnection borrowConnection() throws InterruptedException, IOException {
        int before = connections.size();
        long waitStartedAt = System.currentTimeMillis();

        if (log.isDebugEnabled()) {
            log.debug("Pool borrow requested (availableBefore={}) (thread={})", before, Thread.currentThread().getName());
        }

        PrintSrvConnection connection = connections.take();

        long waitedMs = System.currentTimeMillis() - waitStartedAt;
        if (log.isDebugEnabled()) {
            log.debug("Pool borrow granted con={} waitedMs={} (availableAfter={}) (thread={})",
                    connection.getId(),
                    waitedMs,
                    connections.size(),
                    Thread.currentThread().getName());
        }

        // NB: Проверка socket.isConnected()/isClosed слабая. Основной механизм "пригодности"
        // в этом проекте — инвалидировать соединение при любой ошибке во время I/O/парсинга.
        // Если из очереди достали уже невалидный сокет (например, его убил сервер по idle-timeout),
        // он УЖЕ изъят из пула, поэтому просто закрываем и создаём новый для вызывающего кода.
        if (!connection.isValid()) {
            log.warn("Borrowed PrintSrvConnection is invalid; recreating con={}", connection.getId());
            try {
                connection.close();
            } catch (IOException e) {
                log.debug("Failed to close invalid borrowed PrintSrvConnection con={}", connection.getId(), e);
            }
            connection = factory.createConnection();
        }

        return connection;
    }

    public void returnConnection(@NonNull PrintSrvConnection connection) {
        boolean offered = connections.offer(connection);
        if (!offered) {
            // Пул переполнен (не должно происходить при корректном использовании), но чтобы не течь сокетами — закрываем.
            try {
                connection.close();
            } catch (IOException e) {
                log.debug("Failed to close connection that couldn't be returned to pool con={}", connection.getId(), e);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Pool return con={} offered={} (availableNow={}) (thread={})",
                    connection.getId(),
                    offered,
                    connections.size(),
                    Thread.currentThread().getName());
        }
    }

    /**
     * Инвалидирует текущее соединение (закрывает) и добавляет в пул новое.
     * Используй это, если на соединении произошла любая ошибка (I/O, парсинг, Fail и т.д.).
     */
    public void invalidateAndReplace(@NonNull PrintSrvConnection badConnection, Throwable cause) {
        String conId = badConnection.getId();
        if (cause != null) {
            log.warn("Invalidating PrintSrvConnection con={} due to error", conId, cause);
        } else {
            log.warn("Invalidating PrintSrvConnection con={} due to error", conId);
        }

        try {
            badConnection.close();
        } catch (IOException e) {
            log.debug("Failed to close invalid PrintSrvConnection con={}", conId, e);
        }

        try {
            PrintSrvConnection replacement = factory.createConnection();
            boolean offered = connections.offer(replacement);
            if (!offered) {
                // Пул забит: закрываем replacement, чтобы не оставить лишний сокет.
                try {
                    replacement.close();
                } catch (IOException e) {
                    log.debug("Failed to close replacement connection that couldn't be added to pool con={}", replacement.getId(), e);
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Replaced con={} with new con={} offered={} (availableNow={})",
                        conId,
                        replacement.getId(),
                        offered,
                        connections.size());
            }
        } catch (Exception e) {
            log.error("Failed to replace invalid PrintSrvConnection con={}. Pool size may shrink temporarily", conId, e);
        }
    }

    @PreDestroy
    public void destroy() {
        int closed = 0;
        for (PrintSrvConnection con : connections) {
            try {
                con.close();
                closed++;
            } catch (IOException e) {
                log.warn("Failed to close PrintSrvConnection during shutdown", e);
            }
        }
        log.info("PrintSrvConnectionPool destroyed (closedConnections={})", closed);
    }
}
