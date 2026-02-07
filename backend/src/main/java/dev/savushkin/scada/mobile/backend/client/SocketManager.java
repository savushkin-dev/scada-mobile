package dev.savushkin.scada.mobile.backend.client;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Socket;

@Component
public class SocketManager {
    private final String IP;
    private final int PORT;
    private Socket socket;

    private SocketManager(
            @Value("${printsrv.ip}") String ip,
            @Value("${printsrv.port}") int port
    ) {
        IP = ip;
        PORT = port;
    }

    @PreDestroy
    public void close() throws IOException {
        if (socket != null && !socket.isClosed())
            socket.close();
    }

    public Socket getSocket() throws IOException {
        if (socket != null)
            return socket;
        socket = new Socket(IP, PORT);
        return socket;
    }
}
