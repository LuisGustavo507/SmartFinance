package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
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
import project.smartfinance.application.entry.PlannedEntryProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class EntryEditingIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlannedEntryProcessor processor;

    @Test
    void oldPlannedExpenseCanRemainUnclassifiedOrBeClassifiedOnEdit() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "edit-legacy@example.test");
        Cookie owner = login(csrf, "edit-legacy@example.test");
        int walletId = wallet(csrf, owner);
        int expense = category(csrf, owner, "Histórico", "DESPESA");
        int ownerId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?",
                Integer.class, "edit-legacy@example.test");
        LocalDate expected = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        jdbc.update("""
                INSERT INTO lancamento_financeiro
                    (owner_user_id, categoria_id, conta_financeira_id, descricao,
                     valor, mes_referencia, data_prevista, status, criado_em)
                VALUES (?, ?, ?, 'Previsão antiga', 10, ?, ?, 'PREVISTO', ?)
                """, ownerId, expense, walletId, expected.withDayOfMonth(1), expected,
                OffsetDateTime.now());
        int id = jdbc.queryForObject("""
                SELECT id FROM lancamento_financeiro WHERE owner_user_id = ?
                    AND descricao = 'Previsão antiga'
                """, Integer.class, ownerId);
        mvc.perform(update(csrf, owner, id, walletId, expense,
                        "Previsão ainda antiga", "11.00", expected, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.necessity").isEmpty());
        mvc.perform(put("/api/entries/{id}", id).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"walletId":%d,"categoryId":%d,"description":"Classificada",
                                 "amount":11,"expectedOn":"%s","necessity":"NEUTRA"}
                                """.formatted(walletId, expense, expected)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.necessity").value("NEUTRA"));
    }

    @Test
    void editsOnlyPlannedOccurrencesAndValidatesOwnershipAndReferences() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "edit-owner@example.test");
        register(csrf, "edit-other@example.test");
        Cookie owner = login(csrf, "edit-owner@example.test");
        Cookie other = login(csrf, "edit-other@example.test");
        int walletId = wallet(csrf, owner);
        int expenseCategory = category(csrf, owner, "Moradia", "DESPESA");
        int incomeCategory = category(csrf, owner, "Salário", "RECEITA");
        int foreignCategory = category(csrf, other, "Alheia", "DESPESA");
        LocalDate first = LocalDate.of(Year.now(ZoneOffset.UTC).getValue() + 1, 1, 31);
        LocalDate editedDate = first.plusDays(1);

        int plannedId = id(mvc.perform(plan(csrf, owner, walletId, expenseCategory, first))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(update(csrf, owner, plannedId, walletId, incomeCategory,
                        "Receita editada", "40.00", editedDate, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plannedId))
                .andExpect(jsonPath("$.description").value("Receita editada"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.kind").value("RECEITA"))
                .andExpect(jsonPath("$.necessity").isEmpty())
                .andExpect(jsonPath("$.status").value("PREVISTO"))
                .andExpect(jsonPath("$.expectedOn").value(editedDate.toString()))
                .andExpect(jsonPath("$.referenceMonth").value(
                        editedDate.withDayOfMonth(1).toString()));

        mvc.perform(get("/api/entries?month=" + editedDate.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Receita editada"));
        mvc.perform(get("/api/monthly-summary?month=" + editedDate.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedIncome").value(40.00))
                .andExpect(jsonPath("$.plannedExpenses").value(0));
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(100.00));

        mvc.perform(put("/api/entries/{id}", plannedId)
                        .cookie(owner)
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Sem CSRF",
                                 "amount":40.00,"expectedOn":"%s"}
                                """.formatted(walletId, incomeCategory, editedDate)))
                .andExpect(status().isForbidden());
        mvc.perform(update(csrf, other, plannedId, walletId, incomeCategory,
                        "Entrada alheia", "40.00", editedDate, null))
                .andExpect(status().isNotFound());
        mvc.perform(update(csrf, owner, plannedId, walletId, foreignCategory,
                        "Categoria alheia", "40.00", editedDate, null))
                .andExpect(status().isNotFound());
        mvc.perform(update(csrf, owner, plannedId, walletId, incomeCategory,
                        "Data passada", "40.00", LocalDate.now(ZoneOffset.UTC), null))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/entries/{id}", plannedId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"walletId":%d,"categoryId":%d,"description":"Despesa sem classe",
                                 "amount":40,"expectedOn":"%s"}
                                """.formatted(walletId, expenseCategory, editedDate)))
                .andExpect(status().isBadRequest());
        mvc.perform(update(csrf, owner, plannedId, walletId, incomeCategory,
                        "Valor inválido", "0.00", editedDate, null))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/entries/{id}/cancel", plannedId)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(update(csrf, owner, plannedId, walletId, incomeCategory,
                        "Cancelado", "40.00", editedDate, null))
                .andExpect(status().isConflict());

        int realizedId = id(mvc.perform(post("/api/entries")
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Já realizado",
                                  "amount":10.00,"occurredOn":"%s","necessity":"NEUTRA"}
                                """.formatted(walletId, expenseCategory,
                                LocalDate.now(ZoneOffset.UTC).minusDays(1))))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(update(csrf, owner, realizedId, walletId, expenseCategory,
                         "Tentativa", "10.00", first, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void realizedEntryCanBeCorrectedAndCancelledInCurrentMonthButClosedMonthsStayLocked()
            throws Exception {
        Cookie csrf = csrf();
        register(csrf, "edit-realized-window@example.test");
        Cookie owner = login(csrf, "edit-realized-window@example.test");
        int walletA = wallet(csrf, owner);
        int walletB = wallet(csrf, owner, "Outra carteira");
        int expense = category(csrf, owner, "Mercado do mês", "DESPESA");
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        int id = id(mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"walletId":%d,"categoryId":%d,"description":"Compra errada",
                                 "amount":10,"occurredOn":"%s","necessity":"NEUTRA"}
                                """.formatted(walletA, expense, today)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(put("/api/entries/{id}", id).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"walletId":%d,"categoryId":%d,"description":"Compra corrigida",
                                 "amount":25,"occurredOn":"%s","necessity":"NEUTRA"}
                                """.formatted(walletB, expense, today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFETIVADO"))
                .andExpect(jsonPath("$.amount").value(25));
        mvc.perform(get("/api/wallets/{id}", walletA).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(100));
        mvc.perform(get("/api/wallets/{id}", walletB).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(75));
        mvc.perform(get("/api/monthly-summary").param("month", today.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(jsonPath("$.realizedExpenses").value(25));
        mvc.perform(post("/api/entries/{id}/cancel", id).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/wallets/{id}", walletB).cookie(owner))
                .andExpect(jsonPath("$.estimatedBalance").value(100));
        mvc.perform(get("/api/monthly-summary").param("month", today.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(jsonPath("$.realizedExpenses").value(0));
        assertThat(jdbc.queryForObject("SELECT status FROM lancamento_financeiro WHERE id = ?",
                String.class, id)).isEqualTo("CANCELADO");
        mvc.perform(post("/api/entries/{id}/cancel", id).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());

        int oldId = id(mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"walletId":%d,"categoryId":%d,"description":"Mês encerrado",
                                 "amount":12,"occurredOn":"%s","necessity":"NEUTRA"}
                                """.formatted(walletA, expense, today)))
                .andExpect(status().isCreated()).andReturn());
        LocalDate oldMonth = today.withDayOfMonth(1).minusDays(1);
        jdbc.update("UPDATE lancamento_financeiro SET data_ocorrencia = ?, mes_referencia = ? WHERE id = ?",
                oldMonth, oldMonth.withDayOfMonth(1), oldId);
        mvc.perform(put("/api/entries/{id}", oldId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"walletId":%d,"categoryId":%d,"amount":20,
                                 "occurredOn":"%s","necessity":"NEUTRA"}
                                """.formatted(walletA, expense, today)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/entries/{id}/cancel", oldId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/entries/{id}/entity", oldId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"entityId\":null}"))
                .andExpect(status().isConflict());
    }

    @Test
    void editsThisAndFutureOccurrencesWithoutTouchingCancelledOrEffectedRows()
            throws Exception {
        Cookie csrf = csrf();
        register(csrf, "edit-series@example.test");
        register(csrf, "edit-series-other@example.test");
        Cookie owner = login(csrf, "edit-series@example.test");
        Cookie other = login(csrf, "edit-series-other@example.test");
        int walletId = wallet(csrf, owner);
        int expenseCategory = category(csrf, owner, "Assinaturas", "DESPESA");
        int newCategory = category(csrf, owner, "Serviços", "DESPESA");
        int entityId = id(mvc.perform(post("/api/entities")
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"name\":\"Provedora\"}"))
                .andExpect(status().isCreated()).andReturn());
        LocalDate first = LocalDate.of(Year.now(ZoneOffset.UTC).getValue() + 1, 1, 31);

        MvcResult created = mvc.perform(series(csrf, owner, walletId, expenseCategory,
                        first, 5))
                .andExpect(status().isCreated()).andReturn();
        int seriesId = value(created, "$[0].seriesId");
        int secondId = value(created, "$[1].id");
        int thirdId = value(created, "$[2].id");
        int fourthId = value(created, "$[3].id");
        int fifthId = value(created, "$[4].id");

        mvc.perform(post("/api/entries/{id}/cancel", fourthId)
                        .cookie(csrf, owner).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());

        LocalDate newAnchor = first;
        mvc.perform(updateSeries(csrf, owner, seriesId, 2, walletId, newCategory,
                        "Assinatura reajustada", "30.00", newAnchor, entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[0].occurrenceNumber").value(2))
                .andExpect(jsonPath("$[0].expectedOn").value(first.toString()))
                .andExpect(jsonPath("$[0].description").value("Assinatura reajustada"))
                .andExpect(jsonPath("$[0].amount").value(30.00))
                .andExpect(jsonPath("$[0].categoryId").value(newCategory))
                .andExpect(jsonPath("$[0].entityId").value(entityId))
                .andExpect(jsonPath("$[1].id").value(thirdId))
                .andExpect(jsonPath("$[1].expectedOn").value(first.plusMonths(1).toString()))
                .andExpect(jsonPath("$[2].id").value(fifthId))
                .andExpect(jsonPath("$[2].expectedOn").value(first.plusMonths(3).toString()));
        mvc.perform(put("/api/entries/series/{seriesId}/occurrences/{occurrenceNumber}",
                        seriesId, 2).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("""
                                {"walletId":%d,"categoryId":%d,"description":"Reclassificada",
                                 "amount":30,"expectedOn":"%s","necessity":"FUTIL"}
                                """.formatted(walletId, newCategory, newAnchor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].necessity").value("FUTIL"))
                .andExpect(jsonPath("$[1].necessity").value("FUTIL"))
                .andExpect(jsonPath("$[2].necessity").value("FUTIL"));

        mvc.perform(get("/api/entries/series/{id}", seriesId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].description").value("Despesa recorrente"))
                .andExpect(jsonPath("$[0].amount").value(25.00))
                .andExpect(jsonPath("$[0].expectedOn").value(first.toString()))
                .andExpect(jsonPath("$[2].status").value("PREVISTO"))
                .andExpect(jsonPath("$[3].status").value("CANCELADO"))
                .andExpect(jsonPath("$[3].description").value("Despesa recorrente"))
                .andExpect(jsonPath("$[3].amount").value(25.00))
                .andExpect(jsonPath("$[3].expectedOn").value(first.plusMonths(3).toString()))
                .andExpect(jsonPath("$[4].status").value("PREVISTO"))
                .andExpect(jsonPath("$[4].description").value("Reclassificada"))
                .andExpect(jsonPath("$[0].necessity").value("NECESSARIA"))
                .andExpect(jsonPath("$[3].necessity").value("NECESSARIA"))
                .andExpect(jsonPath("$[4].necessity").value("FUTIL"));

        mvc.perform(get("/api/monthly-summary?month=" + first.toString().substring(0, 7))
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(55.00));
        mvc.perform(get("/api/monthly-summary?month=" +
                        first.plusMonths(1).toString().substring(0, 7)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(30.00));
        mvc.perform(get("/api/monthly-summary?month=" +
                        first.plusMonths(3).toString().substring(0, 7)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(30.00));
        mvc.perform(get("/api/wallets/{id}", walletId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(100.00));

        mvc.perform(updateSeries(csrf, other, seriesId, 2, walletId, newCategory,
                        "Série alheia", "30.00", newAnchor, null))
                .andExpect(status().isNotFound());
        mvc.perform(updateSeries(csrf, owner, seriesId, 0, walletId, newCategory,
                        "Ocorrência zero", "30.00", newAnchor, null))
                .andExpect(status().isBadRequest());
        mvc.perform(updateSeries(csrf, owner, seriesId, 99, walletId, newCategory,
                        "Ocorrência ausente", "30.00", newAnchor, null))
                .andExpect(status().isNotFound());
        mvc.perform(updateSeries(csrf, owner, seriesId, 4, walletId, newCategory,
                        "Ocorrência cancelada", "30.00", newAnchor, null))
                .andExpect(status().isConflict());

        LocalDate missed = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        jdbc.update("""
                UPDATE lancamento_financeiro SET data_prevista = ?, mes_referencia = ?
                WHERE id = ?
                """, missed, missed.withDayOfMonth(1), thirdId);
        processor.processDue();
        mvc.perform(updateSeries(csrf, owner, seriesId, 3, walletId, newCategory,
                        "Depois de efetivada", "30.00", newAnchor, null))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/entries/series/{id}", seriesId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].status").value("EFETIVADO"))
                .andExpect(jsonPath("$[2].description").value("Reclassificada"))
                .andExpect(jsonPath("$[4].description").value("Reclassificada"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder update(
            Cookie csrf, Cookie access, int id, int walletId, int categoryId,
            String description, String amount, LocalDate expectedOn, Integer entityId) {
        return put("/api/entries/{id}", id)
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"%s",
                         "amount":%s,"expectedOn":"%s","entityId":%s}
                        """.formatted(walletId, categoryId, description, amount,
                        expectedOn, entityId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateSeries(
            Cookie csrf, Cookie access, int seriesId, int occurrenceNumber, int walletId,
            int categoryId, String description, String amount, LocalDate expectedOn,
            Integer entityId) {
        return put("/api/entries/series/{seriesId}/occurrences/{occurrenceNumber}",
                        seriesId, occurrenceNumber)
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"%s",
                         "amount":%s,"expectedOn":"%s","entityId":%s}
                        """.formatted(walletId, categoryId, description, amount,
                        expectedOn, entityId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder plan(
            Cookie csrf, Cookie access, int walletId, int categoryId, LocalDate expected) {
        return post("/api/entries/planned")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"Despesa futura",
                          "amount":25.00,"expectedOn":"%s","necessity":"NECESSARIA"}
                        """.formatted(walletId, categoryId, expected));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder series(
            Cookie csrf, Cookie access, int walletId, int categoryId, LocalDate first,
            int count) {
        return post("/api/entries/series")
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"Despesa recorrente",
                          "amount":25.00,"firstExpectedOn":"%s","totalOccurrences":%d,
                          "necessity":"NECESSARIA"}
                        """.formatted(walletId, categoryId, first, count));
    }

    private int wallet(Cookie csrf, Cookie access) throws Exception {
        return wallet(csrf, access, "Dinheiro editável");
    }

    private int wallet(Cookie csrf, Cookie access, String name) throws Exception {
        return id(mvc.perform(post("/api/wallets")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","initialBalance":100.00,
                                  "initialBalanceDate":"2020-01-01"}
                                """.formatted(name)))
                .andExpect(status().isCreated()).andReturn());
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

    private int value(MvcResult response, String path) throws Exception {
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), path).toString());
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
