package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PollingLogger;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Реальный TCP-клиент для одного инстанса PrintSrv.
 * <p>
 * Каждый инстанс (физическая машина маркировки) имеет свой {@code TcpPrintSrvClient}
 * с собственным TCP-соединением. Протокол: P001 magic header + 4-byte length + JSON (windows-1251).
 * <p>
 * Клиент управляет жизненным циклом socket:
 * <ul>
 *   <li>Lazy initialization: соединение создаётся при первом запросе</li>
 *   <li>Переподключение: при ошибке socket инвалидируется, следующий вызов создаст новый</li>
 *   <li>Thread-safe: все операции с socket синхронизированы</li>
 * </ul>
 */
public class TcpPrintSrvClient implements PrintSrvClient {

    private static final Logger log = LoggerFactory.getLogger(TcpPrintSrvClient.class);

    private static final byte[] MAGIC = {'P', '0', '0', '1'};
    private static final Charset CHARSET = Charset.forName("windows-1251");
    private static final int MAX_RESPONSE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final String instanceId;
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final ObjectMapper objectMapper;

    private volatile Socket socket;

    public TcpPrintSrvClient(
            String instanceId,
            String host,
            int port,
            int connectTimeoutMs,
            int readTimeoutMs,
            ObjectMapper objectMapper
    ) {
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.objectMapper = objectMapper;
        log.debug("TcpPrintSrvClient created: instance='{}', address={}:{}", instanceId, host, port);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public QueryAllResponseDTO queryAll(String deviceName) throws IOException {
        QueryAllRequestDTO request = new QueryAllRequestDTO(deviceName, "QueryAll");
        String json = objectMapper.writeValueAsString(request);
        PollingLogger.logRequestSent(instanceId, deviceName, json);
        String response = sendAndReceive(json, deviceName);
        PollingLogger.logResponseBody(instanceId, deviceName, response);
        try {
            QueryAllResponseDTO dto = objectMapper.readValue(response, QueryAllResponseDTO.class);
            int unitCount = dto.units() != null ? dto.units().size() : 0;
            PollingLogger.logParseSuccess(instanceId, deviceName, dto.deviceName(), unitCount);
            return dto;
        } catch (IOException e) {
            PollingLogger.logParseError(instanceId, deviceName, e.getMessage(), response);
            throw e;
        }
    }

    @Override
    public boolean isAlive() {
        Socket s = socket;
        return s != null && !s.isClosed() && s.isConnected();
        // TODO: проверка isAlive() в TcpPrintSrvClient не соответствует политике PrintSrv
    }

    /**
     * Закрывает TCP-соединение. Следующий вызов {@link #queryAll} создаст новое.
     */
    public synchronized void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log.trace("Error closing socket for '{}': {}", instanceId, e.getMessage());
            }
            socket = null;
        }
    }

    @Contract("_, _ -> new")
    private synchronized @NonNull String sendAndReceive(@NonNull String json, String deviceName) throws IOException {
        Socket s = getOrCreateSocket();
        try {
            // Send: MAGIC + length(BE) + body(windows-1251)
            byte[] body = json.getBytes(CHARSET);
            OutputStream out = s.getOutputStream();
            out.write(MAGIC);
            out.write(ByteBuffer.allocate(4).putInt(body.length).array());
            out.write(body);
            out.flush();
            PollingLogger.logRequestBytes(instanceId, MAGIC.length, body.length);

            // Receive: MAGIC + length(BE) + body(windows-1251)
            InputStream in = s.getInputStream();

            byte[] magic = in.readNBytes(4);
            if (magic.length != 4 || magic[0] != 'P' || magic[1] != '0' || magic[2] != '0' || magic[3] != '1') {
                PollingLogger.logResponseInvalidMagic(instanceId, deviceName, magic);
                throw new IOException("Invalid magic header from " + instanceId);
            }

            byte[] lenBytes = in.readNBytes(4);
            int length = ByteBuffer.wrap(lenBytes).getInt();
            if (length < 0 || length > MAX_RESPONSE_SIZE) {
                PollingLogger.logResponseInvalidLength(instanceId, deviceName, length);
                throw new IOException("Invalid response length from " + instanceId + ": " + length);
            }

            PollingLogger.logResponseHeader(instanceId, deviceName, length);
            return new String(in.readNBytes(length), CHARSET);
        } catch (IOException e) {
            PollingLogger.logSocketError(instanceId, e.getMessage());
            invalidate();
            throw e;
        }
    }

    private Socket getOrCreateSocket() throws IOException {
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            PollingLogger.logSocketReused(instanceId, host, port);
            return socket;
        }
        log.debug("Connecting to PrintSrv '{}' at {}:{}", instanceId, host, port);
        PollingLogger.logSocketCreate(instanceId, host, port);
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setSoTimeout(readTimeoutMs);
        socket = s;
        log.debug("Connected to PrintSrv '{}' at {}:{}", instanceId, host, port);
        PollingLogger.logSocketConnected(instanceId, host, port, connectTimeoutMs, readTimeoutMs);
        return s;
    }

    private void invalidate() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
            PollingLogger.logSocketClosed(instanceId);
        }
    }
}
