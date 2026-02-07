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
 *   <li>Автоматически закрывает соединение при shutdown приложения</li>
 * </ul>
 * <p>
 * Параметры соединения настраиваются через application.yaml:
 * <pre>
 * printsrv:
 *   ip: 127.0.0.1
 *   port: 10101
 * </pre>
 */
@Component
public class SocketManager {

    private static final Logger log = LoggerFactory.getLogger(SocketManager.class);

    private final String IP;
    private final int PORT;
    private Socket socket;

    /**
     * Конструктор с внедрением конфигурации из application.yaml.
     *
     * @param ip   IP адрес PrintSrv (из ${printsrv.ip})
     * @param port порт PrintSrv (из ${printsrv.port})
     */
    private SocketManager(
            @Value("${printsrv.ip}") String ip,
            @Value("${printsrv.port}") int port
    ) {
        IP = ip;
        PORT = port;
        log.info("SocketManager initialized with PrintSrv address: {}:{}", IP, PORT);
    }

    /**
     * Закрывает socket-соединение при shutdown приложения.
     * <p>
     * Метод помечен @PreDestroy и вызывается автоматически Spring'ом
     * перед уничтожением bean.
     *
     * @throws IOException если произошла ошибка закрытия socket
     */
    @PreDestroy
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            log.info("Closing socket connection to PrintSrv {}:{}", IP, PORT);
            socket.close();
            log.debug("Socket closed successfully");
        }
    }

    /**
     * Возвращает socket-соединение с PrintSrv.
     * <p>
     * При первом вызове создает новое соединение (lazy initialization).
     * Последующие вызовы возвращают существующее соединение.
     * <p>
     * <b>Важно:</b> Соединение переиспользуется для всех команд PrintSrv.
     * Это обеспечивает эффективность (нет overhead на установку соединения)
     * и соответствует протоколу PrintSrv.
     *
     * @return socket-соединение с PrintSrv
     * @throws IOException если не удалось создать соединение
     */
    public Socket getSocket() throws IOException {
        // Если соединение уже есть - переиспользуем
        if (socket != null) {
            log.trace("Reusing existing socket connection to {}:{}", IP, PORT);
            return socket;
        }

        // Создаем новое соединение (lazy initialization)
        log.info("Creating new socket connection to PrintSrv {}:{}", IP, PORT);
        try {
            socket = new Socket(IP, PORT);
            log.info("Socket connection established successfully to {}:{}", IP, PORT);
        } catch (IOException e) {
            log.error("Failed to create socket connection to {}:{} - {}", IP, PORT, e.getMessage());
            throw e;
        }
        return socket;
    }
}
