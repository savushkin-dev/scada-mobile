package dev.savushkin.scada.mobile.backend.controllers;

import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
@RequestMapping("api/v1/commands")
public class CommandsController {

    private final static Logger log = LoggerFactory.getLogger(CommandsController.class);

    private final CommandsService commandsService;

    public CommandsController(CommandsService commandsService) {
        this.commandsService = commandsService;
    }

    @GetMapping("/queryAll")
    public ResponseEntity<QueryAllResponseDTO> queryAll() throws IOException, InterruptedException {
        return ResponseEntity.ok(commandsService.queryAll());
    }

    @GetMapping("/test-pool")
    public String testPool() throws Exception {
        long start = System.currentTimeMillis();

        // Создаем 10 параллельных запросов
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int taskNo = i;
            futures.add(executor.submit(() -> {
                try {
                    commandsService.queryAll();
                } catch (Exception e) {
                    log.error("test-pool task {} failed", taskNo, e);
                }
            }));
        }

        // Ждем завершения всех
        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        long time = System.currentTimeMillis() - start;

        return "10 requests completed in " + time + "ms";
    }
}
