import { useEffect, useRef, useState } from 'react';
import { API_BASE } from '../config';

interface Props {
  visible: boolean;
  unitId: string | null;
  scrollContainer: HTMLElement | null;
}

export function Fab({ visible, unitId, scrollContainer }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const lastScrollY = useRef(0);

  useEffect(() => {
    if (!scrollContainer || !visible) return;
    const el = scrollContainer;

    function handleScroll() {
      const currentY = el.scrollTop;
      const delta = currentY - lastScrollY.current;
      if (delta > 4) setCollapsed(true);
      else if (delta < -4) setCollapsed(false);
      lastScrollY.current = currentY;
    }

    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [scrollContainer, visible]);

  useEffect(() => {
    if (!visible) {
      setCollapsed(false);
      lastScrollY.current = 0;
    }
  }, [visible]);

  async function handleClick() {
    if (!unitId || sending) return;
    setSending(true);
    try {
      const resp = await fetch(`${API_BASE}/api/line/${unitId}/last-batch`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    } catch (e) {
      console.warn('[FAB] last-batch fallback:', (e as Error).message);
    }
    setSent(true);
    setSending(false);
    setTimeout(() => setSent(false), 2000);
  }

  if (!visible) return null;

  return (
    <button
      aria-label="Сообщить: партия последняя"
      disabled={sending}
      onClick={handleClick}
      style={{
        position: 'fixed',
        bottom: `calc(64px + env(safe-area-inset-bottom) + 16px)`,
        right: '16px',
        width: collapsed ? '52px' : 'calc(50% - 8px)',
        maxWidth: collapsed ? '52px' : '210px',
        minWidth: '52px',
        height: '52px',
        padding: collapsed ? '0' : '0 18px',
        borderRadius: collapsed ? '50%' : '26px',
        justifyContent: collapsed ? 'center' : 'flex-start',
        background: sent ? '#34A853' : '#F97316',
        boxShadow: sent
          ? '0 4px 20px rgba(52,168,83,0.4)'
          : collapsed
            ? '0 4px 14px rgba(249,115,22,0.5)'
            : '0 4px 20px rgba(249,115,22,0.4)',
        zIndex: 9,
        display: 'flex',
        alignItems: 'center',
        overflow: 'hidden',
        whiteSpace: 'nowrap',
        border: 'none',
        cursor: 'pointer',
        fontFamily: 'Inter, sans-serif',
        fontSize: '0.85rem',
        fontWeight: 700,
        letterSpacing: '0.01em',
        color: 'white',
        transition:
          'width 0.3s ease, max-width 0.3s ease, padding 0.3s ease, border-radius 0.3s ease, opacity 0.3s ease, transform 0.3s ease, box-shadow 0.3s ease',
      }}
    >
      <span style={{ fontSize: '1.15rem', flexShrink: 0, lineHeight: 1 }}>
        {sent ? '✅' : '🔔'}
      </span>
      {!collapsed && (
        <span
          style={{
            overflow: 'hidden',
            maxWidth: '160px',
            opacity: 1,
            marginLeft: '8px',
          }}
        >
          {sent ? 'Отправлено!' : 'Последняя партия'}
        </span>
      )}
    </button>
  );
}
