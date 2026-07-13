type StatusVariant = 'active' | 'inactive' | 'error' | 'warning';

interface StatusPillProps {
  children: string;
  variant: StatusVariant;
}

const styles: Record<StatusVariant, { dot: string; bg: string; text: string }> = {
  active: {
    dot: 'bg-[#34a853]',
    bg: 'bg-[#e6f4ea]',
    text: 'text-[#1b6b2f]',
  },
  inactive: {
    dot: 'bg-[#74777f]',
    bg: 'bg-[#edeef0]',
    text: 'text-[#5f6368]',
  },
  error: {
    dot: 'bg-[#ea4335]',
    bg: 'bg-[#fceae9]',
    text: 'text-[#b71c1c]',
  },
  warning: {
    dot: 'bg-[#f59e0b]',
    bg: 'bg-[#fffbeb]',
    text: 'text-[#b45309]',
  },
};

export function StatusPill({ children, variant }: StatusPillProps) {
  const s = styles[variant];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-[20px] px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ${s.bg} ${s.text}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${s.dot}`} />
      {children}
    </span>
  );
}
