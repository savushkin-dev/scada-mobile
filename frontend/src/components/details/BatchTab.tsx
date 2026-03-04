import { useState } from 'react';
import type { LineStatusPayload } from '../../types';

interface Props {
  data: LineStatusPayload | null;
}

function val(v: string | number | undefined | null): string {
  if (v === null || v === undefined) return '-';
  return String(v);
}

export function BatchTab({ data }: Props) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="card p-5 card-static mb-4">
      <div className="card-title">📦 Текущая партия</div>

      <div className="kv-row">
        <div className="kv-key">Описание</div>
        <div className="kv-val">{val(data?.description)}</div>
      </div>
      <div className="kv-row">
        <div className="kv-key">EAN</div>
        <div className="kv-val">{val(data?.ean13)}</div>
      </div>
      <div className="kv-row">
        <div className="kv-key">Номер партии</div>
        <div className="kv-val">{val(data?.batchNumber)}</div>
      </div>
      <div className="kv-row">
        <div className="kv-key">Дата выработки</div>
        <div className="kv-val">{val(data?.dateProduced)}</div>
      </div>
      <div className="kv-row">
        <div className="kv-key">Дата годности</div>
        <div className="kv-val">{val(data?.dateExpiration)}</div>
      </div>

      <button className="accordion-btn" onClick={() => setExpanded((e) => !e)}>
        {expanded ? 'Скрыть дополнительные свойства ▴' : 'Показать все свойства ▾'}
      </button>

      {expanded && (
        <div style={{ paddingTop: '12px', animation: 'fadeIn 0.2s ease' }}>
          <div className="kv-row">
            <div className="kv-key">Краткий код</div>
            <div className="kv-val">{val(data?.shortCode)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">КМС</div>
            <div className="kv-val">{val(data?.kms)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Дата фасовки</div>
            <div className="kv-val">{val(data?.datePacking)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Начальный счётчик</div>
            <div className="kv-val">{val(data?.initialCounter)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Площадка</div>
            <div className="kv-val">{val(data?.site)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">ITF</div>
            <div className="kv-val">{val(data?.itf)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Ёмкость</div>
            <div className="kv-val">{val(data?.capacity)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Кол-во коробок</div>
            <div className="kv-val">{val(data?.boxCount)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Кол-во упаковок</div>
            <div className="kv-val">{val(data?.packageCount)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Заморозка</div>
            <div className="kv-val">{data?.freeze === 1 ? 'Да' : 'Нет'}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Регион</div>
            <div className="kv-val">{val(data?.region)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Дизайн</div>
            <div className="kv-val">{val(data?.design)}</div>
          </div>
          <div className="kv-row">
            <div className="kv-key">Печать DM</div>
            <div className="kv-val">{data?.printDM === 1 ? 'Да' : 'Нет'}</div>
          </div>
        </div>
      )}
    </div>
  );
}
