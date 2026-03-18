package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Типизированная конфигурация VAPID (Voluntary Application Server Identification, RFC 8292).
 * <p>
 * Значения задаются через переменные окружения в production:
 * <ul>
 *   <li>{@code VAPID_PUBLIC_KEY}  — Base64url-кодированный публичный ключ EC P-256</li>
 *   <li>{@code VAPID_PRIVATE_KEY} — Base64url-кодированный приватный ключ EC P-256 (секрет!)</li>
 *   <li>{@code VAPID_SUBJECT}     — Contact URI (mailto: или https://)</li>
 * </ul>
 * <p>
 * Если ключи не заданы (пустая строка) — Web Push отключён, уведомления не отправляются.
 * Это безопасное поведение по умолчанию (fail-safe).
 * <p>
 * Генерация ключей:
 * <pre>
 *   npx web-push generate-vapid-keys
 * </pre>
 */
@ConfigurationProperties(prefix = "vapid")
public class VapidProperties {

    /**
     * Публичный ключ VAPID (Base64url, ~88 символов).
     * Передаётся клиенту для {@code PushManager.subscribe({ applicationServerKey: ... })}.
     */
    private String publicKey = "";

    /**
     * Приватный ключ VAPID (Base64url). Хранить только в секретах, не коммитить в Git!
     */
    private String privateKey = "";

    /**
     * Subject — contact URI, отправляемый в Authorization push-сервису (RFC 8292 §2.1).
     * Формат: {@code mailto:your@email.com} или {@code https://your-domain.com}.
     */
    private String subject = "mailto:scada@example.com";

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Возвращает {@code true} если оба ключа VAPID заданы и не пусты.
     * Используется для условного включения Web Push.
     */
    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
