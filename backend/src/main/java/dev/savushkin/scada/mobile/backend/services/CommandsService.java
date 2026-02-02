package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.clients.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.io.IOException;

@Service
public class CommandsService implements Closeable {

    private final PrintSrvClient printSrvClient;

    public CommandsService(PrintSrvClient printSrvClient) {
        this.printSrvClient = printSrvClient;
    }

    public QueryAllResponseDTO queryAll() throws IOException {
        QueryAllRequestDTO queryAllRequestDTO = new QueryAllRequestDTO("Line", "QueryAll");
        return printSrvClient.queryAll(queryAllRequestDTO);
    }

    @Override
    public void close() throws IOException {
        printSrvClient.close();
    }
}
