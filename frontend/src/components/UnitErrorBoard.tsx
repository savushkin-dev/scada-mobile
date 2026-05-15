import type { ErrorGroup } from '../constants/statusUtils';
import { useAccessControl } from '../context/AccessControlContext';

interface Props {
  groups: ErrorGroup[];
}

/**
 * Информационное табло ошибок — единый компонент для карточек аппарата и цеха.
 *
 * - Карточка **аппарата**: одна группа без `unitName`; рендерятся только устройство + описание.
 * - Карточка **цеха**: N групп с `unitName`; каждая группа разделена горизонтальной линией,
 *   ошибки внутри группы выводятся последовательно с небольшим отступом.
 *
 * Компонент не знает о контексте — он просто рендерит то, что передано.
 */
export function UnitErrorBoard({ groups }: Props) {
  const { isAssignedUnit } = useAccessControl();
  if (groups.length === 0) return null;

  return (
    <div className="mt-2 pt-2 border-t border-red-100">
      {groups.map((group, gi) => (
        <div key={gi}>
          {gi > 0 && <hr className="border-red-100 my-1.5" />}
          {group.unitName && (
            <div className="mb-1 flex items-center justify-between gap-2">
              <p className="text-[0.8rem] font-bold text-red-700 leading-tight">{group.unitName}</p>
              {group.unitId && isAssignedUnit(group.unitId) ? (
                <span className="text-[0.8rem] font-bold text-red-700 leading-tight">
                  Ваш автомат
                </span>
              ) : null}
            </div>
          )}
          <div className="space-y-1.5">
            {group.entries.map((entry, ei) => (
              <div key={ei}>
                <span className="block text-[0.72rem] font-semibold text-gray-500 uppercase tracking-wide leading-none">
                  {entry.device}
                </span>
                <span className="block text-[0.82rem] text-gray-900 leading-snug">
                  {entry.message}
                </span>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
