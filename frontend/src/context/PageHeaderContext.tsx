import { createContext, useContext, useLayoutEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

/**
 * Конфигурация шапки страницы.
 *
 * Каждая страница декларативно задаёт эти поля через хук {@link usePageHeader},
 * а единственный экземпляр `<PageHeader />` в {@link RootLayout} отображает их.
 */
export interface PageHeaderConfig {
  title: string;
  subtitle?: string;
  variant?: 'default' | 'compact';
}

interface PageHeaderContextValue {
  config: PageHeaderConfig;
  setConfig: (config: PageHeaderConfig) => void;
}

const defaultConfig: PageHeaderConfig = { title: '' };

const PageHeaderContext = createContext<PageHeaderContextValue | null>(null);

export function PageHeaderProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<PageHeaderConfig>(defaultConfig);

  const value = useMemo(() => ({ config, setConfig }), [config]);

  return <PageHeaderContext.Provider value={value}>{children}</PageHeaderContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function usePageHeaderContext(): PageHeaderContextValue {
  const ctx = useContext(PageHeaderContext);
  if (!ctx) throw new Error('usePageHeaderContext must be used within PageHeaderProvider');
  return ctx;
}

/**
 * Хук для декларативного управления шапкой из любой страницы.
 *
 * Обновляет заголовок, подзаголовок и вариант единственного
 * экземпляра `<PageHeader />` в RootLayout.
 *
 * Использует `useLayoutEffect`, чтобы шапка обновлялась синхронно
 * до отрисовки кадра — без визуального мерцания при смене страницы.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function usePageHeader(
  title: string,
  subtitle?: string,
  variant?: 'default' | 'compact'
): void {
  const { setConfig } = usePageHeaderContext();

  useLayoutEffect(() => {
    setConfig({ title, subtitle, variant });
  }, [title, subtitle, variant, setConfig]);
}
