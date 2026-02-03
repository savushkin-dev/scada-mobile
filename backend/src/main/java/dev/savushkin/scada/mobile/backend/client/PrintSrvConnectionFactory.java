package dev.savushkin.scada.mobile.backend.client;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PrintSrvConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvConnectionFactory.class);

    private final String ip;
    private final int port;

    public PrintSrvConnectionFactory(
            @Value("${printsrv.ip}") String ip,
            @Value("${printsrv.port}") int port
    ) {
        this.ip = ip;
        this.port = port;
    }

    public PrintSrvConnection createConnection() throws IOException {
        PrintSrvConnection con = new PrintSrvConnection(ip, port);
        log.debug("Created PrintSrvConnection con={} to {}:{}", con.getId(), ip, port);
        return con;
    }

    public boolean isValid(@NonNull PrintSrvConnection printSrvConnection) {
        return printSrvConnection.isValid();
    }
}
