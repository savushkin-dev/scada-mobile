# 🎓 Руководство: Connection Pool для Spring (для начинающих)

## 📖 Что вы будете изучать

1. **Spring Bean Lifecycle** - как Spring создает и уничтожает бины
2. **Connection Pooling Pattern** - паттерн переиспользования соединений
3. **Thread Safety** - как сделать код безопасным для многопоточности
4. **Resource Management** - правильная работа с ресурсами (сокеты, файлы)

---

## 🎯 Цель

Сейчас у вас:

- ❌ Один Socket на все запросы (не thread-safe!)
- ❌ Если соединение оборвется - приложение сломается
- ❌ При 10 одновременных запросах будут конфликты

Нужно:

- ✅ Пул из N соединений (например, 5 штук)
- ✅ Каждый запрос берет свободное соединение, использует и возвращает обратно
- ✅ Если все заняты - запрос ждет
- ✅ Если соединение сломалось - создается новое

---

## 📋 План реализации (5 файлов)

### Файл 1: `PrintSrvConnection.java` (wrapper для Socket)

**Зачем?** Обернуть Socket + добавить методы работы с протоколом

```java
package dev.savushkin.scada.mobile.backend.client;

import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Обертка над Socket для работы с протоколом PrintSrv.
 * Это "физическое" соединение с сервером.
 */
public class PrintSrvConnection implements Closeable {
    private static final byte[] MAGIC = new byte[]{'P', '0', '0', '1'};
    private static final Charset CHARSET = Charset.forName("windows-1251");
    
    private final Socket socket;
    private final ObjectMapper objectMapper;
    private final String host;
    private final int port;
    
    public PrintSrvConnection(String host, int port, ObjectMapper objectMapper) throws IOException {
        this.host = host;
        this.port = port;
        this.objectMapper = objectMapper;
        this.socket = new Socket(host, port);
    }
    
    /**
     * Проверяет, живо ли соединение
     */
    public boolean isValid() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
    
    /**
     * Отправляет JSON-запрос
     */
    public void sendRequest(String json) throws IOException {
        // TODO: реализуйте логику из sendCommand() старого PrintSrvClient
    }
    
    /**
     * Получает JSON-ответ
     */
    public String receiveResponse() throws IOException {
        // TODO: реализуйте логику из getAnswer() старого PrintSrvClient
    }
    
    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}
```

**Задание:** Скопируйте логику из вашего `PrintSrvClient` в методы `sendRequest` и `receiveResponse`.

---

### Файл 2: `PrintSrvConnectionFactory.java` (фабрика соединений)

**Зачем?** Создавать новые соединения по требованию

```java
package dev.savushkin.scada.mobile.backend.client;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Фабрика для создания новых соединений с PrintSrv.
 * Spring будет использовать эту фабрику для пула.
 */
@Component
public class PrintSrvConnectionFactory {
    
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    
    public PrintSrvConnectionFactory(
            @Value("${printsrv.ip}") String host,
            @Value("${printsrv.port}") int port,
            ObjectMapper objectMapper
    ) {
        this.host = host;
        this.port = port;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Создает новое соединение
     */
    public PrintSrvConnection createConnection() throws IOException {
        return new PrintSrvConnection(host, port, objectMapper);
    }
    
    /**
     * Проверяет, валидно ли соединение
     */
    public boolean validateConnection(PrintSrvConnection connection) {
        return connection != null && connection.isValid();
    }
}
```

**Задание:** Добавьте `@Value` импорт и убедитесь, что понимаете, как Spring инжектит значения из `application.yaml`.

---

### Файл 3: `PrintSrvConnectionPool.java` (пул соединений)

**Зачем?** Хранить и переиспользовать соединения

```java
package dev.savushkin.scada.mobile.backend.client;

import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.*;

/**
 * Пул соединений с PrintSrv.
 * Использует BlockingQueue для thread-safe работы.
 */
@Component
public class PrintSrvConnectionPool {
    
    private final BlockingQueue<PrintSrvConnection> availableConnections;
    private final PrintSrvConnectionFactory factory;
    private final int maxPoolSize;
    
    public PrintSrvConnectionPool(
            PrintSrvConnectionFactory factory,
            @Value("${printsrv.pool.size:5}") int maxPoolSize
    ) throws IOException {
        this.factory = factory;
        this.maxPoolSize = maxPoolSize;
        this.availableConnections = new LinkedBlockingQueue<>(maxPoolSize);
        
        // Инициализация пула при старте
        for (int i = 0; i < maxPoolSize; i++) {
            availableConnections.add(factory.createConnection());
        }
    }
    
    /**
     * Берет соединение из пула (блокируется, если все заняты)
     */
    public PrintSrvConnection borrowConnection() throws InterruptedException, IOException {
        PrintSrvConnection connection = availableConnections.take(); // Ждет, если пусто
        
        // Если соединение сломалось - создаем новое
        if (!factory.validateConnection(connection)) {
            try {
                connection.close();
            } catch (IOException ignored) {}
            connection = factory.createConnection();
        }
        
        return connection;
    }
    
    /**
     * Возвращает соединение в пул
     */
    public void returnConnection(PrintSrvConnection connection) {
        if (connection != null && factory.validateConnection(connection)) {
            availableConnections.offer(connection);
        }
    }
    
    /**
     * Закрывает все соединения при остановке приложения
     */
    @PreDestroy
    public void destroy() {
        for (PrintSrvConnection conn : availableConnections) {
            try {
                conn.close();
            } catch (IOException e) {
                // Логируем, но не падаем
            }
        }
        availableConnections.clear();
    }
}
```

**Ключевые концепции:**

- `BlockingQueue<T>` - потокобезопасная очередь (из `java.util.concurrent`)
- `take()` - берет элемент или ЖДЕТ, если очередь пуста
- `offer()` - кладет элемент обратно
- `@PreDestroy` - Spring вызовет этот метод при остановке приложения

**Задание:** Добавьте логирование (slf4j) в методы `borrowConnection` и `returnConnection`, чтобы видеть, что
происходит.

---

### Файл 4: `PrintSrvClient.java` (НОВЫЙ - использует пул)

**Зачем?** Высокоуровневое API для бизнес-логики

```java
package dev.savushkin.scada.mobile.backend.client;

import dev.savushkin.scada.mobile.backend.dto.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Клиент для работы с PrintSrv через пул соединений.
 * Этот класс теперь stateless и thread-safe!
 */
@Component
public class PrintSrvClient {
    
    private final PrintSrvConnectionPool connectionPool;
    private final ObjectMapper objectMapper;
    
    public PrintSrvClient(
            PrintSrvConnectionPool connectionPool,
            ObjectMapper objectMapper
    ) {
        this.connectionPool = connectionPool;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Выполняет команду QueryAll
     */
    public QueryAllResponseDTO queryAll(QueryAllRequestDTO request) throws IOException, InterruptedException {
        PrintSrvConnection connection = null;
        try {
            // 1. Берем соединение из пула
            connection = connectionPool.borrowConnection();
            
            // 2. Выполняем запрос
            String jsonRequest = objectMapper.writeValueAsString(request);
            connection.sendRequest(jsonRequest);
            String jsonResponse = connection.receiveResponse();
            
            // 3. Парсим ответ
            return objectMapper.readValue(jsonResponse, QueryAllResponseDTO.class);
            
        } finally {
            // 4. ВСЕГДА возвращаем соединение обратно!
            if (connection != null) {
                connectionPool.returnConnection(connection);
            }
        }
    }
    
    /**
     * Устанавливает переменные юнита
     */
    public SetUnitVarsResponseDTO setUnitVars(SetUnitVarsRequestDTO request) throws IOException, InterruptedException {
        // TODO: реализуйте по аналогии с queryAll()
    }
}
```

**Ключевая концепция:** Паттерн "borrow-return":

```
1. Взять ресурс
2. Использовать
3. ВСЕГДА вернуть (даже если была ошибка) → try-finally
```

**Задание:** Реализуйте метод `setUnitVars()` по аналогии с `queryAll()`.

---

### Файл 5: `application.yaml` (конфигурация)

Добавьте настройки пула:

```yaml
spring:
  application:
    name: scada.mobile.backend

printsrv:
  ip: 127.0.0.1
  port: 10101
  pool:
    size: 5  # Количество соединений в пуле
```

---

## 🧪 Как тестировать

### Тест 1: Проверка работы пула

Создайте тестовый endpoint:

```java
@GetMapping("/test-pool")
public String testPool() throws Exception {
    long start = System.currentTimeMillis();
    
    // Создаем 10 параллельных запросов
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<Future<?>> futures = new ArrayList<>();
    
    for (int i = 0; i < 10; i++) {
        futures.add(executor.submit(() -> {
            try {
                commandsService.queryAll();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
    
    // Ждем завершения всех
    for (Future<?> future : futures) {
        future.get();
    }
    
    executor.shutdown();
    long time = System.currentTimeMillis() - start;
    
    return "10 requests completed in " + time + "ms";
}
```

Если пул работает правильно:

- ✅ Все 10 запросов выполнятся успешно
- ✅ Первые 5 запустятся сразу, остальные 5 подождут
- ✅ В логах увидите "borrowing" и "returning" соединений

---

## 📚 Что вы изучите

### 1. **Spring Dependency Injection**

```java
@Component  // Spring создаст бин
public class PrintSrvClient {
    // Spring автоматически инжектит зависимости через конструктор
    public PrintSrvClient(PrintSrvConnectionPool pool) { ... }
}
```

### 2. **Spring Bean Lifecycle**

```java
@PreDestroy  // Вызывается при остановке приложения
public void destroy() { ... }
```

### 3. **Thread Safety через BlockingQueue**

```java
BlockingQueue<T> queue = new LinkedBlockingQueue<>();
T item = queue.take();  // Потокобезопасно!
queue.offer(item);      // Потокобезопасно!
```

### 4. **Resource Management Pattern**

```java
try {
    Resource r = pool.borrow();
    // Используем
} finally {
    pool.return(r);  // ВСЕГДА возвращаем
}
```

---

## 🎯 Порядок реализации

1. ✅ Создайте `PrintSrvConnection` - скопируйте логику из старого клиента
2. ✅ Создайте `PrintSrvConnectionFactory` - простой класс
3. ✅ Создайте `PrintSrvConnectionPool` - самая сложная часть, изучите `BlockingQueue`
4. ✅ Переделайте `PrintSrvClient` - используйте пул
5. ✅ Обновите `application.yaml`
6. ✅ Удалите старый `PrintSrvConfig` (он больше не нужен)
7. ✅ Протестируйте!

---

## 💡 Подсказки

### Если запутались в `BlockingQueue`:

```java
// Это ПОТОКОБЕЗОПАСНАЯ очередь!
BlockingQueue<String> queue = new LinkedBlockingQueue<>(5);

// Поток 1
queue.put("item");  // Кладет, ждет если полно

// Поток 2
String item = queue.take();  // Берет, ждет если пусто
```

### Если не понимаете `@PreDestroy`:

```java
// Spring вызывает автоматически:
Application starts → Constructor → @PostConstruct → ... → @PreDestroy → shutdown
```

### Если возникли проблемы с импортами:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
```

---

## 🎓 Дополнительные материалы

- Spring Bean Lifecycle: https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html
- BlockingQueue tutorial: https://jenkov.com/tutorials/java-util-concurrent/blockingqueue.html
- Connection Pool Pattern: https://refactoring.guru/design-patterns/object-pool

---

## ❓ Вопросы для самопроверки

1. Почему `BlockingQueue` безопасен для многопоточности?
2. Что произойдет, если не вернуть соединение в пул?
3. Зачем нужен `@PreDestroy`?
4. Как Spring понимает, какие бины инжектить в конструктор?
5. Что произойдет, если все 5 соединений заняты, а пришел 6-й запрос?

**Ответы:**

1. Он использует внутренние locks (synchronized)
2. Пул "протечет" - соединения закончатся, запросы будут зависать
3. Чтобы закрыть сокеты при остановке приложения (иначе утечка ресурсов)
4. По типу параметра в конструкторе (автоматический autowiring)
5. Метод `take()` заблокирует поток, пока кто-то не вернет соединение

---

Удачи! 🚀 Если застрянете на каком-то шаге - спрашивайте конкретно про этот шаг!
