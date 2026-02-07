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
}
