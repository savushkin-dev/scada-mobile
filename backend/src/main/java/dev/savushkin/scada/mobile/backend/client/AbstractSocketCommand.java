package dev.savushkin.scada.mobile.backend.client;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

public abstract class AbstractSocketCommand<T, R> {

    private final SocketTransport socketTransport;
    private final ObjectMapper objectMapper;

    public AbstractSocketCommand(SocketTransport socketTransport, ObjectMapper objectMapper) {
        this.socketTransport = socketTransport;
        this.objectMapper = objectMapper;
    }

    public R execute(T request) throws IOException {
        socketTransport.sendRequest(objectMapper.writeValueAsString(request));

        String response = socketTransport.getResponse();
        if (response.isEmpty())
            throw new IllegalArgumentException("Response is empty");

        return objectMapper.readValue(response, getClassResponse());
    }

    protected abstract Class<R> getClassResponse();
}
