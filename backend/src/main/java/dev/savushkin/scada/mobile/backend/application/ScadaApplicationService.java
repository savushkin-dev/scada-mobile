package dev.savushkin.scada.mobile.backend.application;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import dev.savushkin.scada.mobile.backend.store.PendingCommandsBuffer;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Application Service for SCADA commands orchestration.
 * <p>
 * This service coordinates between domain models, stores, and external layers.
 * It implements use cases for:
 * <ul>
 *   <li>Querying current device state</li>
 *   <li>Submitting write commands</li>
 * </ul>
 * <p>
 * This layer is responsible for:
 * <ul>
 *   <li>Orchestrating domain operations</li>
 *   <li>Managing infrastructure dependencies (stores, buffers)</li>
 *   <li>Coordinating cross-cutting concerns</li>
 * </ul>
 * <p>
 * This layer does NOT:
 * <ul>
 *   <li>Contain business logic (that's in domain services)</li>
 *   <li>Know about DTOs (that's in API/PrintSrv layers)</li>
 *   <li>Handle protocol details (that's in integration layers)</li>
 * </ul>
 */
@Service
public class ScadaApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ScadaApplicationService.class);

    private final PrintSrvSnapshotStore snapshotStore;
    private final PendingCommandsBuffer commandBuffer;

    /**
     * Constructor with dependency injection.
     *
     * @param snapshotStore store for device snapshots
     * @param commandBuffer buffer for pending write commands
     */
    public ScadaApplicationService(
            PrintSrvSnapshotStore snapshotStore,
            PendingCommandsBuffer commandBuffer
    ) {
        this.snapshotStore = snapshotStore;
        this.commandBuffer = commandBuffer;
        log.info("ScadaApplicationService initialized");
    }

    /**
     * Gets the current device state snapshot.
     * <p>
     * The snapshot is automatically updated by the polling scheduler.
     * Changes made via {@link #submitWriteCommand(int, int)} will be
     * visible after the next scan cycle (≤ 5 seconds).
     *
     * @return current device snapshot
     * @throws IllegalStateException if no snapshot is available yet
     */
    public DeviceSnapshot getCurrentState() {
        log.debug("Reading current device state from store");
        DeviceSnapshot snapshot = snapshotStore.getSnapshot();

        if (snapshot == null) {
            log.warn("Snapshot not available - store is empty");
            throw new IllegalStateException("Device snapshot not available yet. Please wait for the first scan cycle.");
        }

        log.debug("Device state retrieved successfully with {} units", snapshot.getUnitCount());
        return snapshot;
    }

    /**
     * Submits a write command to be executed in the next scan cycle.
     * <p>
     * This method returns immediately (< 50ms), without waiting for
     * the command to be written to the SCADA system.
     * <p>
     * The command will be executed in the next scan cycle (≤ 5 seconds).
     * Clients can verify the result via {@link #getCurrentState()} after
     * the next scan cycle.
     * <p>
     * Architectural guarantees:
     * <ul>
     *   <li><b>Fast Response</b>: returns < 50ms</li>
     *   <li><b>Eventual Consistency</b>: changes visible in ≤ 5 seconds</li>
     *   <li><b>Last-Write-Wins</b>: if multiple commands are sent for the same unit,
     *       only the last one will be executed</li>
     * </ul>
     *
     * @param unitNumber unit number (1-based)
     * @param value      command value to write
     * @throws dev.savushkin.scada.mobile.backend.exception.BufferOverflowException if buffer is full
     */
    public void submitWriteCommand(int unitNumber, int value) {
        log.info("Submitting write command: unit={}, value={}", unitNumber, value);

        // Create domain model command
        WriteCommand command = new WriteCommand(
                unitNumber,
                Map.of("command", value)
        );

        // Add to buffer (will be processed in next scan cycle)
        commandBuffer.add(command);
        log.debug("Command added to buffer successfully (buffer size={})", commandBuffer.size());

        log.info("Write command accepted: unit={}, value={} (will be executed in next scan cycle)",
                unitNumber, value);
    }

    /**
     * Checks if the system is ready to serve requests.
     * <p>
     * The system is ready when at least one snapshot has been received
     * from the polling/scan cycle.
     *
     * @return true if system is ready
     */
    public boolean isReady() {
        return snapshotStore.getSnapshot() != null;
    }

    /**
     * Checks if the system is alive (health check).
     *
     * @return true if system is alive
     */
    public boolean isAlive() {
        return true; // Application service is always alive if it can respond
    }
}
