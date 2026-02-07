package dev.savushkin.scada.mobile.backend.client;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

@Component
public class SocketTransport_new {

    private static final byte[] MAGIC = new byte[]{'P', '0', '0', '1'};
    private static final Charset CHARSET = Charset.forName("windows-1251");

    private final SocketManager_new socketManager;

    public SocketTransport_new(SocketManager_new socketManager) {
        this.socketManager = socketManager;
    }

    public void sendRequest(@NonNull String request) throws IOException {
        Socket socket = socketManager.getSocket();

        if (socket.isClosed())
            throw new SocketException("Socket is closed");
        if (request.isEmpty())
            throw new IllegalArgumentException("JSON cannot be empty");

        byte[] jsonBytes = request.getBytes(CHARSET);

        OutputStream out = socket.getOutputStream();

        out.write(MAGIC);
        out.write(ByteBuffer.allocate(4).putInt(jsonBytes.length).array());
        out.write(jsonBytes);
        out.flush();
    }

    public @NonNull String getResponse() throws IOException {
        Socket socket = socketManager.getSocket();

        if (socket.isClosed())
            throw new SocketException("Socket is closed");

        InputStream in = socket.getInputStream();

        // Чтение MAGIC заголовка (4 байта: "P001")
        byte[] magic = in.readNBytes(4);
        if (magic.length != 4 || magic[0] != 'P' || magic[1] != '0' || magic[2] != '0' || magic[3] != '1') {
            throw new IOException("Incorrect magic header!");
        }

        // Чтение длины JSON (4 байта, Big Endian int32)
        byte[] lengthByte = in.readNBytes(4);
        int length = ByteBuffer.wrap(lengthByte).getInt();

        if (length < 0 || length > 10 * 1024 * 1024) {
            throw new IOException("Incorrect length: " + length);
        }

        // Чтение тела JSON
        return new String(in.readNBytes(length), CHARSET);
    }
}
