package dev.savushkin.scada.mobile.backend.client;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Contract;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class PrintSrvConnection implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvConnection.class);

    private static final byte[] MAGIC = new byte[]{'P', '0', '0', '1'};
    private static final Charset CHARSET = Charset.forName("windows-1251");

    private static final int LOG_PREVIEW_CHARS = 200;

    private final String ip;
    private final int port;
    private final Socket socket;
    private final String id;

    public PrintSrvConnection(@NonNull String ip, int port) throws IOException {
        this.ip = ip;
        this.port = port;
        this.socket = new Socket(ip, port);
        this.id = Integer.toHexString(System.identityHashCode(this));

        log.debug("[con={}] created socket to {}:{} (thread={})", id, ip, port, Thread.currentThread().getName());
    }

    private static String preview(String s) {
        if (s == null) return "null";
        String cleaned = s.replace("\r", "\\r").replace("\n", "\\n");
        return cleaned.length() <= LOG_PREVIEW_CHARS ? cleaned : cleaned.substring(0, LOG_PREVIEW_CHARS) + "...";
    }

    public boolean isValid() {
        return !socket.isClosed() && socket.isConnected();
    }

    public String getId() {
        return id;
    }

    /**
     * Отправляет команду в формате протокола PrintSrv.
     */
    public void sendRequest(@NonNull String json) throws IOException {
        if (socket.isClosed())
            throw new SocketException("Socket is closed");
        if (json.isEmpty())
            throw new IllegalArgumentException("JSON cannot be empty");

        byte[] jsonBytes = json.getBytes(CHARSET);

        if (log.isDebugEnabled()) {
            log.debug("[con={}] -> sendRequest bytes={} preview='{}' (thread={})",
                    id,
                    jsonBytes.length,
                    preview(json),
                    Thread.currentThread().getName());
        }

        OutputStream out = socket.getOutputStream();

        out.write(MAGIC);
        out.write(ByteBuffer.allocate(4).putInt(jsonBytes.length).array());
        out.write(jsonBytes);
        out.flush();
    }

    /**
     * Получает ответ от сервера в формате протокола PrintSrv.
     */
    @Contract(" -> new")
    public String receiveResponse() throws IOException {
        InputStream in = socket.getInputStream();

        // Чтение MAGIC заголовка (4 байта: "P001")
        byte[] magic = in.readNBytes(4);
        if (magic.length != 4 || magic[0] != 'P' || magic[1] != '0' || magic[2] != '0' || magic[3] != '1') {
            String got = new String(magic, CHARSET);
            log.warn("[con={}] <- invalid magic header (len={}) got='{}' (thread={})", id, magic.length, preview(got), Thread.currentThread().getName());
            throw new IOException("Incorrect magic header!");
        }

        // Чтение длины JSON (4 байта, Big Endian int32)
        byte[] lengthByte = in.readNBytes(4);
        int length = ByteBuffer.wrap(lengthByte).getInt();

        if (length < 0 || length > 10 * 1024 * 1024) {
            log.warn("[con={}] <- invalid length {} (thread={})", id, length, Thread.currentThread().getName());
            throw new IOException("Incorrect length: " + length);
        }

        // Чтение тела JSON
        String body = new String(in.readNBytes(length), CHARSET);

        if (log.isDebugEnabled()) {
            log.debug("[con={}] <- receiveResponse bytes={} preview='{}' (thread={})",
                    id,
                    length,
                    preview(body),
                    Thread.currentThread().getName());
        }

        // PrintSrv иногда возвращает короткую текстовую ошибку (например, "Fail") вместо JSON.
        // По договорённости считаем это ошибкой, после которой соединение непригодно.
        if (body.equalsIgnoreCase("Fail")) {
            throw new PrintSrvProtocolException("PrintSrv returned 'Fail' response", body);
        }

        return body;
    }

    @Override
    public void close() throws IOException {
        log.debug("[con={}] closing socket to {}:{} (thread={})", id, ip, port, Thread.currentThread().getName());
        socket.close();
    }
}
