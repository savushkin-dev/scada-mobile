import { IconRefresh, IconAlertCircle } from '../admin/ui/icons';
import { PillButton } from '../admin/ui/PillButton';

interface ServerUnavailablePageProps {
  onRetry?: () => void;
}

export function ServerUnavailablePage({ onRetry }: ServerUnavailablePageProps) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-[#f8f9fa] p-6 text-center">
      <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-[#ffebee] text-[#d32f2f]">
        <IconAlertCircle size={40} />
      </div>
      <h1 className="mb-2 text-2xl font-bold text-[#1a1c1e]">Сервер недоступен</h1>
      <p className="mb-8 max-w-md text-base text-[#74777f]">
        Не удалось подключиться к серверу. Проверьте подключение к сети или повторите попытку позже.
      </p>
      {onRetry && (
        <PillButton icon={<IconRefresh size={18} />} onClick={onRetry}>
          Повторить
        </PillButton>
      )}
    </div>
  );
}
