// Копирование текста в буфер обмена.
// navigator.clipboard доступен только в Secure Context (https/localhost),
// поэтому при работе по http:// (внутренняя сеть) используем fallback
// через временный textarea + document.execCommand('copy').
export async function copyToClipboard(text: string): Promise<boolean> {
  if (window.isSecureContext && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // Падение возможно при потере фокуса документа — пробуем fallback
    }
  }
  return copyViaExecCommand(text);
}

function copyViaExecCommand(text: string): boolean {
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', '');
  // Прячем за пределами viewport, чтобы не было скачка скролла
  textarea.style.position = 'fixed';
  textarea.style.top = '0';
  textarea.style.left = '-9999px';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  textarea.setSelectionRange(0, text.length); // для iOS
  try {
    return document.execCommand('copy');
  } catch {
    return false;
  } finally {
    document.body.removeChild(textarea);
  }
}
