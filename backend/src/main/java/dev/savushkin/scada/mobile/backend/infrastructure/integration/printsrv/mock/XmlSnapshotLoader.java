package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Загрузчик seed-данных из XML-файлов формата PrintSrv.
 *
 * <h3>Формат файла</h3>
 * <pre>{@code
 * <DeviceUnit xmlns="..." xmlns:d2p1="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
 *   <properties xmlns:d2p1="...">
 *     <d2p1:KeyValueOfstringstring>
 *       <d2p1:Key>ST</d2p1:Key>
 *       <d2p1:Value>1</d2p1:Value>
 *     </d2p1:KeyValueOfstringstring>
 *     ...
 *   </properties>
 * </DeviceUnit>
 * }</pre>
 *
 * <h3>Стратегия загрузки</h3>
 * <ol>
 *   <li>Если {@code snapshotBaseDir} задан и файл {@code {baseDir}/{instanceId}/{Device}___Unit0.xml}
 *       существует — использовать его.</li>
 *   <li>Иначе — classpath {@code mock-snapshots/default/{Device}___Unit0.xml}.</li>
 *   <li>Если и classpath-файл отсутствует — вернуть пустой map и залогировать предупреждение
 *       (не падать, не мешать старту контекста).</li>
 * </ol>
 *
 * <p>Все ошибки парсинга перехватываются: метод никогда не кидает исключений вызывающему.
 */
@Component
@Profile("dev")
public class XmlSnapshotLoader {

    private static final Logger log = LoggerFactory.getLogger(XmlSnapshotLoader.class);
    /**
     * Регулярное выражение для поиска всех XML character references вида {@code &#xNN;}.
     *
     * <p>Используется для удаления ссылок
     * на символы, недопустимые в XML 1.0: управляющие символы 0x00-0x08, 0x0B, 0x0C,
     * 0x0E-0x1F, а также 0xFFFE и 0xFFFF.
     *
     * <p>Примеры в реальных PrintSrv-файлах: {@code &#x15;} (NAK), {@code &#x1D;} (GS1-сепаратор).
     */
    private static final Pattern HEX_CHAR_REF_PATTERN = Pattern.compile("&#x([0-9A-Fa-f]{1,5});");
    /**
     * Namespace для элементов ключ-значение в формате .NET DataContract.
     */
    private static final String KV_NAMESPACE =
            "http://schemas.microsoft.com/2003/10/Serialization/Arrays";
    /**
     * Шаблон имени файла устройства ({@code Line___Unit0.xml}, {@code Printer11___Unit0.xml}, …).
     * Три подчёркивания — формат PrintSrv.
     */
    private static final String FILENAME_TEMPLATE = "%s___Unit0.xml";
    /**
     * Путь к classpath-директории с дефолтными seed-файлами.
     */
    private static final String CLASSPATH_DEFAULT_DIR = "mock-snapshots/default/";

    // ─── Public API ───────────────────────────────────────────────────────────
    /**
     * Путь к classpath-директории с per-instance seed-файлами.
     * Формат: {@code mock-snapshots/{instanceId}/{Device}___Unit0.xml}
     */
    private static final String CLASSPATH_INSTANCE_DIR = "mock-snapshots/";

    /**
     * Возвращает {@code true} если code point допустим в XML 1.0.
     *
     * @param cp Unicode code point для проверки
     */
    private static boolean isValidXml10CodePoint(int cp) {
        return cp == 0x9          // TAB
                || cp == 0xA          // LF
                || cp == 0xD          // CR
                || (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF);
    }

    /**
     * Загружает свойства устройства с приоритетом:
     * filesystem → classpath/{instanceId}/ → classpath/default/ → empty.
     *
     * @param deviceName      имя устройства (например, {@code "CamAgregation"})
     * @param snapshotBaseDir путь к базовой директории seed-файлов; может быть null
     * @param instanceId      ID инстанса (используется как поддиректория)
     * @return Map ключ→значение всех свойств; пустой map при любой ошибке загрузки
     */
    public Map<String, String> loadForDevice(
            String deviceName,
            @Nullable String snapshotBaseDir,
            String instanceId) {

        String filename = FILENAME_TEMPLATE.formatted(deviceName);

        // 1) Filesystem: {baseDir}/{instanceId}/{Device}___Unit0.xml
        if (snapshotBaseDir != null) {
            Path fsPath = Path.of(snapshotBaseDir, instanceId, filename);
            if (Files.exists(fsPath)) {
                log.debug("[{}] Loading {} from filesystem: {}", instanceId, deviceName, fsPath);
                Map<String, String> result = loadFromFile(fsPath);
                if (!result.isEmpty()) {
                    return result;
                }
                log.warn("[{}] Filesystem file {} exists but yielded empty properties, falling back to classpath",
                        instanceId, fsPath);
            }
        }

        // 2) Classpath per-instance: mock-snapshots/{instanceId}/{Device}___Unit0.xml
        String instanceClasspathPath = CLASSPATH_INSTANCE_DIR + instanceId + "/" + filename;
        ClassPathResource instanceResource = new ClassPathResource(instanceClasspathPath);
        if (instanceResource.exists()) {
            log.debug("[{}] Loading {} from classpath (instance): {}", instanceId, deviceName, instanceClasspathPath);
            Map<String, String> result = loadFromClasspath(instanceClasspathPath);
            if (!result.isEmpty()) {
                return result;
            }
            log.warn("[{}] Classpath instance file {} exists but yielded empty properties, falling back to default",
                    instanceId, instanceClasspathPath);
        }

        // 3) Classpath default: mock-snapshots/default/{Device}___Unit0.xml
        String defaultClasspathPath = CLASSPATH_DEFAULT_DIR + filename;
        log.debug("[{}] Loading {} from classpath (default): {}", instanceId, deviceName, defaultClasspathPath);
        Map<String, String> result = loadFromClasspath(defaultClasspathPath);

        if (result.isEmpty()) {
            log.warn("[{}] No seed file found for device {}. Instance will start with empty state " +
                    "(properties will be null in QueryAll responses).", instanceId, deviceName);
        }
        return result;
    }

    /**
     * Парсит XML из файловой системы. Не кидает исключений — возвращает пустой map при ошибке.
     */
    public Map<String, String> loadFromFile(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath)) {
            return parseXml(is, filePath.toString());
        } catch (IOException e) {
            log.warn("Cannot open XML file {}: {}", filePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ─── Private: DOM-парсинг ─────────────────────────────────────────────────

    /**
     * Парсит XML из classpath-ресурса. Не кидает исключений — возвращает пустой map при ошибке.
     */
    public Map<String, String> loadFromClasspath(String classpathResource) {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        if (!resource.exists()) {
            // Штатная ситуация: файл намеренно не добавлен
            log.debug("Classpath resource not found: {}", classpathResource);
            return Collections.emptyMap();
        }
        try (InputStream is = resource.getInputStream()) {
            return parseXml(is, "classpath:" + classpathResource);
        } catch (IOException e) {
            log.warn("Cannot open classpath resource {}: {}", classpathResource, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Парсит поток XML и извлекает все пары ключ→значение.
     *
     * <p>Перед парсингом выполняет санитизацию: удаляет XML character references для
     * символов, недопустимых в XML 1.0. PrintSrv иногда сохраняет бинарные данные
     * (GS1-сепараторы {@code &#x1D;}, счётчики {@code &#x15;}) прямо в XML-значениях.
     *
     * <p>Использует namespace-aware DOM, чтобы корректно обработать
     * {@code d2p1:Key} и {@code d2p1:Value} в .NET DataContract XML.
     *
     * @param is     входной поток; никогда не null
     * @param source описание источника для логирования
     * @return карта ключ→значение; пустая если XML повреждён или не содержит пар
     */
    private @NonNull Map<String, String> parseXml(InputStream is, String source) {
        DocumentBuilder builder = createDocumentBuilder(source);
        if (builder == null) {
            return Collections.emptyMap();
        }

        // Читаем в строку и убираем невалидные XML 1.0 character references.
        // Это необходимо, так как PrintSrv хранит бинарные данные (GS1, ctl chars) в XML-значениях.
        String rawXml;
        try {
            rawXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("IO error while reading {}: {}", source, e.getMessage());
            return Collections.emptyMap();
        }

        String cleanedXml = sanitizeXmlCharRefs(rawXml, source);
        InputStream cleanedIs = new ByteArrayInputStream(cleanedXml.getBytes(StandardCharsets.UTF_8));

        Document doc;
        try {
            doc = builder.parse(cleanedIs);
        } catch (SAXException e) {
            log.warn("Malformed XML in {}: {}", source, e.getMessage());
            return Collections.emptyMap();
        } catch (IOException e) {
            log.warn("IO error while parsing {}: {}", source, e.getMessage());
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();

        // Ищем все <d2p1:KeyValueOfstringstring> в namespace KV_NAMESPACE
        NodeList kvNodes = doc.getElementsByTagNameNS(KV_NAMESPACE, "KeyValueOfstringstring");
        for (int i = 0; i < kvNodes.getLength(); i++) {
            Element kv = (Element) kvNodes.item(i);

            NodeList keyNodes = kv.getElementsByTagNameNS(KV_NAMESPACE, "Key");
            NodeList valNodes = kv.getElementsByTagNameNS(KV_NAMESPACE, "Value");

            if (keyNodes.getLength() == 0) {
                log.trace("Skipping KV node #{} in {}: no Key element", i, source);
                continue;
            }

            String key = keyNodes.item(0).getTextContent();
            // Value может быть пустым тегом (<d2p1:Value />) — getTextContent() вернёт ""
            String value = valNodes.getLength() > 0 ? valNodes.item(0).getTextContent() : "";

            if (key == null || key.isBlank()) {
                log.trace("Skipping KV node #{} in {}: blank key", i, source);
                continue;
            }

            result.put(key.trim(), value != null ? value : "");
        }

        log.trace("Loaded {} properties from {}", result.size(), source);
        return result;
    }

    /**
     * Удаляет XML character references для символов, недопустимых в XML 1.0.
     *
     * <p>Допустимые символы XML 1.0: {@code #x9}, {@code #xA}, {@code #xD},
     * {@code [#x20-#xD7FF]}, {@code [#xE000-#xFFFD]}, {@code [#x10000-#x10FFFF]}.
     *
     * @param xml    исходный XML как строка
     * @param source идентификатор источника для лог-сообщений
     * @return очищенная строка
     */
    String sanitizeXmlCharRefs(String xml, String source) {
        return HEX_CHAR_REF_PATTERN.matcher(xml).replaceAll(matchResult -> {
            int codePoint;
            try {
                codePoint = Integer.parseInt(matchResult.group(1), 16);
            } catch (NumberFormatException e) {
                return matchResult.group(); // не трогаем нераспознанные
            }
            if (isValidXml10CodePoint(codePoint)) {
                return matchResult.group(); // оставляем допустимые
            }
            log.trace("Stripped invalid XML 1.0 char ref {} in {}", matchResult.group(), source);
            return "";
        });
    }

    /**
     * Создаёт namespace-aware {@link DocumentBuilder}.
     *
     * <p>Отдельный метод, чтобы перехватить исключение конфигурации без try-catch в основном методе.
     * В JVM 21 {@link ParserConfigurationException} на стандартных настройках не кидается никогда.
     */
    @Nullable
    private DocumentBuilder createDocumentBuilder(String source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Обязательно: без namespace-aware getElementsByTagNameNS не найдёт ничего
            factory.setNamespaceAware(true);
            // Безопасность: отключаем внешние entity-ссылки (XXE-защита)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            // На практике не случается на стандартной JVM, но защищаемся
            log.error("Cannot create DocumentBuilder for {}: {}", source, e.getMessage());
            return null;
        }
    }
}
