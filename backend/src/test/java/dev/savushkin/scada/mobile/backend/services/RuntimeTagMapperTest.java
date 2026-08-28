package dev.savushkin.scada.mobile.backend.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTagMapperTest {

    @Test
    void prefersScadaCountersAndFallsBackIndependently() {
        RuntimeTagMapper.CounterResolution resolved = RuntimeTagMapper.resolveCounters(
                Map.of("Total", "90", "Failed", "7"),
                Map.of("Dev041CounterGeneral", "0", "Dev041CounterMissing", ""),
                "Dev041");

        assertThat(resolved.read()).isEqualTo("0");
        assertThat(resolved.unread()).isEqualTo("7");
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