package scada.mobile.backend;

import scada.mobile.backend.DTO.ParametersDTO;
import scada.mobile.backend.DTO.QueryAllRequestDTO;
import scada.mobile.backend.DTO.QueryAllResponseDTO;
import scada.mobile.backend.DTO.SetUnitVarsRequestDTO;
import scada.mobile.backend.DTO.SetUnitVarsResponseDTO;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class Main {

    static {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }

    public static void main(String[] args) {
        String IP = "127.0.0.1";
        int PORT = 10101;

        try (PrintSrvClient printSrvClient = new PrintSrvClient(IP, PORT)) {

            // ========== QueryAll ==========
            QueryAllRequestDTO queryAllRequestDTO = new QueryAllRequestDTO(
                    "Line",
                    "QueryAll"
            );

            QueryAllResponseDTO queryAllResponseDTO = printSrvClient.QueryAll(queryAllRequestDTO);

            System.out.println("Результат команды QueryAll: " + queryAllResponseDTO);

            // ========== SetUnitVars ==========
            SetUnitVarsRequestDTO setUnitVarsRequestDTO = new SetUnitVarsRequestDTO(
                    "Line",
                    1,
                    "SetUnitVars",
                    new ParametersDTO("766")
            );

            SetUnitVarsResponseDTO setUnitVarsResponseDTO = printSrvClient.SetUnitVars(setUnitVarsRequestDTO);

            System.out.println("Результат команды SetUnitVars: " + setUnitVarsResponseDTO);

            // ========== QueryAll (ещё раз для проверки) ==========
            queryAllRequestDTO = new QueryAllRequestDTO(
                    "Line",
                    "QueryAll"
            );

            queryAllResponseDTO = printSrvClient.QueryAll(queryAllRequestDTO);

            System.out.println("Результат команды QueryAll: " + queryAllResponseDTO);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
