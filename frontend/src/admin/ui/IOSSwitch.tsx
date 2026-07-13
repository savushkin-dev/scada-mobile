import type { InputHTMLAttributes } from 'react';

interface IOSSwitchProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  scale?: 'default' | 'compact';
}

export function IOSSwitch({ scale = 'default', className = '', ...props }: IOSSwitchProps) {
  const width = scale === 'compact' ? 36 : 44;
  const height = scale === 'compact' ? 20 : 24;
  const knob = scale === 'compact' ? 16 : 20;
  const padding = scale === 'compact' ? 2 : 2;

  return (
    <label
      className={`relative inline-block cursor-pointer ${className}`}
      style={{ width, height }}
    >
      <input type="checkbox" className="peer sr-only" {...props} />
      <span
        className="absolute inset-0 rounded-full bg-[#e8eaed] transition-colors duration-200 peer-checked:bg-[#4285f4]"
        style={{ borderRadius: height / 2 }}
      />
      <span
        className="absolute top-[2px] left-[2px] rounded-full bg-white shadow-[0_1px_3px_rgba(0,0,0,0.15)] transition-transform duration-200 peer-checked:translate-x-full"
        style={{
          width: knob,
          height: knob,
          transform: 'translateX(0)',
          // Переопределяем translate-x-full точным значением
        }}
      />
      <style>{`
        .peer:checked + span + span {
          transform: translateX(${width - knob - padding * 2}px) !important;
        }
      `}</style>
    </label>
  );
}
