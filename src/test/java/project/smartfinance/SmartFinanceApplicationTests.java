package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class SmartFinanceApplicationTests {

    @Autowired
    Clock clock;

    @Test
    void contextLoads() {
    }

    @Test
    void businessClockRunsInSaoPaulo() {
        assertThat(clock.getZone().getId()).isEqualTo("America/Sao_Paulo");
    }
}
