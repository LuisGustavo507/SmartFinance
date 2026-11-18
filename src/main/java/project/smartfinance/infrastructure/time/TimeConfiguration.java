package project.smartfinance.infrastructure.time;

import java.time.Clock;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business dates (what counts as "today") follow Brazil's time zone, while
 * tokens and persisted timestamps stay absolute UTC instants.
 */
@Configuration
class TimeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TimeConfiguration.class);

    static final String DEFAULT_TIME_ZONE = "America/Sao_Paulo";

    @Bean
    Clock clock(@Value("${smartfinance.time-zone:" + DEFAULT_TIME_ZONE + "}") String timeZone) {
        ZoneId zone = ZoneId.of(timeZone);
        log.info("Business clock and scheduled jobs use zone {}", zone);
        return Clock.system(zone);
    }
}
