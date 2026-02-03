package dev.savushkin.scada.mobile.backend.client;

import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class PrintSrvClient {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvClient.class);
    private static final int LOG_PREVIEW_CHARS = 200;
    private final ObjectMapper objectMapper;
    private final PrintSrvConnectionPool printSrvConnectionPool;

    public PrintSrvClient(PrintSrvConnectionPool printSrvConnectionPool, ObjectMapper objectMapper) {
        this.printSrvConnectionPool = printSrvConnectionPool;
        this.objectMapper = objectMapper;
    }

    private static String preview(String s) {
        if (s == null) return "null";
        String cleaned = s.replace("\r", "\\r").replace("\n", "\\n");
        return cleaned.length() <= LOG_PREVIEW_CHARS ? cleaned : cleaned.substring(0, LOG_PREVIEW_CHARS) + "...";
    }

    public QueryAllResponseDTO queryAll(QueryAllRequestDTO queryAllRequestDTO) throws IOException, InterruptedException {
        return executeWithOneRetry(
                "queryAll",
                () -> objectMapper.writeValueAsString(queryAllRequestDTO),
                json -> objectMapper.readValue(json, QueryAllResponseDTO.class)
        );
    }

    public SetUnitVarsResponseDTO SetUnitVars(SetUnitVarsRequestDTO setUnitVarsRequestDTO) throws IOException, InterruptedException {
        return executeWithOneRetry(
                "SetUnitVars",
                () -> objectMapper.writeValueAsString(setUnitVarsRequestDTO),
                json -> objectMapper.readValue(json, SetUnitVarsResponseDTO.class)
        );
    }

    private <T> T executeWithOneRetry(String op, RequestSupplier requestSupplier, ResponseParser<T> responseParser)
            throws IOException, InterruptedException {

        Exception firstFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            long startedAt = System.currentTimeMillis();
            PrintSrvConnection connection = null;
            String jsonRequest = null;
            String jsonResponse = null;
            boolean success = false;
            Throwable failure = null;

            try {
                connection = printSrvConnectionPool.borrowConnection();

                if (log.isDebugEnabled()) {
                    log.debug("{}(): attempt {} borrowed con={} (thread={})", op, attempt, connection.getId(), Thread.currentThread().getName());
                }

                jsonRequest = requestSupplier.get();
                connection.sendRequest(jsonRequest);
                jsonResponse = connection.receiveResponse();

                T dto = responseParser.parse(jsonResponse);
                success = true;

                log.info("{}(): OK attempt={} con={} ms={} (thread={})",
                        op,
                        attempt,
                        connection.getId(),
                        (System.currentTimeMillis() - startedAt),
                        Thread.currentThread().getName());

                return dto;

            } catch (Exception e) {
                failure = e;
                if (e instanceof PrintSrvProtocolException pse && jsonResponse == null) {
                    jsonResponse = pse.getResponseBody();
                }

                String conId = (connection == null) ? "null" : connection.getId();
                log.error("{}(): FAILED attempt={} con={} ms={} reqPreview='{}' respPreview='{}' (thread={})",
                        op,
                        attempt,
                        conId,
                        (System.currentTimeMillis() - startedAt),
                        preview(jsonRequest),
                        preview(jsonResponse),
                        Thread.currentThread().getName(),
                        e);

                if (attempt == 1) {
                    firstFailure = e;
                }

                // На первой ошибке делаем ретрай. На второй — пробрасываем.
                if (attempt == 2) {
                    if (firstFailure != null) {
                        e.addSuppressed(firstFailure);
                    }
                    throw e;
                }

                log.warn("{}(): will retry once after failure (attempt=1)", op);

            } finally {
                if (connection != null) {
                    if (success) {
                        printSrvConnectionPool.returnConnection(connection);
                        if (log.isDebugEnabled()) {
                            log.debug("{}(): attempt {} returned con={} (thread={})", op, attempt, connection.getId(), Thread.currentThread().getName());
                        }
                    } else {
                        printSrvConnectionPool.invalidateAndReplace(connection, failure);
                        if (log.isDebugEnabled()) {
                            log.debug("{}(): attempt {} invalidated con={} (thread={})", op, attempt, connection.getId(), Thread.currentThread().getName());
                        }
                    }
                }
            }
        }

        throw new IllegalStateException(op + ": unreachable");
    }

    @FunctionalInterface
    private interface RequestSupplier {
        String get() throws IOException;
    }

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(String json) throws IOException;
    }
}
