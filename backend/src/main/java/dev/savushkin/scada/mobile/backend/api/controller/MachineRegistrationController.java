package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.MachineRegisterRequestDTO;
import dev.savushkin.scada.mobile.backend.api.dto.MachineRegisterResponseDTO;
import dev.savushkin.scada.mobile.backend.services.MachineRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("${scada.api.base-path}")
public class MachineRegistrationController {
    private final MachineRegistrationService registrationService;

    public MachineRegistrationController(MachineRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/machine/register")
    public ResponseEntity<?> register(@Valid @RequestBody MachineRegisterRequestDTO request) {
        return registrationService.register(request.printSrvId())
                .<ResponseEntity<?>>map(machine -> ResponseEntity.ok(new MachineRegisterResponseDTO(
                        machine.token().token(),
                        machine.printSrvId(),
                        machine.unitId(),
                        machine.token().expiresAt().atOffset(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_INSTANT))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("machine_not_found")));
    }

    private record ErrorResponse(String error) {
    }
}
