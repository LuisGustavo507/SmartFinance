package project.smartfinance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class SmartFinanceApplicationTests {

    @Test
    void contextLoads() {
    }

}
