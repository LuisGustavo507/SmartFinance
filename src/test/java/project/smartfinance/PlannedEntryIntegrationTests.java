package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import project.smartfinance.application.entry.Entries;
import project.smartfinance.application.entry.PlannedEntryProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class PlannedEntryIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlannedEntryProcessor processor;

    @Autowired
    Entries entries;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void planCancelAndCatchUpAreOwnerScopedAndIdempotent() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "plan-owner@example.test");
        register(csrf, "plan-other@example.test");
        Cookie owner = login(csrf, "plan-owner@example.test");
        Cookie other = login(csrf, "plan-other@example.test");
        int walletId = wallet(csrf, owner);
        int categoryId = category(csrf, owner);
        LocalDate expected = LocalDate.now(ZoneOffset.UTC).plusDays(3);
        String month = expected.toString().substring(0, 7);

        MvcResult first = mvc.perform(plan(csrf, owner, walletId, categoryId,
                        expected))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREVISTO"))
                .andExpect(jsonPath("$.expectedOn").value(expected.toString()))
                .andExpect(jsonPath("$.referenceMonth").value(expected.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.occurredOn").isEmpty())
                .andReturn();
        int cancelledId = id(first);
        mvc.perform(get("/api/entries?month=" + month).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(100.00));
        mvc.perform(get("/api/monthly-summary?month=" + month).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(0))
                .andExpect(jsonPath("$.plannedExpenses").value(25.00))
                .andExpect(jsonPath("$.projectedResult").value(-25.00));

        mvc.perform(post("/api/entries/{id}/cancel", cancelledId)
                        .cookie(csrf, other).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/entries/{id}/cancel", cancelledId)
                        .cookie(owner))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/entries/{id}/cancel", cancelledId)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/entries/{id}/cancel", cancelledId)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/entries?month=" + month).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/monthly-summary?month=" + month).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(0))
                .andExpect(jsonPath("$.projectedResult").value(0));

        int dueId = id(mvc.perform(plan(csrf, owner, walletId, categoryId, expected))
                .andExpect(status().isCreated()).andReturn());
        LocalDate missed = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        jdbc.update("""
                UPDATE lancamento_financeiro
                SET data_prevista = ?, mes_referencia = ?
                WHERE id = ?
                """, missed, missed.withDayOfMonth(1), dueId);
        processor.processDue();
        OffsetDateTime firstProcessed = jdbc.queryForObject(
                "SELECT efetivado_em FROM lancamento_financeiro WHERE id = ?",
                OffsetDateTime.class, dueId);
        processor.processDue();
        OffsetDateTime secondProcessed = jdbc.queryForObject(
                "SELECT efetivado_em FROM lancamento_financeiro WHERE id = ?",
                OffsetDateTime.class, dueId);
        assertThat(secondProcessed).isEqualTo(firstProcessed);
        assertThat(jdbc.queryForObject("""
                SELECT data_ocorrencia FROM lancamento_financeiro WHERE id = ?
                """, LocalDate.class, dueId)).isEqualTo(missed);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM lancamento_financeiro WHERE id = ?
                """, String.class, cancelledId)).isEqualTo("CANCELADO");
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(75.00));
        mvc.perform(get("/api/entries?month=" + missed.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("EFETIVADO"))
                .andExpect(jsonPath("$[0].occurredOn").value(missed.toString()));
        mvc.perform(get("/api/monthly-summary?month=" + missed.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(25.00))
                .andExpect(jsonPath("$.plannedExpenses").value(0))
                .andExpect(jsonPath("$.projectedResult").value(-25.00));
        mvc.perform(post("/api/entries/{id}/cancel", dueId)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyFutureDatesAndOwnActiveReferencesCanBePlanned() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "plan-validation@example.test");
        register(csrf, "plan-foreign@example.test");
        Cookie owner = login(csrf, "plan-validation@example.test");
        Cookie other = login(csrf, "plan-foreign@example.test");
        int walletId = wallet(csrf, owner);
        int categoryId = category(csrf, owner);
        int foreignCategory = category(csrf, other);
        LocalDate future = LocalDate.now(ZoneOffset.UTC).plusDays(7);

        mvc.perform(plan(csrf, owner, walletId, categoryId, LocalDate.now(ZoneOffset.UTC)))
                .andExpect(status().isBadRequest());
        mvc.perform(plan(csrf, owner, walletId, categoryId, LocalDate.of(2019, 12, 31)))
                .andExpect(status().isBadRequest());
        mvc.perform(plan(csrf, owner, walletId, foreignCategory, future))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/entries/planned").cookie(owner)
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Sem CSRF",
                                 "amount":25,"expectedOn":"%s"}
                                """.formatted(walletId, categoryId, future)))
                .andExpect(status().isForbidden());
        int incomeCategory = category(csrf, owner, "Renda", "RECEITA");
        mvc.perform(plan(csrf, owner, walletId, incomeCategory, future, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("RECEITA"))
                .andExpect(jsonPath("$.necessity").isEmpty());
        mvc.perform(plan(csrf, owner, walletId, categoryId, future, null))
                .andExpect(status().isBadRequest());
        mvc.perform(plan(csrf, owner, walletId, incomeCategory, future, "NEUTRA"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/monthly-summary?month=" + future.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedIncome").value(0))
                .andExpect(jsonPath("$.plannedIncome").value(25.00))
                .andExpect(jsonPath("$.projectedResult").value(25.00));
    }

    @Test
    void finiteSeriesCreatesIndependentMonthlyOccurrencesWithAnchoredDayAndNoBalanceEffect()
            throws Exception {
        Cookie csrf = csrf();
        register(csrf, "series-owner@example.test");
        register(csrf, "series-other@example.test");
        Cookie owner = login(csrf, "series-owner@example.test");
        Cookie other = login(csrf, "series-other@example.test");
        int walletId = wallet(csrf, owner);
        int categoryId = category(csrf, owner);
        LocalDate first = LocalDate.of(LocalDate.now(ZoneOffset.UTC).getYear() + 1, 1, 31);
        int entityId = id(mvc.perform(post("/api/entities")
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"name\":\"Academia\"}"))
                .andExpect(status().isCreated()).andReturn());
        MvcResult created = mvc.perform(series(csrf, owner, walletId, categoryId,
                        first, 3, entityId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("PREVISTO"))
                .andExpect(jsonPath("$[0].occurrenceNumber").value(1))
                .andExpect(jsonPath("$[0].totalOccurrences").value(3))
                .andExpect(jsonPath("$[0].expectedOn").value(first.toString()))
                .andExpect(jsonPath("$[1].expectedOn").value(first.plusMonths(1).toString()))
                .andExpect(jsonPath("$[2].expectedOn").value(first.plusMonths(2).toString()))
                .andExpect(jsonPath("$[2].occurrenceNumber").value(3))
                .andExpect(jsonPath("$[1].entityId").value(entityId))
                .andReturn();
        int seriesId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$[0].seriesId");
        int secondId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$[1].id");
        int firstId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$[0].id");
        assertThat(seriesId).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM lancamento_financeiro WHERE serie_id = ?",
                Integer.class, seriesId)).isEqualTo(3);

        mvc.perform(get("/api/entries/series/{id}", seriesId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].occurrenceNumber").value(3));
        mvc.perform(get("/api/entries/series/{id}", seriesId).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/entries/series/{id}", seriesId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/entries?month=" + first.plusMonths(1).toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seriesId").value(seriesId));
        mvc.perform(get("/api/monthly-summary?month=" + first.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(25.00))
                .andExpect(jsonPath("$.realizedExpenses").value(0));
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(100.00));

        mvc.perform(post("/api/entries/{id}/cancel", secondId)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/entries/series/{id}", seriesId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].status").value("CANCELADO"))
                .andExpect(jsonPath("$[2].status").value("PREVISTO"));
        mvc.perform(get("/api/monthly-summary?month=" +
                        first.plusMonths(1).toString().substring(0, 7)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(0));

        LocalDate missed = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        jdbc.update("""
                UPDATE lancamento_financeiro SET data_prevista = ?, mes_referencia = ?
                WHERE id = ?
                """, missed, missed.withDayOfMonth(1), firstId);
        processor.processDue();
        processor.processDue();
        mvc.perform(get("/api/entries/series/{id}", seriesId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("EFETIVADO"))
                .andExpect(jsonPath("$[0].occurredOn").value(missed.toString()))
                .andExpect(jsonPath("$[1].status").value("CANCELADO"))
                .andExpect(jsonPath("$[2].status").value("PREVISTO"));
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(75.00));
        mvc.perform(get("/api/monthly-summary?month=" +
                        missed.toString().substring(0, 7)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(25.00))
                .andExpect(jsonPath("$.plannedExpenses").value(0));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM lancamento_financeiro WHERE serie_id = ?",
                Integer.class, seriesId)).isEqualTo(3);
    }

    @Test
    void seriesRejectsInvalidInputsWithoutPartialRowsOrCrossOwnerReferences() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "series-invalid@example.test");
        register(csrf, "series-foreign@example.test");
        Cookie owner = login(csrf, "series-invalid@example.test");
        Cookie other = login(csrf, "series-foreign@example.test");
        int walletId = wallet(csrf, owner);
        int categoryId = category(csrf, owner);
        int foreignCategoryId = category(csrf, other);
        LocalDate future = LocalDate.now(ZoneOffset.UTC).plusDays(10);

        mvc.perform(series(csrf, owner, walletId, categoryId, future, 0, null))
                .andExpect(status().isBadRequest());
        mvc.perform(series(csrf, owner, walletId, categoryId, future, 121, null))
                .andExpect(status().isBadRequest());
        mvc.perform(series(csrf, owner, walletId, categoryId, LocalDate.now(ZoneOffset.UTC),
                        3, null))
                .andExpect(status().isBadRequest());
        mvc.perform(series(csrf, owner, walletId, foreignCategoryId, future, 3, null))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/entries/series").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Sem classe",
                                 "amount":25,"firstExpectedOn":"%s","totalOccurrences":3}
                                """.formatted(walletId, categoryId, future)))
                .andExpect(status().isBadRequest());
        mvc.perform(series(csrf, owner, walletId, categoryId,
                        LocalDate.of(9999, 12, 31), 3, null))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/entries/series").cookie(owner)
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Sem CSRF",
                                 "amount":25,"firstExpectedOn":"%s","totalOccurrences":3}
                                """.formatted(walletId, categoryId, future)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM lancamento_financeiro
                WHERE conta_financeira_id = ? AND descricao = 'Despesa recorrente'
                """, Integer.class, walletId)).isZero();

        int ownerId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?",
                Integer.class, "series-invalid@example.test");
        assertThatThrownBy(() -> transactions.executeWithoutResult(ignored -> {
            entries.createSeries(ownerId, walletId, categoryId, "Falha após criar",
                    new java.math.BigDecimal("10.00"), future, 3, null,
                    project.smartfinance.domain.expense.Necessity.NECESSARIA, null);
            throw new IllegalStateException("Falha na transação maior");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM lancamento_financeiro
                WHERE conta_financeira_id = ? AND descricao = 'Falha após criar'
                """, Integer.class, walletId)).isZero();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder series(
            Cookie csrf, Cookie access, int walletId, int categoryId, LocalDate first,
            int count, Integer entityId) {
        return post("/api/entries/series")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"Despesa recorrente",
                         "amount":25.00,"firstExpectedOn":"%s","totalOccurrences":%d,
                          "entityId":%s,"necessity":"NECESSARIA","ownerUserId":999999}
                        """.formatted(walletId, categoryId, first, count, entityId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder plan(
            Cookie csrf, Cookie access, int walletId, int categoryId, LocalDate expected) {
        return plan(csrf, access, walletId, categoryId, expected, "NECESSARIA");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder plan(
            Cookie csrf, Cookie access, int walletId, int categoryId, LocalDate expected,
            String necessity) {
        return post("/api/entries/planned")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"Despesa futura",
                          "amount":25.00,"expectedOn":"%s","necessity":%s,"ownerUserId":999999}
                         """.formatted(walletId, categoryId, expected,
                        necessity == null ? "null" : "\"" + necessity + "\""));
    }

    private int wallet(Cookie csrf, Cookie access) throws Exception {
        return id(mvc.perform(post("/api/wallets")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Dinheiro planejado","initialBalance":100.00,
                                 "initialBalanceDate":"2020-01-01"}
                                """))
                .andExpect(status().isCreated()).andReturn());
    }

    private int category(Cookie csrf, Cookie access) throws Exception {
        return category(csrf, access, "Planejadas", "DESPESA");
    }

    private int category(Cookie csrf, Cookie access, String name, String kind)
            throws Exception {
        return id(mvc.perform(post("/api/categories")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","kind":"%s"}
                                """.formatted(name, kind)))
                .andExpect(status().isCreated()).andReturn());
    }

    private Cookie csrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent()).andReturn()
                .getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private void register(Cookie csrf, String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private int id(MvcResult response) throws Exception {
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id").toString());
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
}
