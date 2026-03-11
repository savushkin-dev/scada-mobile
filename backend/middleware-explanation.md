# Middleware в Spring Boot — объяснение

## Что такое middleware?

**Middleware** — общий термин из мира веб-разработки. Это код, который стоит *между* входящим HTTP-запросом и твоей бизнес-логикой (контроллером). Он перехватывает каждый запрос/ответ и может:
- **читать/изменять** заголовки, тело;
- **прерывать** цепочку (например, вернуть 401 до контроллера);
- **добавлять** данные в контекст (например, requestId в MDC);
- **замерять** время, логировать, считать метрики.

В Spring Boot **нет официального термина "middleware"** — вместо него используется несколько механизмов.

---

## Механизмы middleware в Spring Boot

### 1. `jakarta.servlet.Filter` (Servlet Filter)
Самый низкоуровневый. Работает **до** Spring MVC.  
Именно его реализует твой `MdcFilter` через `OncePerRequestFilter`.

**Жизненный цикл:**
```
Request → Filter → DispatcherServlet → Interceptor → Controller → Interceptor → Filter → Response
```

**Когда использовать:** логирование, MDC, аутентификация, GZIP, rate limiting.

**Пример в твоём проекте:**
`MdcFilter` — привязывает `requestId`, `method`, `uri` к MDC перед любым другим кодом.

---

### 2. `HandlerInterceptor` (Spring MVC Interceptor)
Работает **внутри** Spring MVC, уже после DispatcherServlet.
Имеет 3 метода: `preHandle`, `postHandle`, `afterCompletion`.

**Когда использовать:** авторизация на уровне контроллеров, добавление заголовков в ответ, аудит конкретных endpoints.

**Отличие от Filter:** Interceptor знает, какой контроллер/метод будет вызван. Filter — нет.

---

### 3. `WebMvcConfigurer` (конфигурационные хуки MVC)
Не перехватчик как таковой, но позволяет **глобально настроить** поведение MVC:
- добавить `Interceptor`-ы;
- настроить CORS (именно это делает твой `CorsConfig`);
- добавить форматтеры, конвертеры.

---

## CORS — это middleware?

**Да, в широком смысле.** CORS — это политика браузера, а обработка CORS на бэкенде — это именно middleware-логика:
- перехватить входящий запрос;
- проверить заголовок `Origin`;
- добавить в ответ нужные `Access-Control-*` заголовки;
- при preflight (`OPTIONS`) — ответить сразу, не доходя до контроллера.

**В твоём проекте** CORS реализован через `CorsConfig implements WebMvcConfigurer`.  
Spring под капотом регистрирует `CorsFilter` автоматически.

---

## Порядок выполнения в твоём проекте

```
HTTP Request
    │
    ▼
[MdcFilter]          ← Filter (HIGHEST_PRECEDENCE)
    │  requestId, method, uri → MDC
    ▼
[CorsFilter]         ← Spring регистрирует из CorsConfig автоматически
    │  Проверка Origin, preflight handling
    ▼
[DispatcherServlet]  ← Spring MVC front controller
    │
    ▼
[Controller]         ← Бизнес-логика
    │
    ▼
HTTP Response
```

---

## Что ещё можно добавить (типичные middleware-задачи)

| Задача | Механизм | Когда нужно |
|---|---|---|
| Rate limiting (ограничение запросов) | Filter | Если нет API Gateway |
| Валидация JWT / API Key | Filter или Spring Security | При добавлении аутентификации |
| Логирование тела запросов/ответов | Filter с `ContentCachingRequestWrapper` | При отладке |
| Заголовки безопасности (CSP, HSTS) | Filter или `WebMvcConfigurer` | В prod |
| Аудит действий пользователя | `HandlerInterceptor` | Если нужно знать, какой endpoint вызван |
| Метрики latency per endpoint | `HandlerInterceptor` + Micrometer | Есть частично через Actuator |

---

## Вывод

В твоём проекте middleware уже есть и правильно организован:
- **`MdcFilter`** — Filter с `HIGHEST_PRECEDENCE` — трассировка запросов.
- **`CorsConfig`** — конфигурация CORS через `WebMvcConfigurer` — обработка cross-origin.

Они не конфликтуют, и порядок их выполнения корректный: MDC раньше CORS — это значит, что даже preflight `OPTIONS` запросы будут залогированы с `requestId` в контексте.
