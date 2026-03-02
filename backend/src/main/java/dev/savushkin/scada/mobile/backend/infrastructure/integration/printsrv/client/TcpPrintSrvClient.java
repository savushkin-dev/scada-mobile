package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
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
        String response = sendAndReceive(json);
        return objectMapper.readValue(response, QueryAllResponseDTO.class);
    }

    @Override
    public boolean isAlive() {
        Socket s = socket;
        return s != null && !s.isClosed() && s.isConnected();
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

    private synchronized String sendAndReceive(String json) throws IOException {
        Socket s = getOrCreateSocket();
        try {
            // Send: MAGIC + length(BE) + body(windows-1251)
            byte[] body = json.getBytes(CHARSET);
            OutputStream out = s.getOutputStream();
            out.write(MAGIC);
            out.write(ByteBuffer.allocate(4).putInt(body.length).array());
            out.write(body);
            out.flush();

            // Receive: MAGIC + length(BE) + body(windows-1251)
            InputStream in = s.getInputStream();

            byte[] magic = in.readNBytes(4);
            if (magic.length != 4 || magic[0] != 'P' || magic[1] != '0' || magic[2] != '0' || magic[3] != '1') {
                throw new IOException("Invalid magic header from " + instanceId);
            }

            byte[] lenBytes = in.readNBytes(4);
            int length = ByteBuffer.wrap(lenBytes).getInt();
            if (length < 0 || length > MAX_RESPONSE_SIZE) {
                throw new IOException("Invalid response length from " + instanceId + ": " + length);
            }

            return new String(in.readNBytes(length), CHARSET);
        } catch (IOException e) {
            invalidate();
            throw e;
        }
    }

    private Socket getOrCreateSocket() throws IOException {
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            return socket;
        }
        log.debug("Connecting to PrintSrv '{}' at {}:{}", instanceId, host, port);
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setSoTimeout(readTimeoutMs);
        socket = s;
        log.debug("Connected to PrintSrv '{}' at {}:{}", instanceId, host, port);
        return s;
    }

    private void invalidate() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
        }
    }
}
