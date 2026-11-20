package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import project.smartfinance.application.card.CardStatementProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, CardCycleBoundaryIntegrationTests.TestClock.class})
class CardCycleBoundaryIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    CardStatementProcessor processor;

    @Test
    void closesAtBusinessMidnightAndAcceptsClosingDayInNextStatement() throws Exception {
        clock.realTime();
        Cookie csrf = csrf();
        Cookie owner = login(csrf, register(csrf));
        clock.set("2026-11-04T15:00:00Z");
        int card = create(csrf, owner, "/api/cards", """
                {"name":"Cartão do dia 5","institutionName":"Banco Teste",
                 "closingDay":5,"dueDay":14}
                """);
        int category = create(csrf, owner, "/api/categories",
                "{\"name\":\"Mercado\",\"kind\":\"DESPESA\"}");
        int wallet = create(csrf, owner, "/api/wallets", """
                {"name":"Conta teste","initialBalance":100,
                 "initialBalanceDate":"2020-01-01"}
                """);

        int previousPurchase = id(mvc.perform(purchase(csrf, owner, card, category,
                        "2026-11-04", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installments[0].statementReferenceMonth").value("2026-11"))
                .andReturn());
        mvc.perform(purchase(csrf, owner, card, category, "2026-11-05", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchasedOn").value("2026-11-05"))
                .andExpect(jsonPath("$.installments[0].statementReferenceMonth").value("2026-12"))
                .andExpect(jsonPath("$.installments[1].statementReferenceMonth").value("2027-01"));
        mvc.perform(get("/api/card-statements").param("month", "2026-11").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ABERTA"))
                .andExpect(jsonPath("$[0].totalAmount").value(10));
        mvc.perform(get("/api/card-statements").param("month", "2026-12").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PROJETADA"));

        // The scheduler is not required for the boundary: an API request regularizes it.
        clock.set("2026-11-05T02:59:59Z"); // 04/11 23:59:59 in America/Sao_Paulo
        mvc.perform(get("/api/card-statements").param("month", "2026-11").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ABERTA"));
        clock.set("2026-11-05T03:00:00Z"); // 00:00 in America/Sao_Paulo
        mvc.perform(get("/api/card-statements").param("month", "2026-11").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FECHADA"))
                .andExpect(jsonPath("$[0].closedAmount").value(10));
        mvc.perform(get("/api/card-statements").param("month", "2026-12").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ABERTA"));
        mvc.perform(purchase(csrf, owner, card, category, "2026-11-04", 1))
                .andExpect(status().isConflict());
        mvc.perform(purchase(csrf, owner, card, category, "2026-11-05", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchasedOn").value("2026-11-05"))
                .andExpect(jsonPath("$.installments[0].statementReferenceMonth").value("2026-12"));
        mvc.perform(get("/api/monthly-summary").param("month", "2026-11").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(10));
        mvc.perform(get("/api/monthly-summary").param("month", "2026-12").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(15));

        mvc.perform(post("/api/card-purchases/{id}/reversal", previousPurchase)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].status").value("ESTORNADA"));
        mvc.perform(get("/api/card-statements").param("month", "2026-11").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appliedCredits").value(0))
                .andExpect(jsonPath("$[0].closedAmount").value(10));
        mvc.perform(get("/api/card-statements").param("month", "2026-12").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectedCredits").value(10));
        clock.set("2026-11-05T04:00:00Z");
        mvc.perform(post("/api/card-statements/{id}/payments",
                        jdbc.queryForObject("SELECT id FROM fatura_cartao WHERE cartao_id = ? AND mes_referencia = ?",
                                Integer.class, card, java.sql.Date.valueOf("2026-11-01")))
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"paidOn":"2026-11-05T00:30:00-03:00"}
                                """.formatted(wallet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10));
        assertThat(jdbc.queryForObject("""
                SELECT comprada_em AT TIME ZONE 'America/Sao_Paulo'
                FROM compra_cartao WHERE id = ?
                """, java.time.LocalDateTime.class, previousPurchase).toLocalDate())
                .isEqualTo(java.time.LocalDate.of(2026, 11, 4));
    }

    @Test
    void delayedJobDoesNotApplyCreditsCreatedOnTheClosingDayToOldStatement() throws Exception {
        clock.realTime();
        Cookie csrf = csrf();
        Cookie owner = login(csrf, register(csrf));
        clock.set("2026-11-04T15:00:00Z");
        int card = create(csrf, owner, "/api/cards", """
                {"name":"Cartão do crédito","institutionName":"Banco Teste",
                 "closingDay":5,"dueDay":14}
                """);
        int category = create(csrf, owner, "/api/categories",
                "{\"name\":\"Mercado\",\"kind\":\"DESPESA\"}");
        int purchase = id(mvc.perform(purchase(csrf, owner, card, category,
                        "2026-11-04", 1))
                .andExpect(status().isCreated()).andReturn());
        jdbc.update("""
                INSERT INTO credito_estorno
                    (owner_user_id, compra_cartao_id, valor_original, criado_em)
                SELECT owner_user_id, id, 7, ? FROM compra_cartao WHERE id = ?
                """, java.time.OffsetDateTime.parse("2026-11-05T00:00:00-03:00"), purchase);

        clock.set("2026-11-06T15:00:00Z");
        processor.processDue();
        processor.processDue();
        mvc.perform(get("/api/card-statements").param("month", "2026-11").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].closedAmount").value(10))
                .andExpect(jsonPath("$[0].appliedCredits").value(0));
    }

    private MockHttpServletRequestBuilder purchase(Cookie csrf, Cookie owner, int card,
            int category, String purchasedOn, int installments) {
        return post("/api/card-purchases").cookie(csrf, owner)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"cardId":%d,"categoryId":%d,"description":"Compra","amount":10,
                         "purchasedOn":"%s","installments":%d,"necessity":"NECESSARIA"}
                        """.formatted(card, category, purchasedOn, installments));
    }

    private int create(Cookie csrf, Cookie owner, String path, String body) throws Exception {
        return id(mvc.perform(post(path).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn());
    }

    private Cookie csrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private String register(Cookie csrf) throws Exception {
        String email = "cycle-" + java.util.UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/auth/register").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private Cookie login(Cookie csrf, String email) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isNoContent()).andReturn();
        String header = response.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("SF_ACCESS="))
                .findFirst().orElseThrow();
        return new Cookie("SF_ACCESS", header.substring("SF_ACCESS=".length(),
                header.indexOf(';')));
    }

    private int id(MvcResult response) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id")).intValue();
    }

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock fixedBusinessClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final ZoneId zone = ZoneId.of("America/Sao_Paulo");
        private volatile Instant current = Instant.parse("2026-11-04T15:00:00Z");

        void set(String instant) {
            current = Instant.parse(instant);
        }

        void realTime() {
            current = Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
