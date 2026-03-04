import { useEffect } from 'react';
import { fetchWorkshops } from '../api/workshops';
import { WorkshopCard } from '../components/WorkshopCard';
import { useAppContext } from '../context/AppContext';

export function DashboardPage() {
  const { state, setWorkshops, navigateToWorkshop } = useAppContext();

  useEffect(() => {
    fetchWorkshops().then(setWorkshops);
  }, [setWorkshops]);

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
      <header className="p-6 flex justify-between items-start mt-4 flex-shrink-0">
        <div>
          <p className="text-[10px] font-bold tracking-wider text-[#74777F] uppercase mb-1">
            Площадка г. Брест
          </p>
          <h1 className="text-2xl font-bold text-[#1A1C1E]">Савушкин продукт</h1>
        </div>
      </header>

      <main className="px-4 space-y-4 pb-10">
        {!state.workshops.length ? (
          <p className="text-center text-[#74777F] py-5 text-[0.88rem]">Загрузка цехов...</p>
        ) : (
          state.workshops.map((ws) => (
            <WorkshopCard
              key={ws.id}
              workshop={ws}
              alerts={state.alerts}
              onClick={() => navigateToWorkshop(ws.id, ws.name)}
            />
          ))
        )}
      </main>
    </section>
  );
}
