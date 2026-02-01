package scada.mobile.backend;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import scada.mobile.backend.DTO.QueryAllRequestDTO;
import scada.mobile.backend.DTO.QueryAllResponseDTO;
import scada.mobile.backend.DTO.SetUnitVarsRequestDTO;
import scada.mobile.backend.DTO.SetUnitVarsResponseDTO;
import scada.mobile.backend.utils.JsonMapper;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class PrintSrvClient implements Closeable {

    private static final byte[] MAGIC = new byte[]{'P', '0', '0', '1'};
    private static final Charset CHARSET = Charset.forName("windows-1251");
    private final Socket socket;

    public PrintSrvClient(String IP, int PORT) throws IOException {
        socket = new Socket(IP, PORT);
    }

    /**
     * Выполняет команду QueryAll для чтения всех тегов устройства.
     *
     * @param queryAllRequestDTO запрос QueryAll
     * @return полный ответ со всеми данными юнита
     * @throws IOException при ошибках сокета или парсинга
     */
    public QueryAllResponseDTO QueryAll(@NotNull QueryAllRequestDTO queryAllRequestDTO) throws IOException {
        if (socket.isClosed())
            throw new SocketException("Socket is closed");

        String JsonRequest = JsonMapper.mapper().writeValueAsString(queryAllRequestDTO);
        sendCommand(JsonRequest);
        String JsonAnswer = getAnswer();
        return JsonMapper.mapper().readValue(JsonAnswer, QueryAllResponseDTO.class);
    }

    /**
     * Устанавливает значения тегов юнита.
     *
     * @param setUnitVarsRequestDTO запрос SetUnitVars
     * @return частичный ответ с изменёнными полями
     * @throws IOException при ошибках сокета или парсинга
     */
    public SetUnitVarsResponseDTO SetUnitVars(@NotNull SetUnitVarsRequestDTO setUnitVarsRequestDTO) throws IOException {
        if (socket.isClosed())
            throw new SocketException("Socket is closed");

        String JsonRequest = JsonMapper.mapper().writeValueAsString(setUnitVarsRequestDTO);
        sendCommand(JsonRequest);
        String JsonAnswer = getAnswer();
        return JsonMapper.mapper().readValue(JsonAnswer, SetUnitVarsResponseDTO.class);
    }

    /**
     * Отправляет команду в формате протокола PrintSrv.
     */
    void sendCommand(@NotNull String json) throws IOException {
        if (socket.isClosed())
            throw new SocketException("Socket is closed");
        if (json.isEmpty())
            throw new IllegalArgumentException("JSON cannot be empty");

        byte[] jsonBytes = json.getBytes(CHARSET);
        OutputStream out = socket.getOutputStream();
        out.write(MAGIC);
        out.write(ByteBuffer.allocate(4).putInt(jsonBytes.length).array());
        out.write(jsonBytes);
        out.flush();
    }

    /**
     * Получает ответ от сервера в формате протокола PrintSrv.
     */
    @Contract(" -> new")
    @NotNull String getAnswer() throws IOException {
        InputStream in = socket.getInputStream();

        // Чтение MAGIC заголовка (4 байта: "P001")
        byte[] magic = in.readNBytes(4);
        if (magic.length != 4 || magic[0] != 'P' || magic[1] != '0' || magic[2] != '0' || magic[3] != '1') {
            throw new IOException("Incorrect magic header!");
        }

        // Чтение длины JSON (4 байта, Big Endian int32)
        byte[] lengthByte = in.readNBytes(4);
        int length = ByteBuffer.wrap(lengthByte).getInt();

        if (length < 0 || length > 10 * 1024 * 1024) {
            throw new IOException("Incorrect length: " + length);
        }

        // Чтение тела JSON
        return new String(in.readNBytes(length), CHARSET);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
