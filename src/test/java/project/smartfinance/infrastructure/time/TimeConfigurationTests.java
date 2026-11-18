package project.smartfinance.infrastructure.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class TimeConfigurationTests {

    private final TimeConfiguration configuration = new TimeConfiguration();

    @Test
    void businessClockUsesSaoPaulo() {
        Clock clock = configuration.clock(TimeConfiguration.DEFAULT_TIME_ZONE);

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
        assertThat(LocalDate.now(clock))
                .isEqualTo(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    }

    @Test
    void lateUtcInstantFallsOnPreviousBusinessDayInSaoPaulo() {
        // 19/11 02:30 UTC is still 18/11 23:30 in Sao Paulo.
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T02:30:00Z"),
                ZoneId.of("America/Sao_Paulo"));

        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 11, 18));
    }
}
