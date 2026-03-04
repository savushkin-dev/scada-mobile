import { useEffect } from 'react';
import { fetchUnits } from '../api/workshops';
import { UnitCard } from '../components/UnitCard';
import { useAppContext } from '../context/AppContext';

export function WorkshopPage() {
  const { state, setUnits, navigate, openDetails } = useAppContext();
  const workshopId = state.currentWorkshopId ?? '';
  const workshopName = state.currentWorkshopName ?? 'Цех';
  const units = state.unitsByWorkshop[workshopId] ?? [];

  useEffect(() => {
    if (!workshopId) return;
    fetchUnits(workshopId).then((u) => setUnits(workshopId, u));
  }, [workshopId, setUnits]);

  return (
    <section
      style={{
        flex: 1,
        overflowY: 'auto',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        animation: 'fadeIn 0.3s ease',
      }}
    >
      <header className="p-6 flex items-center gap-3 mt-2 flex-shrink-0">
        <button
          onClick={() => navigate('dashboard')}
          style={{
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            border: 'none',
            background: '#F0F7FF',
            cursor: 'pointer',
            fontSize: '1.1rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
          aria-label="Назад"
        >
          ←
        </button>
        <div>
          <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-0.5">
            Цех
          </p>
          <h1 className="text-xl font-bold text-[#1A1C1E] leading-tight">{workshopName}</h1>
        </div>
      </header>

      <main className="px-4 space-y-4 pb-10">
        {!units.length ? (
          <p className="text-center text-[#74777F] py-5 text-[0.88rem]">Загрузка оборудования...</p>
        ) : (
          units.map((u) => (
            <UnitCard
              key={u.id}
              unit={u}
              alerts={state.alerts}
              onClick={() => openDetails(workshopId, u.id)}
            />
          ))
        )}
      </main>
    </section>
  );
}
