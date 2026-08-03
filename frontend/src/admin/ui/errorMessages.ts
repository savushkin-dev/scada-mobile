/**
 * Общие хелперы для человекочитаемых сообщений об ошибках админки.
 */

/** Связанная запись, которую backend возвращает при FK-ошибке удаления (HTTP 409). */
export interface DeleteReference {
  type: string;
  typeLabel: string;
  id: number | string;
  name: string;
}

export function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'string') return error || fallback;
  if (error instanceof Error) return error.message || fallback;
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    return typeof message === 'string' && message ? message : fallback;
  }
  return fallback;
}

/**
 * Сообщение об ошибке удаления. Если backend вернул `body.references`
 * (FK-ограничение, HTTP 409), дополняет текст списком связанных записей,
 * сгруппированным по типам:
 * «…Связанные записи — Автоматы: «Розлив №2»; Сотрудники: «Иванов И.И.».»
 */
export function formatDeleteError(error: unknown, fallback = 'Ошибка удаления'): string {
  const base = getErrorMessage(error, fallback);
  const references = getDeleteReferences(error);
  if (references.length === 0) return base;

  const groups = new Map<string, string[]>();
  for (const ref of references) {
    const label = ref.typeLabel || ref.type;
    const name = ref.name?.trim() ? `«${ref.name}»` : `#${ref.id}`;
    const names = groups.get(label) ?? [];
    names.push(name);
    groups.set(label, names);
  }

  const list = [...groups.entries()]
    .map(([label, names]) => `${label}: ${names.join(', ')}`)
    .join('; ');
  const baseWithDot = base.replace(/[\s.]+$/, '');
  return `${baseWithDot}. Связанные записи — ${list}.`;
}

function getDeleteReferences(error: unknown): DeleteReference[] {
  if (!error || typeof error !== 'object' || !('body' in error)) return [];
  const body = (error as { body?: unknown }).body;
  if (!body || typeof body !== 'object' || !('references' in body)) return [];
  const references = (body as { references?: unknown }).references;
  if (!Array.isArray(references)) return [];
  return references.filter(
    (ref): ref is DeleteReference =>
      ref != null &&
      typeof ref === 'object' &&
      typeof (ref as DeleteReference).type === 'string' &&
      typeof (ref as DeleteReference).typeLabel === 'string'
  );
}
