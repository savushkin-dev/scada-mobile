import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { PillButton } from './PillButton';
import { IconCopy, IconCheck } from './icons';

interface GeneratedCredentialsDialogProps {
  isOpen: boolean;
  fullName: string;
  code: string;
  password: string;
  onClose: () => void;
}

export function GeneratedCredentialsDialog({
  isOpen,
  fullName,
  code,
  password,
  onClose,
}: GeneratedCredentialsDialogProps) {
  const [countdown, setCountdown] = useState(5);
  const [copiedCode, setCopiedCode] = useState(false);
  const [copiedPassword, setCopiedPassword] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setCountdown(5);
      setCopiedCode(false);
      setCopiedPassword(false);
      return;
    }

    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [isOpen, countdown]);

  if (!isOpen) return null;

  const handleCopy = async (text: string, type: 'code' | 'password') => {
    try {
      await navigator.clipboard.writeText(text);
      if (type === 'code') {
        setCopiedCode(true);
        setTimeout(() => setCopiedCode(false), 1500);
      } else {
        setCopiedPassword(true);
        setTimeout(() => setCopiedPassword(false), 1500);
      }
    } catch {
      // игнорируем ошибки копирования
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 px-4 py-6 backdrop-blur-[2px]">
      <div
        role="alertdialog"
        aria-modal="true"
        className="w-full max-w-[420px] rounded-[26px] bg-white p-6 shadow-[0_30px_80px_rgba(17,24,39,0.25)]"
      >
        <div className="mb-4 rounded-2xl border border-[#f0b4b8] bg-[#fff0f1] px-4 py-3">
          <p className="text-sm font-bold text-[#9f3138]">ВНИМАНИЕ</p>
          <p className="mt-1 text-xs font-medium text-[#9f3138]">
            Этот пароль вы увидите только один раз и только сейчас. Убедитесь, что вы сохранили
            пароль!
          </p>
        </div>

        <div className="mb-5 space-y-3">
          <div>
            <p className="text-xs font-semibold text-[#74777f]">ФИО</p>
            <p className="text-base font-bold text-[#1a1c1e]">{fullName}</p>
          </div>

          <CredentialRow
            label="Код сотрудника (логин)"
            value={code}
            copied={copiedCode}
            onCopy={() => handleCopy(code, 'code')}
          />

          <CredentialRow
            label="Временный пароль"
            value={password}
            copied={copiedPassword}
            onCopy={() => handleCopy(password, 'password')}
          />
        </div>

        <PillButton onClick={onClose} disabled={countdown > 0} className="w-full">
          {countdown > 0 ? `Сохранил (${countdown})` : 'Сохранил'}
        </PillButton>
      </div>
    </div>,
    document.body
  );
}

function CredentialRow({
  label,
  value,
  copied,
  onCopy,
}: {
  label: string;
  value: string;
  copied: boolean;
  onCopy: () => void;
}) {
  return (
    <div className="rounded-2xl border border-[#e8eaed] bg-[#f8f9fa] p-3">
      <p className="text-xs font-semibold text-[#74777f]">{label}</p>
      <div className="mt-1 flex items-center justify-between gap-3">
        <p className="break-all text-lg font-bold tracking-wide text-[#1a1c1e]">{value}</p>
        <button
          type="button"
          onClick={onCopy}
          className="flex shrink-0 items-center gap-1 rounded-full px-2 py-1 text-xs font-medium text-[#4285f4] transition hover:bg-[#e8f0ff]"
          aria-label={`Копировать ${label}`}
        >
          {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
          {copied ? 'Скопировано' : 'Копировать'}
        </button>
      </div>
    </div>
  );
}
