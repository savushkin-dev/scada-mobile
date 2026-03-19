const APP_HEIGHT_CSS_VAR = '--app-height';
const VIEWPORT_BOTTOM_INSET_CSS_VAR = '--viewport-bottom-inset';

declare global {
  interface Window {
    __scadaViewportCssVarsBound?: boolean;
  }
}

function readViewportMetrics(): { appHeight: number; bottomInset: number } {
  const visualViewport = window.visualViewport;
  if (!visualViewport) {
    return { appHeight: window.innerHeight, bottomInset: 0 };
  }

  const appHeight = Math.round(visualViewport.height);
  const bottomInset = Math.max(
    0,
    Math.round(window.innerHeight - (visualViewport.height + visualViewport.offsetTop))
  );

  return { appHeight, bottomInset };
}

/**
 * Синхронизирует CSS-переменные viewport:
 *  - `--app-height`            — фактическая видимая высота страницы;
 *  - `--viewport-bottom-inset` — нижний системный inset (в т.ч. browser overlays).
 *
 * Используется для корректного позиционирования fixed/flex-элементов в Android-браузере,
 * где `100vh` может не совпадать с реальной видимой областью.
 */
export function bindViewportCssVars(): void {
  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  const apply = () => {
    const { appHeight, bottomInset } = readViewportMetrics();
    const root = document.documentElement;
    root.style.setProperty(APP_HEIGHT_CSS_VAR, `${appHeight}px`);
    root.style.setProperty(VIEWPORT_BOTTOM_INSET_CSS_VAR, `${bottomInset}px`);
  };

  apply();

  if (window.__scadaViewportCssVarsBound) return;
  window.__scadaViewportCssVarsBound = true;

  window.addEventListener('resize', apply, { passive: true });
  window.addEventListener('orientationchange', apply, { passive: true });
  window.visualViewport?.addEventListener('resize', apply, { passive: true });
  window.visualViewport?.addEventListener('scroll', apply, { passive: true });
}
