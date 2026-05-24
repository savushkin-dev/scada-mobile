package dev.savushkin.scada.mobile.backend.domain.model;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Доменная модель инстанса PrintSrv — агрегированное представление
 * данных из БД (units + unit_devices + device_types).
 *
 * @param instanceId    printsrv_instance_id (строковый ID аппарата)
 * @param displayName   отображаемое название (units.name)
 * @param workshopId    внутренний ID цеха (workshops.workshop_id)
 * @param host          TCP-хост PrintSrv
 * @param port          TCP-порт PrintSrv
 * @param deviceNames   полный список имён устройств (включая системные: Line, scada, BatchQueue)
 * @param printers      имена принтеров
 * @param aggregationCams       имена камер агрегации
 * @param aggregationBoxCams    имена камер агрегации коробов
 * @param checkerCams           имена камер проверки
 */
public record PrintSrvInstance(
        @NonNull String instanceId,
        @NonNull String displayName,
        long workshopId,
        @NonNull String host,
        int port,
        @NonNull List<String> deviceNames,
        @NonNull List<String> printers,
        @NonNull List<String> aggregationCams,
        @NonNull List<String> aggregationBoxCams,
        @NonNull List<String> checkerCams
) {
    /**
     * Возвращает имя системного устройства Line (захардкожено — одинаково для всех инстансов).
     */
    public @NonNull String lineDeviceName() {
        return "Line";
    }

    /**
     * Возвращает имя системного устройства scada.
     */
    public @NonNull String scadaDeviceName() {
        return "scada";
    }

    /**
     * Возвращает имя системного устройства BatchQueue.
     */
    public @NonNull String batchQueueDeviceName() {
        return "BatchQueue";
    }
}
