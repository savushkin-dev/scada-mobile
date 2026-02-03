package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CommandsService {

    private final PrintSrvClient printSrvClient;

    public CommandsService(PrintSrvClient printSrvClient) {
        this.printSrvClient = printSrvClient;
    }

    public QueryAllResponseDTO queryAll() throws IOException, InterruptedException {
        QueryAllRequestDTO queryAllRequestDTO = new QueryAllRequestDTO("Line", "QueryAll");
        return printSrvClient.queryAll(queryAllRequestDTO);
    }
}
