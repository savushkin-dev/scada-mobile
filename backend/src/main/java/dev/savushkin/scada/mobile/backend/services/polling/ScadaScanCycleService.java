package dev.savushkin.scada.mobile.backend.services.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Плейсхолдер сервиса скан-цикла.
 * <p>
 * Здесь позже появится:
 * <ul>
 *   <li>шаги scan-cycle (poll -> normalize -> store)</li>
 *   <li>метрики/трейсинг</li>
 *   <li>policy-решения (например, что считать «валидным» snapshot)</li>
 * </ul>
 */
@Service
public class ScadaScanCycleService {

    private static final Logger log = LoggerFactory.getLogger(ScadaScanCycleService.class);

    public ScadaScanCycleService() {
        log.info("ScadaScanCycleService placeholder initialized");
    }
}

