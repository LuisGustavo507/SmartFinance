package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class EntryIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void filtersTheMonthByEntityIncludingCardInstallments() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "entry-entity-filter@example.test");
        Cookie owner = login(csrf, "entry-entity-filter@example.test");
        int wallet = wallet(csrf, owner, "Carteira filtro");
        int expense = category(csrf, owner, "Compras filtro", "DESPESA");
        int income = category(csrf, owner, "Renda filtro", "RECEITA");
        int entity = id(mvc.perform(post("/api/entities").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"name\":\"Fornecedor\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Compra do fornecedor",
                                 "amount":30,"occurredOn":"2026-01-04","entityId":%d,
                                 "necessity":"NEUTRA"}
                                """.formatted(wallet, expense, entity)))
                .andExpect(status().isCreated());
        mvc.perform(entry(csrf, owner, wallet, expense, "Compra sem entidade", "10.00",
                        "2026-01-05", "NEUTRA"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Receita do fornecedor",
                                 "amount":80,"occurredOn":"2026-01-06","entityId":%d}
                                """.formatted(wallet, income, entity)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/monthly-summary").param("month", "2026-01")
                        .param("entityId", String.valueOf(entity)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedIncome").value(80.00))
                .andExpect(jsonPath("$.realizedExpenses").value(30.00))
                .andExpect(jsonPath("$.realizedResult").value(50.00))
                .andExpect(jsonPath("$.categoryTotals.length()").value(2))
                .andExpect(jsonPath("$.necessityTotals.length()").value(1))
                .andExpect(jsonPath("$.necessityTotals[0].realizedExpenses").value(30.00));
        mvc.perform(get("/api/monthly-summary").param("month", "2026-01").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedIncome").value(80.00))
                .andExpect(jsonPath("$.realizedExpenses").value(40.00));
    }

    @Test
    void reportsTheMonthlySeriesFillingEmptyMonths() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "entry-monthly-report@example.test");
        Cookie owner = login(csrf, "entry-monthly-report@example.test");
        int wallet = wallet(csrf, owner, "Carteira série mensal");
        int expense = category(csrf, owner, "Compras série", "DESPESA");
        int income = category(csrf, owner, "Renda série", "RECEITA");
        mvc.perform(entry(csrf, owner, wallet, expense, "Janeiro", "30.00",
                        "2026-01-10", "NEUTRA"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Março",
                                 "amount":70,"occurredOn":"2026-03-10","necessity":"NEUTRA"}
                                """.formatted(wallet, expense)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Renda de março",
                                 "amount":200,"occurredOn":"2026-03-15"}
                                """.formatted(wallet, income)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/reports/monthly").param("from", "2026-01")
                        .param("to", "2026-03").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].month").value("2026-01-01"))
                .andExpect(jsonPath("$[0].realizedExpenses").value(30.00))
                .andExpect(jsonPath("$[0].realizedIncome").value(0))
                .andExpect(jsonPath("$[0].realizedResult").value(-30.00))
                .andExpect(jsonPath("$[1].month").value("2026-02-01"))
                .andExpect(jsonPath("$[1].realizedExpenses").value(0))
                .andExpect(jsonPath("$[1].realizedResult").value(0))
                .andExpect(jsonPath("$[2].month").value("2026-03-01"))
                .andExpect(jsonPath("$[2].realizedExpenses").value(70.00))
                .andExpect(jsonPath("$[2].realizedIncome").value(200.00))
                .andExpect(jsonPath("$[2].realizedResult").value(130.00))
                .andExpect(jsonPath("$[2].projectedResult").value(130.00));
        mvc.perform(get("/api/reports/monthly").param("from", "2026-03")
                        .param("to", "2026-01").cookie(owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsEntitiesOverAPeriodSeparatingIncomeAndExpenses() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "entry-entity-period@example.test");
        Cookie owner = login(csrf, "entry-entity-period@example.test");
        int wallet = wallet(csrf, owner, "Carteira período");
        int expense = category(csrf, owner, "Compras período", "DESPESA");
        int income = category(csrf, owner, "Renda período", "RECEITA");
        int entity = id(mvc.perform(post("/api/entities").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"name\":\"Distribuidora\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Janeiro da distribuidora",
                                 "amount":40,"occurredOn":"2026-01-10","entityId":%d,
                                 "necessity":"NEUTRA"}
                                """.formatted(wallet, expense, entity)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Fevereiro da distribuidora",
                                 "amount":70,"occurredOn":"2026-02-10","entityId":%d,
                                 "necessity":"NEUTRA"}
                                """.formatted(wallet, expense, entity)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Receita da distribuidora",
                                 "amount":200,"occurredOn":"2026-02-15","entityId":%d}
                                """.formatted(wallet, income, entity)))
                .andExpect(status().isCreated());
        mvc.perform(entry(csrf, owner, wallet, expense, "Sem entidade no período", "5.00",
                        "2026-03-02", "NEUTRA"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/reports/entity-period").param("from", "2026-01")
                        .param("to", "2026-02").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-01-01"))
                .andExpect(jsonPath("$.to").value("2026-02-28"))
                .andExpect(jsonPath("$.entities.length()").value(1))
                .andExpect(jsonPath("$.entities[0].entityName").value("Distribuidora"))
                .andExpect(jsonPath("$.entities[0].realizedExpenses").value(110.00))
                .andExpect(jsonPath("$.entities[0].realizedIncome").value(200.00))
                .andExpect(jsonPath("$.entities[0].plannedExpenses").value(0));
        mvc.perform(get("/api/reports/entity-period").param("from", "2026-01")
                        .param("to", "2026-03").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.length()").value(2))
                .andExpect(jsonPath("$.entities[0].entityName").value("Distribuidora"))
                .andExpect(jsonPath("$.entities[1].entityName").value("Sem entidade"))
                .andExpect(jsonPath("$.entities[1].realizedExpenses").value(5.00));
        mvc.perform(get("/api/reports/entity-period").param("from", "2026-03")
                        .param("to", "2026-01").cookie(owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void groupsTheMonthByCategoryAndNatureReconcilingWithTheTotals() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "entry-category-totals@example.test");
        Cookie owner = login(csrf, "entry-category-totals@example.test");
        int wallet = wallet(csrf, owner, "Carteira agrupamento");
        int mercado = category(csrf, owner, "Mercado", "DESPESA");
        int lazer = category(csrf, owner, "Lazer", "DESPESA");
        int salario = category(csrf, owner, "Salário", "RECEITA");

        mvc.perform(entry(csrf, owner, wallet, mercado, "Compras", "60.00",
                        "2026-01-04", "NECESSARIA"))
                .andExpect(status().isCreated());
        mvc.perform(entry(csrf, owner, wallet, lazer, "Cinema", "25.00",
                        "2026-01-06", "FUTIL"))
                .andExpect(status().isCreated());
        mvc.perform(entry(csrf, owner, wallet, salario, "Salário", "100.00",
                        "2026-01-05", null))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/entries/planned").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Mercado previsto",
                                 "amount":30,"expectedOn":"2099-01-10","necessity":"NEUTRA"}
                                """.formatted(wallet, mercado)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/monthly-summary?month=2026-01").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedIncome").value(100.00))
                .andExpect(jsonPath("$.realizedExpenses").value(85.00))
                .andExpect(jsonPath("$.categoryTotals.length()").value(3))
                .andExpect(jsonPath("$.categoryTotals[0].categoryName").value("Salário"))
                .andExpect(jsonPath("$.categoryTotals[0].kind").value("RECEITA"))
                .andExpect(jsonPath("$.categoryTotals[0].realizedIncome").value(100.00))
                .andExpect(jsonPath("$.categoryTotals[0].realizedExpenses").value(0))
                .andExpect(jsonPath("$.categoryTotals[1].categoryName").value("Lazer"))
                .andExpect(jsonPath("$.categoryTotals[1].kind").value("DESPESA"))
                .andExpect(jsonPath("$.categoryTotals[1].realizedExpenses").value(25.00))
                .andExpect(jsonPath("$.categoryTotals[2].categoryName").value("Mercado"))
                .andExpect(jsonPath("$.categoryTotals[2].realizedExpenses").value(60.00))
                .andExpect(jsonPath("$.categoryTotals[2].plannedExpenses").value(0));

        mvc.perform(get("/api/monthly-summary?month=2099-01").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedExpenses").value(30.00))
                .andExpect(jsonPath("$.categoryTotals.length()").value(1))
                .andExpect(jsonPath("$.categoryTotals[0].categoryName").value("Mercado"))
                .andExpect(jsonPath("$.categoryTotals[0].plannedExpenses").value(30.00))
                .andExpect(jsonPath("$.categoryTotals[0].realizedExpenses").value(0));
    }

    @Test
    void correctsTheEntityLinkOfEffectedRecordsWithoutTouchingOtherData() throws Exception {
        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        Cookie csrf = csrf();
        int ownerId = register(csrf, "entry-entity-link@example.test");
        register(csrf, "entry-entity-link-other@example.test");
        Cookie owner = login(csrf, "entry-entity-link@example.test");
        Cookie other = login(csrf, "entry-entity-link-other@example.test");
        int wallet = wallet(csrf, owner, "Carteira vínculo");
        int expense = category(csrf, owner, "Compras vínculo", "DESPESA");
        int first = id(mvc.perform(post("/api/entities").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"name\":\"Fornecedor A\"}"))
                .andExpect(status().isCreated()).andReturn());
        int second = id(mvc.perform(post("/api/entities").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"name\":\"Fornecedor B\"}"))
                .andExpect(status().isCreated()).andReturn());
        MvcResult created = mvc.perform(entry(csrf, owner, wallet, expense, "Compra vinculada",
                        "40.00", today, "NECESSARIA"))
                .andExpect(status().isCreated()).andReturn();
        int entryId = id(created);
        mvc.perform(put("/api/entries/{id}/entity", entryId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").value(first))
                .andExpect(jsonPath("$.entityName").value("Fornecedor A"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.status").value("EFETIVADO"))
                .andExpect(jsonPath("$.occurredOn").value(today));

        mvc.perform(put("/api/entries/{id}/entity", entryId).cookie(csrf, other)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(second)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/entries/{id}/entity", 999999).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(second)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/entries/{id}/entity", entryId).cookie(owner)
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(second)))
                .andExpect(status().isForbidden());

        jdbc.update("UPDATE entidade SET ativa = false WHERE owner_user_id = ? AND id = ?",
                ownerId, second);
        mvc.perform(put("/api/entries/{id}/entity", entryId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(second)))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE entidade SET ativa = true WHERE owner_user_id = ? AND id = ?",
                ownerId, second);
        mvc.perform(put("/api/entries/{id}/entity", entryId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"entityId\":%d}".formatted(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityName").value("Fornecedor B"));

        mvc.perform(put("/api/entries/{id}/entity", entryId).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"entityId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").isEmpty())
                .andExpect(jsonPath("$.entityName").isEmpty())
                .andExpect(jsonPath("$.amount").value(40.00));
        mvc.perform(get("/api/entries?month=" + today.substring(0, 7)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entityName").isEmpty());
    }

    @Test
    void createsEntityDuringTheRecordAndKeepsItAtomic() throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "entry-new-entity@example.test");
        Cookie owner = login(csrf, "entry-new-entity@example.test");
        assertThat(ownerId).isPositive();
        int wallet = wallet(csrf, owner, "Carteira entidade nova");
        int expense = category(csrf, owner, "Compras diversas", "DESPESA");

        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Compra com entidade nova",
                                 "amount":10.00,"occurredOn":"2026-01-05",
                                 "necessity":"NEUTRA","newEntityName":"  Padaria Nova  "}
                                """.formatted(wallet, expense)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityName").value("Padaria Nova"))
                .andExpect(jsonPath("$.entityId").isNumber());
        mvc.perform(get("/api/entities").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Padaria Nova"));

        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Nome repetido",
                                 "amount":10.00,"occurredOn":"2026-01-05",
                                 "necessity":"NEUTRA","newEntityName":"padaria nova"}
                                """.formatted(wallet, expense)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/entities").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Dois nomes",
                                 "amount":10.00,"occurredOn":"2026-01-05",
                                 "necessity":"NEUTRA","entityId":1,"newEntityName":"Outra"}
                                """.formatted(wallet, expense)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Nome vazio",
                                 "amount":10.00,"occurredOn":"2026-01-05",
                                 "necessity":"NEUTRA","newEntityName":"   "}
                                """.formatted(wallet, expense)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/entities").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void failedRecordDoesNotLeaveAnOrphanEntity() throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "entry-orphan-entity@example.test");
        Cookie owner = login(csrf, "entry-orphan-entity@example.test");
        int wallet = wallet(csrf, owner, "Carteira sem entidade");
        int expense = category(csrf, owner, "Compras sem entidade", "DESPESA");

        mvc.perform(post("/api/entries").cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Valor inválido",
                                 "amount":0,"occurredOn":"2026-01-05",
                                 "necessity":"NEUTRA","newEntityName":"Entidade órfã"}
                                """.formatted(wallet, expense)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM entidade WHERE owner_user_id = ?
                """, Integer.class, ownerId)).isZero();
    }

    @Test
    void requiresNecessityForNewExpensesButPreservesUnclassifiedHistory() throws Exception {
        Cookie csrf = csrf();
        int ownerId = register(csrf, "entry-necessity@example.test");
        Cookie owner = login(csrf, "entry-necessity@example.test");
        int wallet = wallet(csrf, owner, "Carteira necessidade");
        int expense = category(csrf, owner, "Mercado necessidade", "DESPESA");
        int income = category(csrf, owner, "Renda necessidade", "RECEITA");

        mvc.perform(entry(csrf, owner, wallet, expense, "Sem classificação", "10",
                        "2026-01-01", null))
                .andExpect(status().isBadRequest());
        mvc.perform(entry(csrf, owner, wallet, income, "Receita classificada", "10",
                        "2026-01-01", "FUTIL"))
                .andExpect(status().isBadRequest());
        mvc.perform(entry(csrf, owner, wallet, expense, "Classificada", "10",
                        "2026-01-01", "FUTIL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.necessity").value("FUTIL"));
        mvc.perform(entry(csrf, owner, wallet, income, "Receita", "10",
                        "2026-01-01", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.necessity").isEmpty());

        jdbc.update("""
                INSERT INTO lancamento_financeiro
                    (owner_user_id, categoria_id, conta_financeira_id, descricao,
                     valor, mes_referencia, data_ocorrencia, efetivado_em, status, criado_em)
                VALUES (?, ?, ?, 'Legada', 5, '2026-01-01', '2026-01-01', now(),
                        'EFETIVADO', now())
                """, ownerId, expense, wallet);
        mvc.perform(get("/api/entries?month=2026-01").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].description").value("Legada"))
                .andExpect(jsonPath("$[2].necessity").isEmpty());
        mvc.perform(get("/api/monthly-summary?month=2026-01").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.necessityTotals.length()").value(2))
                .andExpect(jsonPath("$.necessityTotals[0].necessity").value("FUTIL"))
                .andExpect(jsonPath("$.necessityTotals[0].realizedExpenses").value(10))
                .andExpect(jsonPath("$.necessityTotals[1].necessity").isEmpty())
                .andExpect(jsonPath("$.necessityTotals[1].realizedExpenses").value(5));
        assertThat(jdbc.queryForObject("""
                SELECT necessidade FROM lancamento_financeiro
                WHERE owner_user_id = ? AND descricao = 'Legada'
                """, String.class, ownerId)).isNull();
    }

    @Test
    void realizedEntriesUseOccurrenceMonthAndChangeEstimatedBalanceEvenBelowZero()
            throws Exception {
        Cookie csrf = csrf();
        int owner = register(csrf, "entry-owner@example.test");
        register(csrf, "entry-other@example.test");
        Cookie access = login(csrf, "entry-owner@example.test");
        Cookie otherAccess = login(csrf, "entry-other@example.test");
        int wallet = wallet(csrf, access, "Carteira 1");
        int expense = category(csrf, access, "Mercado", "DESPESA");
        int income = category(csrf, access, "Trabalho", "RECEITA");

        mvc.perform(entry(csrf, access, wallet, expense, "Compra antiga", "25.50",
                        "2026-01-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(owner))
                .andExpect(jsonPath("$.kind").value("DESPESA"))
                .andExpect(jsonPath("$.referenceMonth").value("2026-01-01"))
                .andExpect(jsonPath("$.occurredOn").value("2026-01-01"))
                .andExpect(jsonPath("$.status").value("EFETIVADO"));
        mvc.perform(entry(csrf, access, wallet, income, "Reembolso", "40.00",
                        "2026-01-02", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.necessity").isEmpty());
        mvc.perform(entry(csrf, access, wallet, expense, "Despesa grande", "200.00",
                        "2026-02-03")).andExpect(status().isCreated());

        mvc.perform(get("/api/entries?month=2026-01").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].description").value("Compra antiga"))
                .andExpect(jsonPath("$[1].kind").value("RECEITA"));
        mvc.perform(get("/api/entries?month=2026-02").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/entries?month=2026-11").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/entries?month=2026-01").cookie(otherAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/entries?month=2026-01"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wallets/{id}", wallet).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialBalance").value(100.00))
                .andExpect(jsonPath("$.estimatedBalance").value(-85.50));
        mvc.perform(get("/api/wallets").cookie(otherAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/monthly-summary?month=2026-01").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-01-01"))
                .andExpect(jsonPath("$.realizedIncome").value(40.00))
                .andExpect(jsonPath("$.realizedExpenses").value(25.50))
                .andExpect(jsonPath("$.realizedResult").value(14.50))
                .andExpect(jsonPath("$.necessityTotals[0].necessity").value("NECESSARIA"))
                .andExpect(jsonPath("$.necessityTotals[0].realizedExpenses").value(25.50))
                .andExpect(jsonPath("$.plannedIncome").value(0))
                .andExpect(jsonPath("$.plannedExpenses").value(0))
                .andExpect(jsonPath("$.projectedResult").value(14.50));
        mvc.perform(get("/api/monthly-summary?month=2026-02").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedIncome").value(0))
                .andExpect(jsonPath("$.realizedExpenses").value(200.00))
                .andExpect(jsonPath("$.realizedResult").value(-200.00));
        mvc.perform(get("/api/monthly-summary?month=2026-01").cookie(otherAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(0))
                .andExpect(jsonPath("$.realizedResult").value(0));
        mvc.perform(get("/api/monthly-summary?month=2026-03").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedResult").value(0));
        mvc.perform(get("/api/monthly-summary?month=2026-01"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("""
                SELECT criado_em::date > data_ocorrencia
                FROM lancamento_financeiro
                WHERE owner_user_id = ? AND descricao = 'Compra antiga'
                """, Boolean.class, owner)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT efetivado_em IS NOT NULL
                FROM lancamento_financeiro
                WHERE owner_user_id = ? AND descricao = 'Compra antiga'
                """, Boolean.class, owner)).isTrue();
    }

    @Test
    void rejectsCrossOwnerInactiveReferencesInvalidDatesMoneyAndMissingCsrf()
            throws Exception {
        Cookie csrf = csrf();
        int owner = register(csrf, "entry-validation@example.test");
        register(csrf, "entry-foreign@example.test");
        Cookie access = login(csrf, "entry-validation@example.test");
        Cookie foreignAccess = login(csrf, "entry-foreign@example.test");
        int wallet = wallet(csrf, access, "Minha carteira");
        int category = category(csrf, access, "Despesas", "DESPESA");
        int foreignWallet = wallet(csrf, foreignAccess, "Outra carteira");
        int foreignCategory = category(csrf, foreignAccess, "Despesas", "DESPESA");

        mvc.perform(entry(csrf, access, foreignWallet, category, "Outra carteira",
                        "10.00", "2026-01-01"))
                .andExpect(status().isNotFound());
        mvc.perform(entry(csrf, access, wallet, foreignCategory, "Outra categoria",
                        "10.00", "2026-01-01"))
                .andExpect(status().isNotFound());
        mvc.perform(entry(csrf, access, wallet, category, "Data antiga", "1",
                        "2025-12-31"))
                .andExpect(status().isBadRequest());
        mvc.perform(entry(csrf, access, wallet, category, "No futuro", "1",
                        "2099-01-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(entry(csrf, access, wallet, category, "Valor zero", "0",
                        "2026-01-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(entry(csrf, access, wallet, category, "Valor fracionado", "0.001",
                        "2026-01-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(entry(csrf, access, wallet, category, "  ", "10",
                        "2026-01-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(""));
        mvc.perform(post("/api/entries").cookie(csrf, access)
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"description":"Sem CSRF",
                                 "amount":10,"occurredOn":"2026-01-01"}
                                """.formatted(wallet, category)))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE categoria SET ativa = false WHERE owner_user_id = ? AND id = ?",
                owner, category);
        mvc.perform(entry(csrf, access, wallet, category, "Inativa", "1",
                        "2026-01-01"))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE categoria SET ativa = true WHERE owner_user_id = ? AND id = ?",
                owner, category);
        jdbc.update("UPDATE conta_financeira SET ativa = false WHERE owner_user_id = ? AND id = ?",
                owner, wallet);
        mvc.perform(entry(csrf, access, wallet, category, "Carteira inativa", "1",
                        "2026-01-01"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM lancamento_financeiro WHERE owner_user_id = ?",
                Integer.class, owner)).isEqualTo(1);
    }

    @Test
    void databaseRejectsReferencesToAnotherOwnerEvenByDirectInsert() throws Exception {
        Cookie csrf = csrf();
        int owner = register(csrf, "entry-dbowner@example.test");
        register(csrf, "entry-dbother@example.test");
        Cookie access = login(csrf, "entry-dbowner@example.test");
        Cookie other = login(csrf, "entry-dbother@example.test");
        int wallet = wallet(csrf, access, "Banco 1");
        int foreignCategory = category(csrf, other, "Alheia", "DESPESA");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lancamento_financeiro
                (owner_user_id, categoria_id, conta_financeira_id,
                 descricao, valor, mes_referencia, data_ocorrencia,
                 efetivado_em, status, criado_em)
                VALUES (?, ?, ?, 'Violação', ?, ?, ?, ?, 'EFETIVADO', ?)
                """, owner, foreignCategory, wallet, new BigDecimal("1.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1),
                OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plannedEntryDoesNotChangeRealizedBalanceOrMonthlySummary() throws Exception {
        Cookie csrf = csrf();
        int owner = register(csrf, "entry-planned@example.test");
        Cookie access = login(csrf, "entry-planned@example.test");
        int wallet = wallet(csrf, access, "Dinheiro previsto");
        int category = category(csrf, access, "Prevista", "DESPESA");
        jdbc.update("""
                INSERT INTO lancamento_financeiro
                (owner_user_id, categoria_id, conta_financeira_id, descricao,
                 valor, mes_referencia, data_prevista, status, criado_em)
                VALUES (?, ?, ?, 'Ainda não ocorreu', ?, ?, ?, 'PREVISTO', ?)
                """, owner, category, wallet, new BigDecimal("50.00"),
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 9),
                OffsetDateTime.now());

        mvc.perform(get("/api/monthly-summary?month=2026-12").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(0))
                .andExpect(jsonPath("$.realizedResult").value(0))
                .andExpect(jsonPath("$.plannedExpenses").value(50.00))
                .andExpect(jsonPath("$.projectedResult").value(-50.00));
        mvc.perform(get("/api/wallets/{id}", wallet).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedBalance").value(100.00));
        mvc.perform(get("/api/entries?month=2026-12").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PREVISTO"));
    }

    @Test
    void optionalCounterpartyIsOwnedUniqueFilterableAndPreservedInHistory()
            throws Exception {
        Cookie csrf = csrf();
        int owner = register(csrf, "entity-owner@example.test");
        register(csrf, "entity-other@example.test");
        Cookie access = login(csrf, "entity-owner@example.test");
        Cookie other = login(csrf, "entity-other@example.test");
        int wallet = wallet(csrf, access, "Dinheiro entidade");
        int category = category(csrf, access, "Compras entidade", "DESPESA");
        int entityId = entity(csrf, access, "  Mercado  ");
        int foreignEntityId = entity(csrf, other, "Mercado");

        mvc.perform(post("/api/entities").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"mercado"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/entities").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mercado"))
                .andExpect(jsonPath("$[0].ownerUserId").value(owner));
        mvc.perform(get("/api/entities").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/entities")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/entities").cookie(access)
                        .contentType("application/json")
                        .content("""
                                {"name":"Sem CSRF"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/entries").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"entityId":%d,
                                 "description":"Compra com entidade","amount":10.00,
                                  "occurredOn":"2026-01-03","necessity":"NEUTRA"}
                                """.formatted(wallet, category, entityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityId").value(entityId))
                .andExpect(jsonPath("$.entityName").value("Mercado"));
        mvc.perform(entry(csrf, access, wallet, category, "Sem entidade", "5.00",
                        "2026-01-03"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityId").isEmpty());
        mvc.perform(post("/api/entries").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"entityId":%d,
                                 "description":"Alheia","amount":1,
                                  "occurredOn":"2026-01-03","necessity":"NEUTRA"}
                                """.formatted(wallet, category, foreignEntityId)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/entries?month=2026-01&entityId=" + entityId)
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityName").value("Mercado"));
        mvc.perform(get("/api/entries?month=2026-01&entityId=" + foreignEntityId)
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/entries?month=2026-01").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/monthly-summary?month=2026-01").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(15.00));

        mvc.perform(renameEntity(csrf, access, entityId, "  Mercadinho  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mercadinho"));
        mvc.perform(changeEntityStatus(csrf, access, entityId, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/entries?month=2026-01&entityId=" + entityId)
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entityName").value("Mercadinho"));
        mvc.perform(post("/api/entries").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"entityId":%d,
                                 "description":"Inativa","amount":1,
                                  "occurredOn":"2026-01-03","necessity":"NEUTRA"}
                                """.formatted(wallet, category, entityId)))
                .andExpect(status().isNotFound());
        mvc.perform(changeEntityStatus(csrf, access, entityId, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/entries").cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"walletId":%d,"categoryId":%d,"entityId":%d,
                                 "description":"Reativada","amount":1,
                                  "occurredOn":"2026-01-03","necessity":"NEUTRA"}
                                """.formatted(wallet, category, entityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityName").value("Mercadinho"));
        mvc.perform(get("/api/monthly-summary?month=2026-01").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedExpenses").value(16.00));
        mvc.perform(delete("/api/entities/{id}", entityId).cookie(csrf, access)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/entries?month=2026-01&entityId=" + entityId)
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entityName").value("Mercadinho"));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lancamento_financeiro
                (owner_user_id, categoria_id, conta_financeira_id, entidade_id,
                 descricao, valor, mes_referencia, data_ocorrencia,
                 efetivado_em, status, criado_em)
                VALUES (?, ?, ?, ?, 'Entidade alheia', ?, ?, ?, ?, 'EFETIVADO', ?)
                """, owner, category, wallet, foreignEntityId, new BigDecimal("1.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1),
                OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void counterpartyStatusRequiresOwnerValidBodyAndCsrf() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "entity-status-owner@example.test");
        register(csrf, "entity-status-other@example.test");
        Cookie owner = login(csrf, "entity-status-owner@example.test");
        Cookie other = login(csrf, "entity-status-other@example.test");
        int id = entity(csrf, owner, "Fornecedor");
        int otherId = entity(csrf, owner, "Cliente");

        mvc.perform(changeEntityStatus(csrf, other, id, false))
                .andExpect(status().isNotFound());
        mvc.perform(changeEntityStatus(csrf, owner, 999999, false))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/entities/{id}/status", id).cookie(csrf, owner)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/entities/{id}/status", id).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(changeEntityStatus(csrf, owner, id, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(changeEntityStatus(csrf, owner, id, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/entities").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].active").value(false));
        mvc.perform(get("/api/entities").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(renameEntity(csrf, other, id, "Acesso alheio"))
                .andExpect(status().isNotFound());
        mvc.perform(renameEntity(csrf, owner, 999999, "Inexistente"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/entities/{id}", id).cookie(csrf, owner)
                        .contentType("application/json")
                        .content("{\"name\":\"Sem CSRF\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(renameEntity(csrf, owner, id, "   "))
                .andExpect(status().isBadRequest());
        mvc.perform(renameEntity(csrf, owner, id, "  cLiEnTe  "))
                .andExpect(status().isConflict());
        mvc.perform(renameEntity(csrf, owner, id, "Fornecedor atualizado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.name").value("Fornecedor atualizado"));
        mvc.perform(get("/api/entities").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(otherId))
                .andExpect(jsonPath("$[1].name").value("Fornecedor atualizado"));
    }

    @Test
    void deletesOnlyUnlinkedOwnCounterpartyWithCsrf() throws Exception {
        Cookie csrf = csrf();
        register(csrf, "entity-delete-owner@example.test");
        register(csrf, "entity-delete-other@example.test");
        Cookie owner = login(csrf, "entity-delete-owner@example.test");
        Cookie other = login(csrf, "entity-delete-other@example.test");
        int id = entity(csrf, owner, "Sem vínculos");

        mvc.perform(delete("/api/entities/{id}", id).cookie(csrf, other)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/entities/{id}", id).cookie(owner))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/entities/{id}", id).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/entities/{id}", id).cookie(csrf, owner)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/entities").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        entity(csrf, owner, "Sem vínculos");
    }

    private MockHttpServletRequestBuilder renameEntity(Cookie csrf,
            Cookie access, int id, String name) {
        return put("/api/entities/{id}", id)
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"name\":\"%s\"}".formatted(name));
    }

    private MockHttpServletRequestBuilder changeEntityStatus(Cookie csrf,
            Cookie access, int id, boolean active) {
        return patch("/api/entities/{id}/status", id)
                .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"active\":%s}".formatted(active));
    }

    private MockHttpServletRequestBuilder entry(Cookie csrf, Cookie access,
            int walletId, int categoryId, String description, String amount,
            String occurredOn) {
        return entry(csrf, access, walletId, categoryId, description, amount,
                occurredOn, "NECESSARIA");
    }

    private MockHttpServletRequestBuilder entry(Cookie csrf, Cookie access,
            int walletId, int categoryId, String description, String amount,
            String occurredOn, String necessity) {
        return post("/api/entries")
                .cookie(csrf, access)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("""
                        {"walletId":%d,"categoryId":%d,"description":"%s",
                          "amount":%s,"occurredOn":"%s","necessity":%s,"ownerUserId":999999}
                         """.formatted(walletId, categoryId, description, amount, occurredOn,
                        necessity == null ? "null" : "\"" + necessity + "\""));
    }

    private int wallet(Cookie csrf, Cookie access, String name) throws Exception {
        MvcResult response = mvc.perform(post("/api/wallets")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","initialBalance":100.00,
                                 "initialBalanceDate":"2026-01-01"}
                                """.formatted(name)))
                .andExpect(status().isCreated()).andReturn();
        return id(response);
    }

    private int category(Cookie csrf, Cookie access, String name, String kind)
            throws Exception {
        MvcResult response = mvc.perform(post("/api/categories")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","kind":"%s"}
                                """.formatted(name, kind)))
                .andExpect(status().isCreated()).andReturn();
        return id(response);
    }

    private int entity(Cookie csrf, Cookie access, String name) throws Exception {
        MvcResult response = mvc.perform(post("/api/entities")
                        .cookie(csrf, access).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","ownerUserId":999999}
                                """.formatted(name)))
                .andExpect(status().isCreated()).andReturn();
        return id(response);
    }

    private Cookie csrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent()).andReturn()
                .getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private int register(Cookie csrf, String email) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/register")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha-de-teste-segura"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return id(response);
    }

    private int id(MvcResult result) throws Exception {
        return Integer.parseInt(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id").toString());
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
