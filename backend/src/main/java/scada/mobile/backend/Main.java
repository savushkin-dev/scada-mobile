package scada.mobile.backend;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Main {

    static {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }

    private static final String IP = "127.0.0.1";
    private static final int PORT = 10101;
    private static final byte[] MAGIC = new byte[] {'P', '0', '0', '1'};
    private static final Charset CHARSET = Charset.forName("windows-1251");

    static void main() throws IOException {
        // 1. Посмотрели всё
        System.out.println("1. QueryAll\n");
        String result1 = sendCommand("""
            {
              "DeviceName": "Line",
              "Command": "QueryAll"
            }
            """);
        System.out.println(result1 + "\n\n");

        // 2. Установили значение (Unit=1, т.к. сервер использует 1-based индексацию: 1=u1, 2=u2...)
        // Ответ "Fail" - это особенность сервера, операция выполняется успешно!
        System.out.println("2. SetUnitVars\n");
        String result2 = sendCommand("""
            {
              "DeviceName": "Line",
              "Unit": 1,
              "Command": "SetUnitVars",
              "Parameters": {
                "command": "555"
              }
            }
            """);
        System.out.println(result2 + "\n\n");

        // 3. Посмотрели всё
        System.out.println("3. QueryAll\n");
        String result3 = sendCommand("""
            {
              "DeviceName": "Line",
              "Command": "QueryAll"
            }
            """);
        System.out.println(result3);
    }

    private static String sendCommand(String json) throws IOException {
        try (Socket socket = new Socket(IP, PORT)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] jsonBytes = json.getBytes(CHARSET);

            // Отправка: [MAGIC][LENGTH][JSON]
            out.write(MAGIC);
            out.write(ByteBuffer.allocate(4).putInt(jsonBytes.length).array());
            out.write(jsonBytes);
            out.flush();

            // Чтение ответа
            byte[] response = readFrame(in);
            return new String(response, CHARSET);
        }
    }

    private static byte[] readFrame(InputStream in) throws IOException {
        // 1. Читаем магию
        byte[] magic = in.readNBytes(4);
        if (magic.length != 4 ||
                magic[0] != 'P' || magic[1] != '0' ||
                magic[2] != '0' || magic[3] != '1') {
            throw new IOException("Неверный magic header");
        }

        // 2. Читаем длину
        byte[] lenBytes = in.readNBytes(4);
        int length = ByteBuffer.wrap(lenBytes).getInt();

        if (length < 0 || length > 10 * 1024 * 1024) {
            throw new IOException("Некорректная длина: " + length);
        }

        // 3. Читаем тело
        return in.readNBytes(length);
    }
}
