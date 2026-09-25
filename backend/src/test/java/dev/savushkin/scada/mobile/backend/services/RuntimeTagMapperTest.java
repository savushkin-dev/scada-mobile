package dev.savushkin.scada.mobile.backend.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTagMapperTest {

    @Test
    void prefersScadaCountersAndFallsBackIndependently() {
        RuntimeTagMapper.CounterResolution resolved = RuntimeTagMapper.resolveCounters(
                Map.of("Succeeded", "90", "Failed", "7"),
                Map.of("Dev041CounterGeneral", "0", "Dev041CounterMissing", ""),
                "Dev041");

        assertThat(resolved.read()).isEqualTo("0");
        assertThat(resolved.unread()).isEqualTo("7");
        assertThat(resolved.readSource()).isEqualTo("scada:Dev041CounterGeneral");
        assertThat(resolved.unreadSource()).isEqualTo("device:Failed");
    }

    @Test
    void fallsBackToSucceededNotTotal() {
        // CounterGeneral ≈ Succeeded, а не Total (Total = Succeeded + Failed
        // завышает «Считано») — PRINTSRV_UI_MAP.md §2.
        RuntimeTagMapper.CounterResolution resolved = RuntimeTagMapper.resolveCounters(
                Map.of("Succeeded", "90", "Total", "97", "Failed", "7"),
                Map.of(),
                "Dev041");

        assertThat(resolved.read()).isEqualTo("90");
        assertThat(resolved.unread()).isEqualTo("7");
        assertThat(resolved.readSource()).isEqualTo("device:Succeeded");
        assertThat(resolved.unreadSource()).isEqualTo("device:Failed");
    }

    @Test
    void fallsBackToSucceededWithoutScadaPrefix() {
        RuntimeTagMapper.CounterResolution resolved = RuntimeTagMapper.resolveCounters(
                Map.of("Succeeded", "120", "Total", "130", "Failed", "10"),
                Map.of("Dev041CounterGeneral", "999"),
                null);

        assertThat(resolved.read()).isEqualTo("120");
        assertThat(resolved.unread()).isEqualTo("10");
        assertThat(resolved.readSource()).isEqualTo("device:Succeeded");
    }

    @Test
    void supportsCmsPrefixAndConnectAliasWithoutSubstringMatching() {
        assertThat(RuntimeTagMapper.parseErrorKey("Dev041Fail").orElseThrow())
            .isEqualTo(new RuntimeTagMapper.ErrorTag("Dev041", "Fail"));
        assertThat(RuntimeTagMapper.parseErrorKey("CMSDev041Fail").orElseThrow())
            .isEqualTo(new RuntimeTagMapper.ErrorTag("Dev041", "Fail"));
        assertThat(RuntimeTagMapper.parseErrorKey("Dev042Connect").orElseThrow())
            .isEqualTo(new RuntimeTagMapper.ErrorTag("Dev042", "Connection"));
        assertThat(RuntimeTagMapper.parseErrorKey("SomeDev041Fail")).isEmpty();
    }

    @Test
    void recognizesConfirmedActiveValuesOnly() {
        assertThat(RuntimeTagMapper.isActiveFlag("1")).isTrue();
        assertThat(RuntimeTagMapper.isActiveFlag("true")).isTrue();
        assertThat(RuntimeTagMapper.isActiveFlag(" 0 ")).isFalse();
        assertThat(RuntimeTagMapper.isActiveFlag("false")).isFalse();
        assertThat(RuntimeTagMapper.isActiveFlag("unexpected")).isFalse();
    }
}
