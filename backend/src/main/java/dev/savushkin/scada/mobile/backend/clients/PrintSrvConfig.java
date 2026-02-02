package dev.savushkin.scada.mobile.backend.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Configuration
public class PrintSrvConfig {

    private final String ip;
    private final int port;

    public PrintSrvConfig(
            @Value("${printsrv.ip}") String ip,
            @Value("${printsrv.port}") int port
    ) {
        this.ip = ip;
        this.port = port;
    }

    @Bean
    public PrintSrvClient printSrvClient(ObjectMapper objectMapper) throws IOException {
        return new PrintSrvClient(ip, port, objectMapper);
    }
}
