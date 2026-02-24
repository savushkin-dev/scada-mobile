package dev.savushkin.scada.mobile.backend.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для UnitProperties.
 * Проверяет builder, Optional-геттеры, equals/hashCode/toString.
 */
class UnitPropertiesTest {

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    @Test
    void builder_allFieldsSet_returnsCorrectValues() {
        UnitProperties props = UnitProperties.builder()
                .command(25)
                .message("Msg")
                .error("0")
                .errorMessage("")
                .cmdSuccess("1")
                .st("0")
                .batchId("B1")
                .curItem("item1")
                .batchIdCodesQueue("0")
                .setBatchId("0")
                .devChangeBatch("")
                .devsChangeBatchIdQueueControl("")
                .devType("10")
                .lineId("5")
                .onChangeBatchPrinters("Level1Printers")
                .level1Printers("P1,P2")
                .level2Printers("P3")
                .onChangeBatchCams("Level1Cams")
                .level1Cams("C1,C2")
                .level2Cams("C3")
                .signalCams("SC1")
                .lineDevices("D1,D2,D3")
                .enableErrors("0")
                .build();

        assertTrue(props.getCommand().isPresent());
        assertEquals(25, props.getCommand().get());
        assertEquals("Msg", props.getMessage().orElse(null));
        assertEquals("0", props.getError().orElse(null));
        assertEquals("", props.getErrorMessage().orElse(null));
        assertEquals("1", props.getCmdSuccess().orElse(null));
        assertEquals("0", props.getSt().orElse(null));
        assertEquals("B1", props.getBatchId().orElse(null));
        assertEquals("item1", props.getCurItem().orElse(null));
        assertEquals("0", props.getBatchIdCodesQueue().orElse(null));
        assertEquals("0", props.getSetBatchId().orElse(null));
        assertEquals("", props.getDevChangeBatch().orElse(null));
        assertEquals("", props.getDevsChangeBatchIdQueueControl().orElse(null));
        assertEquals("10", props.getDevType().orElse(null));
        assertEquals("5", props.getLineId().orElse(null));
        assertEquals("Level1Printers", props.getOnChangeBatchPrinters().orElse(null));
        assertEquals("P1,P2", props.getLevel1Printers().orElse(null));
        assertEquals("P3", props.getLevel2Printers().orElse(null));
        assertEquals("Level1Cams", props.getOnChangeBatchCams().orElse(null));
        assertEquals("C1,C2", props.getLevel1Cams().orElse(null));
        assertEquals("C3", props.getLevel2Cams().orElse(null));
        assertEquals("SC1", props.getSignalCams().orElse(null));
        assertEquals("D1,D2,D3", props.getLineDevices().orElse(null));
        assertEquals("0", props.getEnableErrors().orElse(null));
    }

    @Test
    void builder_emptyBuild_allFieldsEmpty() {
        UnitProperties props = UnitProperties.builder().build();

        assertTrue(props.getCommand().isEmpty());
        assertTrue(props.getMessage().isEmpty());
        assertTrue(props.getError().isEmpty());
        assertTrue(props.getLineId().isEmpty());
        assertTrue(props.getEnableErrors().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Optional геттеры (null → empty, value → present)
    // -------------------------------------------------------------------------

    @Test
    void getCommand_whenNull_returnsEmpty() {
        UnitProperties props = UnitProperties.builder().build();
        assertTrue(props.getCommand().isEmpty());
    }

    @Test
    void getCommand_whenSet_returnsPresent() {
        UnitProperties props = UnitProperties.builder().command(99).build();
        assertEquals(99, props.getCommand().get());
    }

    // -------------------------------------------------------------------------
    // equals / hashCode
    // -------------------------------------------------------------------------

    @Test
    void equals_sameFields_areEqual() {
        UnitProperties a = UnitProperties.builder().command(1).message("m").build();
        UnitProperties b = UnitProperties.builder().command(1).message("m").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentCommand_notEqual() {
        UnitProperties a = UnitProperties.builder().command(1).build();
        UnitProperties b = UnitProperties.builder().command(2).build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_differentType_notEqual() {
        UnitProperties a = UnitProperties.builder().command(1).build();
        assertNotEquals(a, "not a UnitProperties");
    }

    @Test
    void equals_null_returnsFalse() {
        UnitProperties a = UnitProperties.builder().command(1).build();
        assertNotEquals(null, a);
    }

    @Test
    void equals_sameInstance_returnsTrue() {
        UnitProperties a = UnitProperties.builder().command(1).build();
        assertEquals(a, a);
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    void toString_containsClassName() {
        UnitProperties props = UnitProperties.builder().command(77).build();
        assertTrue(props.toString().contains("UnitProperties"));
        assertTrue(props.toString().contains("77"));
    }
}
