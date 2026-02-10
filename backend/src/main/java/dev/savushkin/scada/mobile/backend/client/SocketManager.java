package dev.savushkin.scada.mobile.backend.client;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Socket;

/**
 * Менеджер socket-соединения с PrintSrv.
 * <p>
 * Управляет единственным socket-соединением с PrintSrv:
 * <ul>
 *   <li>Создает соединение при первом запросе (lazy initialization)</li>
 *   <li>Переиспользует существующее соединение для всех последующих запросов</li>
 *   <li>Поддерживает инвалидацию и переподключение при ошибках</li>
 *   <li>Автоматически закрывает соединение при shutdown приложения</li>
 * </ul>
 * <p>
 * Параметры соединения настраиваются через application.yaml:
 * <pre>
 * printsrv:
 *   ip: 127.0.0.1
 *   port: 10101
 *   socket:
 *     connect-timeout-ms: 5000
 *     read-timeout-ms: 5000
 * </pre>
 * <p>
 * Thread-safe: все публичные методы синхронизированы.
 */
@Component
public class SocketManager {

    private static final Logger log = LoggerFactory.getLogger(SocketManager.class);

    private final String IP;
    private final int PORT;
    private final int CONNECT_TIMEOUT_MS;
    private final int READ_TIMEOUT_MS;

    private volatile Socket socket;

    /**
     * Конструктор с внедрением конфигурации из application.yaml.
     *
     * @param ip                IP адрес PrintSrv (из ${printsrv.ip})
     * @param port              порт PrintSrv (из ${printsrv.port})
     * @param connectTimeoutMs  таймаут на установку соединения (из ${printsrv.socket.connect-timeout-ms})
     * @param readTimeoutMs     таймаут на чтение данных (из ${printsrv.socket.read-timeout-ms})
     */
    private SocketManager(
            @Value("${printsrv.ip}") String ip,
            @Value("${printsrv.port}") int port,
            @Value("${printsrv.socket.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${printsrv.socket.read-timeout-ms}") int readTimeoutMs
    ) {
        IP = ip;
        PORT = port;
        CONNECT_TIMEOUT_MS = connectTimeoutMs;
        READ_TIMEOUT_MS = readTimeoutMs;
        log.info("SocketManager initialized with PrintSrv address: {}:{}, timeouts: connect={}ms, read={}ms",
                IP, PORT, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /**
     * Закрывает socket-соединение при shutdown приложения.
     * <p>
     * Метод помечен @PreDestroy и вызывается автоматически Spring'ом
     * перед уничтожением bean.
     */
    @PreDestroy
    public synchronized void close() {
        closeQuietly(socket);
        socket = null;
        log.info("SocketManager shutdown completed");
    }

    /**
     * Возвращает socket-соединение с PrintSrv.
     * <p>
     * При первом вызове создает новое соединение (lazy initialization).
     * Последующие вызовы возвращают существующее соединение, если оно валидно.
     * <p>
     * Валидация соединения:
     * <ul>
     *   <li>Socket не null</li>
     *   <li>Socket не закрыт (isClosed() == false)</li>
     *   <li>Socket подключен (isConnected() == true)</li>
     * </ul>
     * <p>
     * <b>Thread-safe:</b> метод синхронизирован для безопасного использования
     * из нескольких потоков (PrintSrvPollingScheduler + SetUnitVars).
     *
     * @return socket-соединение с PrintSrv
     * @throws IOException если не удалось создать соединение
     */
    public synchronized Socket getSocket() throws IOException {
        // Проверяем существующее соединение на валидность
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            log.trace("Reusing existing socket connection to {}:{}", IP, PORT);
            return socket;
        }

        // Если соединение невалидно - создаем новое
        log.info("Creating new socket connection to PrintSrv {}:{}", IP, PORT);
        try {
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(IP, PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            log.info("✅ Socket connection established successfully to {}:{}", IP, PORT);
        } catch (IOException e) {
            socket = null;  // Обнуляем при ошибке
            log.error("❌ Failed to create socket connection to {}:{} - {}", IP, PORT, e.getMessage());
            throw e;
        }
        return socket;
    }

    /**
     * Инвалидирует текущее socket-соединение.
     * <p>
     * Используется при обнаружении проблем с соединением (IOException, SocketException).
     * Следующий вызов {@link #getSocket()} создаст новое соединение.
     * <p>
     * <b>Thread-safe:</b> метод синхронизирован.
     */
    public synchronized void invalidate() {
        if (socket != null) {
            log.warn("⚠️ Invalidating socket connection to {}:{}", IP, PORT);
            closeQuietly(socket);
            socket = null;
        }
    }

    /**
     * Закрывает socket без выброса исключений.
     * <p>
     * Вспомогательный метод для безопасного закрытия socket в finally-блоках.
     *
     * @param socket socket для закрытия (может быть null)
     */
    private void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                log.debug("Closing socket connection to {}:{}", IP, PORT);
                socket.close();
                log.trace("Socket closed successfully");
            } catch (IOException e) {
                log.warn("Failed to close socket gracefully: {}", e.getMessage());
            }
        }
    }
}
