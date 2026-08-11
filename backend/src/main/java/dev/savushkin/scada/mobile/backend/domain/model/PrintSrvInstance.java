package dev.savushkin.scada.mobile.backend.domain.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

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
 * @param deviceDisplayNames    отображаемые имена устройств: код → device_catalog.name
 * @param typeDisplayNames      отображаемые имена типов: код типа → device_types.name
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
        @NonNull List<String> checkerCams,
        @NonNull Map<String, String> deviceDisplayNames,
        @NonNull Map<String, String> typeDisplayNames
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
