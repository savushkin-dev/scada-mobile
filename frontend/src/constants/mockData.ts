import type { Workshop, Unit, UnitWsMessage } from '../types';

export const MOCK_WORKSHOPS: Workshop[] = [
  { id: 'apparatniy', name: 'Аппаратный цех', totalUnits: 18, problemUnits: 0 },
  { id: 'cmp', name: 'Цех розлива (ЦМП)', totalUnits: 12, problemUnits: 1 },
];

export const MOCK_UNITS: Record<string, Unit[]> = {
  apparatniy: [
    {
      id: '101',
      workshopId: 'apparatniy',
      unit: 'Танк T-01 (Сырое молоко)',
      event: 'Заполнение 85%',
      timer: '00:00:00',
    },
    {
      id: '102',
      workshopId: 'apparatniy',
      unit: 'Сепаратор GEA Westfalia №2',
      event: 'Нормализация',
      timer: '00:00:00',
    },
    {
      id: '103',
      workshopId: 'apparatniy',
      unit: 'Пастеризатор Tetra Pak P-04',
      event: '72°C - Стабильно',
      timer: '00:00:00',
    },
  ],
  cmp: [
    {
      id: '201',
      workshopId: 'cmp',
      unit: 'Линия розлива ПЭТ №1',
      event: 'Активен розлив',
      timer: '00:00:00',
    },
    {
      id: '202',
      workshopId: 'cmp',
      unit: 'Автомат Tetra Top №5',
      event: 'Замятие ленты крышек',
      timer: '00:05:44',
    },
  ],
};

export const MOCK_UNIT_MESSAGES: UnitWsMessage[] = [
  {
    type: 'LINE_STATUS',
    payload: {
      lineName: 'Hassia №2',
      lineState: 1,
      shortCode: '2345',
      kms: '123456',
      description: 'Сырок гл. МАКОВКА 20% 40г',
      ean13: '4810268055485',
      batchNumber: '567',
      dateProduced: '21.07.2024 12:01:53',
      datePacking: '21.07.2024 12:01:31',
      dateExpiration: '21.08.2024 12:00:43',
      initialCounter: 0,
      site: 'Брест',
      itf: '24810268055489',
      capacity: 0,
      boxCount: 0,
      packageCount: 0,
      freeze: 0,
      region: 'BY',
      design: 0,
      printDM: 1,
    },
  },
  {
    type: 'DEVICES_STATUS',
    payload: {
      printer: { state: 1, error: 0, batch: '2345 | 567 | 21.07.2024' },
      cam41: { state: 1, error: 0, read: 123456, unread: 456789, batch: '1234 | 456 | 12.06.2025' },
      cam42: { state: 1, error: 0, read: 123000, unread: 450000 },
    },
  },
  {
    type: 'QUEUE',
    payload: {
      items: [
        { position: 1, shortCode: '2345', batch: '568', dateProduced: '22.07.2024' },
        { position: 2, shortCode: '2345', batch: '569', dateProduced: '23.07.2024' },
        { position: 3, shortCode: '2345', batch: '570', dateProduced: '24.07.2024' },
      ],
    },
  },
  {
    type: 'ERRORS',
    payload: {
      deviceErrors: [
        { objectName: 'CMSDev041', propertyDesc: 'Не совпадает идентификатор партии', value: 0 },
        { objectName: 'CMSDev041', propertyDesc: 'Нет связи с устройством', value: 1 },
      ],
      logs: [
        {
          time: '21.07.2024 10:00',
          ackTime: '10:05',
          group: 'Система',
          description: 'Запуск линии',
        },
        {
          time: '21.07.2024 10:15',
          ackTime: '-',
          group: 'Ошибка',
          description: 'Потеря связи с камерой 41',
        },
      ],
    },
  },
];
