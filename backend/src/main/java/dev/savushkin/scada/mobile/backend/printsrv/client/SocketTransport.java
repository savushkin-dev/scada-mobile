package dev.savushkin.scada.mobile.backend.printsrv.client;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Низкоуровневый транспорт для работы с socket-соединением PrintSrv.
 * <p>
 * Реализует протокол обмена данными с PrintSrv:
 * <pre>
 * Формат пакета:
 * - MAGIC (4 байта): "P001"
 * - LENGTH (4 байта): длина JSON в байтах (Big Endian int32)
 * - BODY (N байт): JSON данные в кодировке windows-1251
 * </pre>
 * <p>
 * Протокол работает в режиме запрос-ответ через единое socket-соединение.
 */
@Component
public class SocketTransport {

    private static final Logger log = LoggerFactory.getLogger(SocketTransport.class);

    /**
     * MAGIC заголовок протокола PrintSrv
     */
    private static final byte[] MAGIC = new byte[]{'P', '0', '0', '1'};

    /**
     * Кодировка для JSON данных (требование PrintSrv)
     */
    private static final Charset CHARSET = Charset.forName("windows-1251");

    private final SocketManager socketManager;

    /**
     * Конструктор с внедрением SocketManager.
     *
     * @param socketManager менеджер socket-соединений
     */
    public SocketTransport(SocketManager socketManager) {
        this.socketManager = socketManager;
        log.info("SocketTransport initialized with SocketManager");
    }

    /**
     * Отправляет JSON запрос в PrintSrv через socket.
     * <p>
     * Формат отправки:
     * <ol>
     *   <li>4 байта: MAGIC заголовок "P001"</li>
     *   <li>4 байта: длина JSON (Big Endian int32)</li>
     *   <li>N байт: JSON в windows-1251</li>
     * </ol>
     *
     * @param request JSON строка запроса (не null, не пустая)
     * @throws IOException              если произошла ошибка записи в socket
     * @throws SocketException          если socket закрыт
     * @throws IllegalArgumentException если запрос пустой
     */
    public void sendRequest(@NonNull String request) throws IOException {
        log.trace("Preparing to send request via socket");

        // Получаем socket от менеджера
        Socket socket = socketManager.getSocket();
        log.trace("Socket obtained: {}:{}, closed={}",
                socket.getInetAddress(), socket.getPort(), socket.isClosed());

        // Проверяем состояние socket
        if (socket.isClosed()) {
            log.error("Attempted to send request but socket is closed");
            throw new SocketException("Socket is closed");
        }
        if (request.isEmpty()) {
            log.error("Attempted to send empty request");
            throw new IllegalArgumentException("JSON cannot be empty");
        }

        // Конвертируем JSON в байты (windows-1251)
        byte[] jsonBytes = request.getBytes(CHARSET);
        log.debug("Sending request to PrintSrv: {} bytes", jsonBytes.length);

        // Отправляем пакет по протоколу
        OutputStream out = socket.getOutputStream();
        out.write(MAGIC);                                              // MAGIC заголовок
        out.write(ByteBuffer.allocate(4).putInt(jsonBytes.length).array()); // Длина
        out.write(jsonBytes);                                          // JSON данные
        out.flush();

        log.trace("Request sent successfully (MAGIC + length + JSON body)");
    }

    /**
     * Получает JSON ответ от PrintSrv через socket.
     * <p>
     * Формат чтения:
     * <ol>
     *   <li>4 байта: MAGIC заголовок "P001" (валидация)</li>
     *   <li>4 байта: длина JSON (Big Endian int32)</li>
     *   <li>N байт: JSON в windows-1251</li>
     * </ol>
     * <p>
     * Максимальный размер ответа: 10 МБ (защита от некорректных данных).
     *
     * @return JSON строка ответа (не null, не пустая)
     * @throws IOException     если произошла ошибка чтения из socket
     * @throws SocketException если socket закрыт
     * @throws IOException     если MAGIC заголовок некорректен
     * @throws IOException     если длина ответа некорректна (< 0 или > 10MB)
     */
    public @NonNull String getResponse() throws IOException {
        log.trace("Waiting for response from PrintSrv");

        // Получаем socket от менеджера
        Socket socket = socketManager.getSocket();

        if (socket.isClosed()) {
            log.error("Attempted to read response but socket is closed");
            throw new SocketException("Socket is closed");
        }

        InputStream in = socket.getInputStream();

        // Шаг 1: Чтение и валидация MAGIC заголовка
        log.trace("Reading MAGIC header");
        byte[] magic = in.readNBytes(4);
        if (magic.length != 4 || magic[0] != 'P' || magic[1] != '0' || magic[2] != '0' || magic[3] != '1') {
            log.error("Invalid MAGIC header received: expected P001, got: {}",
                    magic.length == 4 ? new String(magic) : "incomplete");
            throw new IOException("Incorrect magic header!");
        }
        log.trace("MAGIC header validated successfully");

        // Шаг 2: Чтение длины JSON
        log.trace("Reading response length");
        byte[] lengthByte = in.readNBytes(4);
        int length = ByteBuffer.wrap(lengthByte).getInt();
        log.debug("Response length: {} bytes", length);

        // Валидация длины (защита от некорректных данных)
        if (length < 0 || length > 10 * 1024 * 1024) {
            log.error("Invalid response length: {}", length);
            throw new IOException("Incorrect length: " + length);
        }

        // Шаг 3: Чтение JSON тела
        log.trace("Reading response body");
        String response = new String(in.readNBytes(length), CHARSET);
        log.debug("Response received successfully from PrintSrv: {} bytes", response.length());

        return response;
    }
}
