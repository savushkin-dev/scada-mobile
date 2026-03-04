/**
 * SkeletonBlock — примитив для построения skeleton-плейсхолдеров.
 *
 * Сам по себе не несёт смысла; используется как строительный блок
 * в конкретных skeleton-компонентах (WorkshopCardSkeleton и т.д.).
 *
 * Цвета намеренно нейтральные: чуть темнее фона карточки (#ffffff),
 * shimmer плавно идёт от #eaebec → #f4f5f6 → #eaebec.
 * Никакого цвета — только форма и движение.
 */

interface SkeletonBlockProps {
  width?: string;
  height?: string;
  borderRadius?: string;
  className?: string;
  style?: React.CSSProperties;
}

import type React from 'react';

export function SkeletonBlock({
  width = '100%',
  height = '16px',
  borderRadius = '6px',
  className,
  style,
}: SkeletonBlockProps) {
  return (
    <span
      aria-hidden="true"
      className={className}
      style={{
        display: 'block',
        width,
        height,
        borderRadius,
        ...style,
        background: 'linear-gradient(90deg, #eaebec 25%, #f4f5f6 50%, #eaebec 75%)',
        backgroundSize: '200% 100%',
        animation: 'skeleton-shimmer 1.6s ease-in-out infinite',
        flexShrink: 0,
      }}
    />
  );
}
