package dev.savushkin.scada.mobile.backend.client;

/**
 * Ошибка протокола/ответа PrintSrv, при которой текущее соединение стоит считать непригодным.
 * <p>
 * Например: сервер вернул текстовую ошибку вроде "Fail" вместо ожидаемого JSON-ответа.
 */
public class PrintSrvProtocolException extends RuntimeException {

    private final String responseBody;

    public PrintSrvProtocolException(String message) {
        super(message);
        this.responseBody = null;
    }

    public PrintSrvProtocolException(String message, Throwable cause) {
        super(message, cause);
        this.responseBody = null;
    }

    public PrintSrvProtocolException(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
