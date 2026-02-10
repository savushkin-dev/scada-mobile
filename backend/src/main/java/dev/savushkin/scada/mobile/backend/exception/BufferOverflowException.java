package dev.savushkin.scada.mobile.backend.exception;

/**
 * Исключение, выбрасываемое при переполнении буфера pending команд.
 * <p>
 * Указывает, что PrintSrv недоступен длительное время и буфер достиг
 * максимального размера (MAX_BUFFER_SIZE).
 */
public class BufferOverflowException extends RuntimeException {

    public BufferOverflowException(String message) {
        super(message);
    }

    public BufferOverflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
