/**
 * Zod-схема для переменных окружения (import.meta.env / process.env).
 *
 * Валидируется один раз при старте приложения в config/runtime.ts.
 * Некорректные значения вызывают console.error (dev) — с fallback на значения по умолчанию.
 */
import { z } from 'zod';

export const EnvSchema = z.object({
  /** URL HTTP-API (без слеша в конце). Ожидается http:// или https://. */
  VITE_API_BASE: z
    .string()
    .regex(/^https?:\/\//, 'VITE_API_BASE должен начинаться с http:// или https://')
    .optional(),
  /** URL WebSocket. Ожидается ws:// или wss://. */
  VITE_WS_BASE: z
    .string()
    .regex(/^wss?:\/\//, 'VITE_WS_BASE должен начинаться с ws:// или wss://')
    .optional(),
  /**
   * Идентификатор текущего пользователя (строковый логин, например "ivanov").
   * Временное решение для идентификации без аутентификации.
   * Передаётся в REST-заголовке X-User-Id и WS query-параметре ?userId=.
   * В дальнейшем будет заменён на JWT.
   */
  VITE_USER_ID: z.string().min(1, 'VITE_USER_ID не может быть пустым').optional(),
});

export type Env = z.infer<typeof EnvSchema>;
