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

import { SKELETON_BLOCK_DEFAULTS } from '../../config';
import type React from 'react';

export function SkeletonBlock({
  width = SKELETON_BLOCK_DEFAULTS.width,
  height = SKELETON_BLOCK_DEFAULTS.height,
  borderRadius = SKELETON_BLOCK_DEFAULTS.borderRadius,
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
        background: SKELETON_BLOCK_DEFAULTS.background,
        backgroundSize: SKELETON_BLOCK_DEFAULTS.backgroundSize,
        animation: SKELETON_BLOCK_DEFAULTS.animation,
        flexShrink: 0,
      }}
    />
  );
}
